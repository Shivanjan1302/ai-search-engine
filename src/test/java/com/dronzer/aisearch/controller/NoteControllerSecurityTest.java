package com.dronzer.aisearch.controller;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dronzer.aisearch.entity.Note;
import com.dronzer.aisearch.service.JwtService;
import com.dronzer.aisearch.service.NoteService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
class NoteControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

        @Autowired
        private JwtService jwtService;

    @MockitoBean
    private NoteService noteService;

    @Test
    void authenticatedCreateAndListUseTheAuthenticatedIdentity() throws Exception {
        Note note = new Note("A note");
        when(noteService.saveNote("A note", "user-a@example.test")).thenReturn(note);
        when(noteService.getAllNotes("user-a@example.test"))
                .thenReturn(List.of(note));

        mockMvc.perform(post("/notes")
                        .with(authenticatedUser("user-a@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"A note\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("A note"));

        mockMvc.perform(get("/notes")
                        .with(authenticatedUser("user-a@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("A note"));

        verify(noteService).saveNote("A note", "user-a@example.test");
        verify(noteService).getAllNotes("user-a@example.test");
    }

    @Test
    void idBasedOperationsUseTheAuthenticatedIdentity() throws Exception {
        when(noteService.getNote(10L, "user-a@example.test"))
                .thenReturn(new Note("A note"));
        when(noteService.updateNote(10L, "changed", "user-a@example.test"))
                .thenReturn(new Note("changed"));

        mockMvc.perform(get("/notes/10")
                        .with(authenticatedUser("user-a@example.test")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/notes/10")
                        .with(authenticatedUser("user-a@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"changed\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/notes/10")
                        .with(authenticatedUser("user-a@example.test")))
                .andExpect(status().isOk());

        verify(noteService).getNote(10L, "user-a@example.test");
        verify(noteService).updateNote(10L, "changed", "user-a@example.test");
        verify(noteService).deleteNote(10L, "user-a@example.test");
    }

    @Test
    void unauthenticatedNoteAccessRemainsRejected() throws Exception {
        mockMvc.perform(get("/notes"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void jwtSubjectIsPassedToNoteServiceAsTheAuthenticatedEmail() throws Exception {
        when(noteService.getAllNotes("user-a@example.test"))
                .thenReturn(List.of());

        mockMvc.perform(get("/notes")
                        .header("Authorization", "Bearer "
                                + jwtService.generateToken("user-a@example.test")))
                .andExpect(status().isOk());

        verify(noteService).getAllNotes("user-a@example.test");
    }

    private RequestPostProcessor authenticatedUser(String email) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                email, null, Collections.emptyList());
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }
}
