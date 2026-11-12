package com.project.cqrs.command.payment;
import com.project.cqrs.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
class PaymentWebhookFlowIT extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Test void webhookIsPublicButRequiresValidSignature() throws Exception {
        mvc.perform(post("/api/v1/webhooks/mercadopago?data.id=123").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"payment\",\"data\":{\"id\":\"123\"}}")).andExpect(status().isUnauthorized());
    }
    @Test void adminEndpointsRequireAuthenticationAndRole() throws Exception {
        mvc.perform(get("/admin/dlq/stats")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/admin/cache/all").with(user("42")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/command/orders/1/checkout/in-person").with(user("42")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"CASH\"}")).andExpect(status().isForbidden());
    }
    @Test void csrfRequiredForCookieAuthenticatedMutations() throws Exception {
        mvc.perform(post("/api/v1/command/orders").with(user("42")).contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/csrf")).andExpect(status().isOk()).andExpect(jsonPath("token").isNotEmpty());
    }
}