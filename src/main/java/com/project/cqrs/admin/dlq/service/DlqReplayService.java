package com.project.cqrs.admin.dlq.service;

import com.project.cqrs.admin.dlq.infrastructure.DlqReplayGateway;
import org.springframework.stereotype.Service;

@Service
public class DlqReplayService {

    private final DlqReplayGateway replayGateway;

    public DlqReplayService(
            DlqReplayGateway replayGateway
    ) {
        this.replayGateway = replayGateway;
    }

    public int replay(String dlqTopic) {

        String originalTopic =
                dlqTopic.replace(".DLT", "");

        return replayGateway.replayMessages(
                dlqTopic,
                originalTopic
        );
    }
}