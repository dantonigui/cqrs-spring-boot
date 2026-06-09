package com.project.cqrs.admin.dlq.dto;

public record DlqReplayResponse(
        String dlqTopic,
        String originalTopic,
        Integer replayed,
        String message
) {
}
