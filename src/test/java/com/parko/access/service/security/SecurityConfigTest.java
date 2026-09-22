package com.parko.access.service.security;

import com.parko.access.service.controller.AccessController;
import com.parko.access.service.controller.TicketPaymentController;
import com.parko.access.service.service.AccessService;
import com.parko.access.service.service.TicketPaymentResolution;
import com.parko.access.service.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AccessController.class, TicketPaymentController.class})
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    @MockitoBean
    private AccessService accessService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void pay_withoutToken_isPublic() throws Exception {
        when(ticketService.resolvePayment(any(UUID.class))).thenReturn(TicketPaymentResolution.notFound());

        mockMvc.perform(get("/api/v1/access/sessions/" + UUID.randomUUID() + "/pay"))
                .andExpect(status().isNotFound());
    }

    @Test
    void pay_pendingTicket_redirectsWithoutToken() throws Exception {
        when(ticketService.resolvePayment(any(UUID.class)))
                .thenReturn(TicketPaymentResolution.redirect("https://mp.com/checkout"));

        mockMvc.perform(get("/api/v1/access/sessions/" + UUID.randomUUID() + "/pay"))
                .andExpect(status().isFound());
    }

    @Test
    void resolveAccess_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessMethod\":\"TICKET\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resolveAccess_withJwt_isAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/access")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessMethod\":\"TICKET\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void otherSessionRoutes_withoutToken_stayProtected() throws Exception {
        mockMvc.perform(get("/api/v1/access/sessions/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void payLookalikePath_withoutToken_stayProtected() throws Exception {
        mockMvc.perform(get("/api/v1/access/sessions/" + UUID.randomUUID() + "/pay/extra"))
                .andExpect(status().isUnauthorized());
    }
}
