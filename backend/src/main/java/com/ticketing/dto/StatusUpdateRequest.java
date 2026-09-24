package com.ticketing.dto;

import com.ticketing.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(@NotNull TicketStatus status) {
}
