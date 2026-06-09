package com.project.cqrs.admin.dlq.dto;

public record DlqMessageResponse(
        String topic,
        Integer partition,
        Long offset,
        String key,
        String value
) {
}
