package com.project.cqrs.admin.dlq.dto;

import java.util.List;

public record DlqPeekResponse(
        String dlqTopic,
        Integer count,
        List<DlqMessageResponse> messages
) {
}
