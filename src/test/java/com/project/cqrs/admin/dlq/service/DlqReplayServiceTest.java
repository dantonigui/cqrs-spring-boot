package com.project.cqrs.admin.dlq.service;

import com.project.cqrs.admin.dlq.infrastructure.DlqReplayGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DlqReplayService")
class DlqReplayServiceTest {

    @Mock private DlqReplayGateway replayGateway;

    private DlqReplayService dlqReplayService;

    @BeforeEach
    void setUp() {
        dlqReplayService = new DlqReplayService(replayGateway);
    }

    @Test
    @DisplayName("deve derivar o topic original removendo o sufixo .DLT")
    void shouldDeriveOriginalTopicByStrippingDltSuffix() {
        when(replayGateway.replayMessages("order.created.DLT", "order.created"))
                .thenReturn(0);

        dlqReplayService.replay("order.created.DLT");

        verify(replayGateway).replayMessages("order.created.DLT", "order.created");
    }

    @Test
    @DisplayName("deve retornar exatamente o valor retornado pelo gateway")
    void shouldReturnGatewayResultAsIs() {
        when(replayGateway.replayMessages("payment.approved.DLT", "payment.approved"))
                .thenReturn(7);

        int result = dlqReplayService.replay("payment.approved.DLT");

        assertThat(result).isEqualTo(7);
    }

    @Test
    @DisplayName("deve retornar zero quando o gateway não encontra mensagens")
    void shouldReturnZeroWhenGatewayFindsNothing() {
        when(replayGateway.replayMessages("product.deleted.DLT", "product.deleted"))
                .thenReturn(0);

        assertThat(dlqReplayService.replay("product.deleted.DLT")).isZero();
    }
}