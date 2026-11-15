package com.parko.access.service.controller;

import com.parko.access.service.dto.response.OccupancyResponse;
import com.parko.access.service.security.SecurityConfig;
import com.parko.access.service.service.OccupancyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OccupancyController.class)
@Import(SecurityConfig.class)
class OccupancyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OccupancyService occupancyService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void getOccupancy_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/access/occupancy"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOccupancy_withJwt_returnsSnapshot() throws Exception {
        when(occupancyService.getCurrent()).thenReturn(new OccupancyResponse(42, 100, 58));

        mockMvc.perform(get("/api/v1/access/occupancy").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupied").value(42))
                .andExpect(jsonPath("$.capacity").value(100))
                .andExpect(jsonPath("$.available").value(58));
    }

    @Test
    void stream_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/access/occupancy/stream"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void stream_withJwt_opensEventStream() throws Exception {
        when(occupancyService.subscribe()).thenReturn(new SseEmitter(0L));

        mockMvc.perform(get("/api/v1/access/occupancy/stream").with(jwt()))
                .andExpect(request().asyncStarted());
    }
}
