package com.direitoria.questoes.auth;

import com.direitoria.questoes.TestcontainersConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthRegisterLoginTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String email() {
        return "u_" + UUID.randomUUID() + "@x.com";
    }

    private String body(String email, String password) {
        return "{\"firstName\":\"Ana\",\"lastName\":\"Souza\",\"email\":\"" + email
                + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void registerCreatesUserWithHashedPasswordAndUserRole() throws Exception {
        String email = email();
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, "secret")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        String stored = jdbc.queryForObject("SELECT password FROM users WHERE email = ?", String.class, email);
        assertThat(stored).isNotEqualTo("secret");
        assertThat(stored).startsWith("$2");
    }

    @Test
    void registerRejectsDuplicateEmailWith409() throws Exception {
        String email = email();
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body(email, "secret"))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, "secret")))
                .andExpect(status().isConflict());
    }

    @Test
    void registerRejectsShortPasswordWith400() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(email(), "ab")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginReturnsTokensAndAccessTokenAuthorizesMe() throws Exception {
        String email = email();
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body(email, "secret"))).andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(response);
        String access = node.get("accessToken").asText();
        assertThat(node.get("refreshToken").asText()).isNotBlank();

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void loginWithWrongPasswordAndUnknownEmailGiveIdenticalGeneric401() throws Exception {
        String email = email();
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body(email, "secret"))).andExpect(status().isCreated());

        String wrongPw = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"WRONG\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknown = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody_" + UUID.randomUUID() + "@x.com\",\"password\":\"WRONG\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPw).isEqualTo(unknown); // byte-identical: no enumeration
    }
}
