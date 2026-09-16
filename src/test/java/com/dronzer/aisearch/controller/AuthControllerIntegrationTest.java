package com.dronzer.aisearch.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
@Transactional
class AuthControllerIntegrationTest {

    private static final String VALID_PASSWORD = "strong-password-1";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registersNewUserWithCreatedStatusAndMessageBody() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("register-ok@example.test", VALID_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void rejectsDuplicateEmailWithConflict() throws Exception {
        registerUser("duplicate@example.test");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("duplicate@example.test", VALID_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Email already registered"))
                .andExpect(jsonPath("$.path").value("/auth/register"));
    }

    @Test
    void rejectsBlankRegistrationEmailWithBadRequest() throws Exception {
        assertBadRequestRegister("{\"email\":\"\",\"password\":\"" + VALID_PASSWORD + "\"}",
                "email must not be blank");
    }

    @Test
    void rejectsMalformedRegistrationEmailWithBadRequest() throws Exception {
        assertBadRequestRegister(credentials("not-an-email", VALID_PASSWORD),
                "email must be a valid email address");
    }

    @Test
    void rejectsRegistrationWithoutPasswordWithBadRequest() throws Exception {
        assertBadRequestRegister("{\"email\":\"missing-password@example.test\"}",
                "password must not be blank");
    }

    @Test
    void rejectsRegistrationWithShortPasswordWithBadRequest() throws Exception {
        assertBadRequestRegister(credentials("short-password@example.test", "short"),
                "password must be between 8 and 72 characters");
    }

    @Test
    void rejectsRegistrationWithNullPasswordWithoutLeakingEncoderInternals() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"null-password@example.test\",\"password\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("password must not be blank"))
                .andExpect(content().string(not(containsString("rawPassword"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void logsInWithJsonTokenThatAuthenticatesApiRequests() throws Exception {
        registerUser("login-ok@example.test");

        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("login-ok@example.test", VALID_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = JsonPath.read(body, "$.token");
        assertThat(token.split("\\.")).hasSize(3);

        mockMvc.perform(get("/documents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsWrongPasswordWithUnauthorizedAndNoToken() throws Exception {
        registerUser("wrong-password@example.test");

        assertUnauthorizedWithoutToken(
                credentials("wrong-password@example.test", "another-password-1"));
    }

    @Test
    void rejectsUnknownUserWithUnauthorizedAndNoToken() throws Exception {
        assertUnauthorizedWithoutToken(
                credentials("unknown-user@example.test", VALID_PASSWORD));
    }

    @Test
    void rejectsBlankLoginCredentialsWithBadRequest() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"" + VALID_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("email must not be blank"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"blank-login@example.test\",\"password\":\"        \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password must not be blank"));
    }

    @Test
    void failedLoginNeverProducesAnAuthenticatedResponseBody() throws Exception {
        registerUser("no-token@example.test");

        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("no-token@example.test", "definitely-wrong-1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("eyJ");
    }

    private void assertBadRequestRegister(String body, String expectedMessage) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.path").value("/auth/register"));
    }

    private void assertUnauthorizedWithoutToken(String body) throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.path").value("/auth/login"));
    }

    private void registerUser(String email) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, VALID_PASSWORD)))
                .andExpect(status().isCreated());
    }

    private String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
