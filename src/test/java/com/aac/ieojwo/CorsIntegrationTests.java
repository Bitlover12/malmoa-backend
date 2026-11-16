package com.aac.ieojwo;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.frontend-allowed-origins=https://frontend-pied-five-90.vercel.app/, https://frontend-git-feature-jian-mal-moa.vercel.app/"
})
@AutoConfigureMockMvc
class CorsIntegrationTests {

    @Autowired
    MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "https://frontend-pied-five-90.vercel.app",
            "https://frontend-git-feature-jian-mal-moa.vercel.app"
    })
    void allowsConfiguredFrontendOrigins(String origin) throws Exception {
        mockMvc.perform(options("/api/v1/auth/me")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }
}
