package com.parko.access.service.controller;

import com.parko.access.service.service.TicketPaymentResolution;
import com.parko.access.service.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketPaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class TicketPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    @Test
    void pay_sessionNotFound_returns404() throws Exception {
        UUID parkingSessionId = UUID.randomUUID();
        when(ticketService.resolvePayment(parkingSessionId)).thenReturn(TicketPaymentResolution.notFound());

        mockMvc.perform(get("/api/v1/access/sessions/" + parkingSessionId + "/pay"))
                .andExpect(status().isNotFound());
    }

    @Test
    void pay_ticketAlreadyPaid_returns200WithMessage() throws Exception {
        UUID parkingSessionId = UUID.randomUUID();
        when(ticketService.resolvePayment(parkingSessionId)).thenReturn(TicketPaymentResolution.alreadyPaid());

        mockMvc.perform(get("/api/v1/access/sessions/" + parkingSessionId + "/pay"))
                .andExpect(status().isOk());
    }

    @Test
    void pay_ticketUnavailable_returns409() throws Exception {
        UUID parkingSessionId = UUID.randomUUID();
        when(ticketService.resolvePayment(parkingSessionId))
                .thenReturn(TicketPaymentResolution.unavailable(com.parko.domain.lib.model.TicketStatus.USED));

        mockMvc.perform(get("/api/v1/access/sessions/" + parkingSessionId + "/pay"))
                .andExpect(status().isConflict());
    }

    @Test
    void pay_ticketPending_redirectsToCheckout() throws Exception {
        UUID parkingSessionId = UUID.randomUUID();
        when(ticketService.resolvePayment(parkingSessionId))
                .thenReturn(TicketPaymentResolution.redirect("https://mp.com/checkout"));

        mockMvc.perform(get("/api/v1/access/sessions/" + parkingSessionId + "/pay"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://mp.com/checkout"));
    }
}
