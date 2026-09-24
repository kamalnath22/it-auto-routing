package com.ticketing.dto;

import java.time.Instant;

public record TicketResponse(
        Long id,
        String subject,
        String description,
        String status,
        Instant createdAt,
        Long userId,
        String userEmail
) {
}
