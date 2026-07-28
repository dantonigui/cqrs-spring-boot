package com.project.cqrs.admin.dlq.controller;

import com.project.cqrs.admin.dlq.dto.DlqStatsResponse;
import com.project.cqrs.admin.dlq.service.DlqService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa especificamente a proteção @PreAuthorize("hasRole('ADMIN')")
 * do DlqAdminController — a correção de segurança que fizemos
 * anteriormente (antes estava comentada/desabilitada).
 *
 * @WebMvcTest sobe apenas a camada web (controller + filtros de
 * segurança), sem banco, sem Kafka — rápido e focado.
 */
@WebMvcTest(DlqAdminController.class)
@DisplayName("DlqAdminController — segurança")
class DlqAdminControllerSecurityTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private DlqService dlqService;

    @Test
    @DisplayName("GET /admin/dlq/stats sem autenticação deve retornar 401")
    void statsWithoutAuthenticationShouldReturn401() throws Exception {
        mockMvc.perform(get("/admin/dlq/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /admin/dlq/stats com role USER deve retornar 403")
    void statsWithUserRoleShouldReturn403() throws Exception {
        mockMvc.perform(get("/admin/dlq/stats"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /admin/dlq/stats com role ADMIN deve retornar 200")
    void statsWithAdminRoleShouldReturn200() throws Exception {
        when(dlqService.stats()).thenReturn(
                new DlqStatsResponse(Map.of(), 0L, "EMPTY"));

        mockMvc.perform(get("/admin/dlq/stats"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("POST /admin/dlq/{topic}/replay com role USER deve retornar 403")
    void replayWithUserRoleShouldReturn403() throws Exception {
        mockMvc.perform(post("/admin/dlq/product.created.DLT/replay"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /admin/dlq/{topic}/peek com role ADMIN deve retornar 200")
    void peekWithAdminRoleShouldReturn200() throws Exception {
        when(dlqService.peek("product.created.DLT", 10)).thenReturn(
                new com.project.cqrs.admin.dlq.dto.DlqPeekResponse(
                        "product.created.DLT", 0, java.util.List.of()));

        mockMvc.perform(get("/admin/dlq/product.created.DLT/peek"))
                .andExpect(status().isOk());
    }
}