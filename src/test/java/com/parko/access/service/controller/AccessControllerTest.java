package com.parko.access.service.controller;

import tools.jackson.databind.ObjectMapper;
import com.parko.access.service.dto.request.AccessRequest;
import com.parko.access.service.dto.response.AccessResponse;
import com.parko.access.service.exception.GlobalExceptionHandler;
import com.parko.access.service.service.AccessService;
import com.parko.domain.lib.model.AccessEventType;
import com.parko.domain.lib.model.AccessMethod;
import com.parko.domain.lib.model.AccessResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccessController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccessService accessService;

    @Test
    void resolveAccess_authorized_returnsOk() throws Exception {
        AccessRequest request = new AccessRequest(AccessMethod.PLATE, "AB123CD", "device-1", null);
        AccessResponse response = new AccessResponse(AccessResult.AUTHORIZED, AccessEventType.ENTRY, null);
        when(accessService.resolveAccess(any(AccessRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/access")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("AUTHORIZED"))
                .andExpect(jsonPath("$.eventType").value("ENTRY"));
    }

    @Test
    void resolveAccess_denied_stillReturnsOk() throws Exception {
        AccessRequest request = new AccessRequest(AccessMethod.PLATE, "ZZ999ZZ", "device-1", null);
        AccessResponse response = new AccessResponse(AccessResult.DENIED, AccessEventType.ENTRY, "Patente no registrada: ZZ999ZZ");
        when(accessService.resolveAccess(any(AccessRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/access")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("DENIED"));
    }

    @Test
    void resolveAccess_serviceThrowsIllegalArgument_returnsBadRequest() throws Exception {
        AccessRequest request = new AccessRequest(null, "AB123CD", "device-1", null);
        when(accessService.resolveAccess(any(AccessRequest.class)))
                .thenThrow(new IllegalArgumentException("No hay estrategia registrada para null"));

        mockMvc.perform(post("/api/v1/access")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resolveAccess_serviceThrowsForUnknownIdentifier_returnsBadRequest() throws Exception {
        AccessRequest request = new AccessRequest(AccessMethod.PLATE, "", "device-1", null);
        when(accessService.resolveAccess(any(AccessRequest.class)))
                .thenThrow(new IllegalArgumentException("La patente es requerida para el método PLATE"));

        mockMvc.perform(post("/api/v1/access")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
