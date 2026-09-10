package com.dronzer.aisearch.integration;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.annotation.Transactional;

import com.dronzer.aisearch.entity.Note;
import com.dronzer.aisearch.entity.User;
import com.dronzer.aisearch.repository.NoteRepository;
import com.dronzer.aisearch.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
@Transactional
class NoteTenantIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        noteRepository.deleteAll();
        userRepository.deleteAll();
        userA = userRepository.save(user("integration-a@example.test"));
        userB = userRepository.save(user("integration-b@example.test"));
    }

    @Test
    void eachUserSeesOnlyOwnedNotesAndCannotReadTheOtherUsersNote() throws Exception {
        createNote("A private note", userA.getEmail());
        createNote("B private note", userB.getEmail());

        assertThat(noteUserId("A private note"))
            .isEqualTo(userA.getId());
        assertThat(noteUserId("B private note"))
            .isEqualTo(userB.getId());

        mockMvc.perform(get("/notes")
                        .with(authenticatedUser(userA.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("A private note"));

        mockMvc.perform(get("/notes")
                        .with(authenticatedUser(userB.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("B private note"));

        Note noteA = noteRepository.findByUserOrderByIdAsc(userA).get(0);
        mockMvc.perform(get("/notes/{id}", noteA.getId())
                        .with(authenticatedUser(userB.getEmail())))
                .andExpect(status().isNotFound());

        assertThat(noteRepository.findById(noteA.getId())).isPresent();
        assertThat(noteRepository.findById(noteA.getId()).orElseThrow().getUser())
            .extracting(User::getId)
            .isEqualTo(userA.getId());
    }

        @Test
        void allIdBasedOperationsRejectCrossUserAccess() throws Exception {
        Note noteA = createNote("A private note", userA.getEmail());
        Note noteB = createNote("B private note", userB.getEmail());

        mockMvc.perform(get("/notes/{id}", noteB.getId())
                .with(authenticatedUser(userA.getEmail())))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/notes/{id}", noteA.getId())
                .with(authenticatedUser(userB.getEmail())))
            .andExpect(status().isNotFound());

        mockMvc.perform(put("/notes/{id}", noteB.getId())
                .with(authenticatedUser(userA.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"changed\"}"))
            .andExpect(status().isNotFound());
        mockMvc.perform(put("/notes/{id}", noteA.getId())
                .with(authenticatedUser(userB.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"changed\"}"))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/notes/{id}", noteB.getId())
                .with(authenticatedUser(userA.getEmail())))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/notes/{id}", noteA.getId())
                .with(authenticatedUser(userB.getEmail())))
            .andExpect(status().isNotFound());

        assertThat(noteRepository.findById(noteA.getId())).isPresent();
        assertThat(noteRepository.findById(noteB.getId())).isPresent();
        }

        private Note createNote(String title, String email) throws Exception {
        mockMvc.perform(post("/notes")
                        .with(authenticatedUser(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail(email).orElseThrow();
        var notes = noteRepository.findByUserOrderByIdAsc(user);
        return notes.get(notes.size() - 1);
    }

    private User user(String email) {
        return new User(email, "encoded-password", LocalDateTime.now());
    }

    private Long noteUserId(String title) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id FROM notes WHERE title = ?",
                Long.class,
                title);
    }

    private RequestPostProcessor authenticatedUser(String email) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                email, null, Collections.emptyList());
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }
}