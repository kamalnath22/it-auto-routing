package com.ticketing.dto;

import java.time.Instant;
import com.ticketing.entity.TicketStatus;
import com.ticketing.entity.ClassificationStatus;
import com.ticketing.entity.Priority;
import com.ticketing.entity.SlaStatus;

public record TicketResponse(
        Long id,
        String subject,
        String description,
        TicketStatus status,
        Instant createdAt,
        Long userId,
        String userEmail,
        Instant updatedAt,
        Long assignedAgentId,
        String assignedAgentEmail,
        String category,
        String predictedCategory,
        Double mlConfidence,
        ClassificationStatus classificationStatus,
        Long assignedTeamId,
        String assignedTeamName,
        Priority priority,
        Instant slaDeadline,
        SlaStatus slaStatus,
        Integer slaTargetMinutes
) {
}
