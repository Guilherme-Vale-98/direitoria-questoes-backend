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
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthRefreshLogoutTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    private JsonNode loginNewUser() throws Exception {
        String email = "u_" + UUID.randomUUID() + "@x.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Ana\",\"lastName\":\"Souza\",\"email\":\"" + email
                        + "\",\"password\":\"secret\"}")).andExpect(status().isCreated());
        String res = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res);
    }

    private String refresh(String token) {
        return "{\"refreshToken\":\"" + token + "\"}";
    }

    @Test
    void refreshRotatesAndOldTokenStopsWorking() throws Exception {
        String first = loginNewUser().get("refreshToken").asText();

        String rotated = mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refresh(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn().getResponse().getContentAsString();
        String newRefresh = json.readTree(rotated).get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(first);

        // new token works — exercise it BEFORE replaying the old one (replay revokes the family)
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(refresh(newRefresh))).andExpect(status().isOk());
        // old token now rejected
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(refresh(first))).andExpect(status().isUnauthorized());
    }

    @Test
    void reuseOfRotatedTokenKillsFamily() throws Exception {
        String first = loginNewUser().get("refreshToken").asText();
        String rotated = mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refresh(first))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = json.readTree(rotated).get("refreshToken").asText();

        // replay the already-rotated 'first' => reuse => family revoked
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(refresh(first))).andExpect(status().isUnauthorized());
        // the legitimate 'second' is now dead too
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(refresh(second))).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesAndIsIdempotent() throws Exception {
        String token = loginNewUser().get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content(refresh(token))).andExpect(status().isNoContent());
        // can't refresh after logout
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(refresh(token))).andExpect(status().isUnauthorized());
        // logout again still 204 (idempotent)
        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content(refresh(token))).andExpect(status().isNoContent());
    }
}
