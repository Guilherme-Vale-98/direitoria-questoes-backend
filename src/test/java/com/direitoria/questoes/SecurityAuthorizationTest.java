package com.direitoria.questoes;

import com.direitoria.questoes.auth.JwtService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityAuthorizationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwt;

    private String bearer(String... roles) {
        return "Bearer " + jwt.issueAccessToken(UUID.randomUUID(), List.of(roles));
    }

    @Test
    void catalogStaysPublic() throws Exception {
        mockMvc.perform(get("/api/questions").param("size", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedPathRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedPathRejectsGarbageToken() throws Exception {
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminPathRejectsUserRoleWith403() throws Exception {
        mockMvc.perform(get("/api/admin/ping").header("Authorization", bearer("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPathPassesAuthorizationForAdminRole() throws Exception {
        // ADMIN passes the authorization rule; no controller exists at this path yet => 404 (not 401/403).
        mockMvc.perform(get("/api/admin/ping").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNotFound());
    }
}
