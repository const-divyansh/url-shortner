package com.urlshortener.controller;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.urlshortener.AbstractIntegrationTest;
import com.urlshortener.auth.IssuedSession;

@AutoConfigureMockMvc
class ApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("guest session, create, and redirect work end to end through Spring MVC")
    void guestSessionCreateAndRedirectFlow() throws Exception {
        IssuedSession guest = issueGuestSession();

        mockMvc.perform(get("/api/auth/session").header(AUTHORIZATION, bearer(guest.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("guest"))
                .andExpect(jsonPath("$.guest").value(true));

        mockMvc.perform(post("/api/urls")
                        .header(AUTHORIZATION, bearer(guest.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUrl": "https://example.com/from-controller",
                                  "customAlias": "mvc-flow"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost:8080/mvc-flow"))
                .andExpect(jsonPath("$.shortCode").value("mvc-flow"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/mvc-flow"))
                .andExpect(jsonPath("$.targetUrl").value("https://example.com/from-controller"));

        mockMvc.perform(get("/mvc-flow"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/from-controller"));
    }

    @Test
    @DisplayName("delete stays owner-gated end to end and makes the link return code.deleted")
    void deleteIsOwnerGatedThroughMvc() throws Exception {
        IssuedSession owner = issueGoogleSession();
        IssuedSession stranger = issueGoogleSession();

        mockMvc.perform(post("/api/urls")
                        .header(AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUrl": "https://example.com/delete-me",
                                  "customAlias": "mvc-delete"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/urls/mvc-delete").header(AUTHORIZATION, bearer(stranger.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.forbidden"));

        mockMvc.perform(delete("/api/urls/mvc-delete").header(AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/mvc-delete"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("code.deleted"));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
