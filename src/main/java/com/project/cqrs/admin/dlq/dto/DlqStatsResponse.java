package com.project.cqrs.admin.dlq.dto;

import java.util.Map;

public record DlqStatsResponse(
        Map<String, Long> dlqCounts,
        Long totalPending,
        String status
) {
}
