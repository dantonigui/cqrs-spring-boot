package com.project.cqrs.admin.dlq.service;

import com.project.cqrs.admin.dlq.dto.DlqMessageResponse;
import com.project.cqrs.admin.dlq.dto.DlqPeekResponse;
import com.project.cqrs.admin.dlq.dto.DlqReplayResponse;
import com.project.cqrs.admin.dlq.dto.DlqStatsResponse;
import com.project.cqrs.admin.dlq.infrastructure.DlqInspector;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DlqService {

    private static final List<String> DLQ_TOPICS = List.of(
            "product.created.DLT",
            "product.updated.DLT",
            "product.deleted.DLT"
    );

    private final DlqInspector dlqInspector;
    private final DlqReplayService dlqReplayService;

    public DlqService(
            DlqInspector dlqInspector,
            DlqReplayService dlqReplayService
    ) {
        this.dlqInspector = dlqInspector;
        this.dlqReplayService = dlqReplayService;
    }

    public DlqStatsResponse stats() {

        Map<String, Long> counts =
                new HashMap<>();

        for (String topic : DLQ_TOPICS) {

            counts.put(
                    topic,
                    dlqInspector.countMessages(topic)
            );
        }

        long total =
                counts.values()
                        .stream()
                        .mapToLong(Long::longValue)
                        .sum();

        return new DlqStatsResponse(
                counts,
                total,
                total > 0
                        ? "HAS_PENDING"
                        : "EMPTY"
        );
    }

    public DlqPeekResponse peek(
            String topic,
            int limit
    ) {

        String dlqTopic =
                topic.endsWith(".DLT")
                        ? topic
                        : topic + ".DLT";

        validateDlq(dlqTopic);

        List<DlqMessageResponse> messages =
                dlqInspector.peek(
                        dlqTopic,
                        limit
                );

        return new DlqPeekResponse(
                dlqTopic,
                messages.size(),
                messages
        );
    }

    public DlqReplayResponse replay(
            String topic
    ) {

        String dlqTopic =
                topic.endsWith(".DLT")
                        ? topic
                        : topic + ".DLT";

        validateDlq(dlqTopic);

        int replayed =
                dlqReplayService.replay(dlqTopic);

        return new DlqReplayResponse(
                dlqTopic,
                dlqTopic.replace(".DLT", ""),
                replayed,
                replayed > 0
                        ? "Mensagens reenviadas para reprocessamento."
                        : "Nenhuma mensagem encontrada."
        );
    }

    private void validateDlq(String topic) {

        if (!DLQ_TOPICS.contains(topic)) {

            throw new IllegalArgumentException(
                    "DLQ desconhecida: " + topic
            );
        }
    }
}
