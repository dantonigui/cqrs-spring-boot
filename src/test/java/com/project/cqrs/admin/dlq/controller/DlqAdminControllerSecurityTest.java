package com.project.cqrs.admin.dlq.controller;
import com.project.cqrs.admin.dlq.service.DlqService;
import com.project.cqrs.admin.dlq.dto.DlqStatsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
@SpringJUnitConfig(DlqAdminControllerSecurityTest.Config.class)
class DlqAdminControllerSecurityTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean DlqService service() { var service = mock(DlqService.class); when(service.stats()).thenReturn(new DlqStatsResponse(Map.of(), 0L, "EMPTY")); return service; }
        @Bean DlqAdminController controller(DlqService service) { return new DlqAdminController(service); }
    }
    @Autowired DlqAdminController controller;
    @Test @WithMockUser(roles = "USER") void userCannotReadAdminStats() { assertThatThrownBy(() -> controller.stats()).isInstanceOf(org.springframework.security.access.AccessDeniedException.class); }
    @Test @WithMockUser(roles = "ADMIN") void adminCanReadStats() { assertThat(controller.stats().getStatusCode().value()).isEqualTo(200); }
    @Test @WithMockUser(roles = "USER") void userCannotReplay() { assertThatThrownBy(() -> controller.replay("products-created.DLT")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class); }
}