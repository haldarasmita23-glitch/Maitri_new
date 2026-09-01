package com.maitri.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application.properties")
@DisplayName("CORS Preflight and Cross-Origin Integration Tests")
class CorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("OPTIONS /api/auth/register allows LAN frontend origin http://192.168.1.67:3000")
    void optionsRegister_allowsLanFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://192.168.1.67:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type, Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://192.168.1.67:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
    }

    @Test
    @DisplayName("OPTIONS /api/categories allows LAN frontend origin http://192.168.1.67:3000")
    void optionsCategories_allowsLanFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/categories")
                        .header(HttpHeaders.ORIGIN, "http://192.168.1.67:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://192.168.1.67:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("GET /api/categories with LAN origin returns categories and Access-Control-Allow-Origin")
    void getCategories_withLanOrigin_returnsCorsHeader() throws Exception {
        mockMvc.perform(get("/api/categories")
                        .header(HttpHeaders.ORIGIN, "http://192.168.1.67:3000")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://192.168.1.67:3000"))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("OPTIONS /api/auth/register allows Vercel frontend deployments (*.vercel.app)")
    void optionsRegister_allowsVercelOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "https://maitri-frontend.vercel.app")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://maitri-frontend.vercel.app"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("OPTIONS /api/auth/register allows localhost development origins")
    void optionsRegister_allowsLocalhost() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5500")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5500"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("OPTIONS /api/auth/register rejects unauthorized public origin")
    void optionsRegister_rejectsUntrustedOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://malicious-site.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
