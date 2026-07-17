package com.project.cqrs.admin.dlq.controller;

import com.project.cqrs.admin.dlq.dto.DlqPeekResponse;
import com.project.cqrs.admin.dlq.dto.DlqReplayResponse;
import com.project.cqrs.admin.dlq.dto.DlqStatsResponse;
import com.project.cqrs.admin.dlq.service.DlqService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/dlq")
@PreAuthorize("hasRole('ADMIN')")
public class DlqAdminController {

    private final DlqService dlqService;

    public DlqAdminController(DlqService dlqService) {
        this.dlqService = dlqService;
    }

    @GetMapping("/stats")
    public ResponseEntity<DlqStatsResponse> stats() {

        return ResponseEntity.ok(
                dlqService.stats()
        );
    }

    @GetMapping("/{topic}/peek")
    public ResponseEntity<DlqPeekResponse> peek(
            @PathVariable String topic,
            @RequestParam(defaultValue = "10")
            int limit
    ) {

        return ResponseEntity.ok(
                dlqService.peek(
                        topic,
                        limit
                )
        );
    }

    @PostMapping("/{topic}/replay")
    public ResponseEntity<DlqReplayResponse> replay(
            @PathVariable String topic
    ) {

        return ResponseEntity.ok(
                dlqService.replay(topic)
        );
    }
}

