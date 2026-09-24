package com.ticketing.dto;

import jakarta.validation.constraints.NotNull;

public record AssignTicketRequest(@NotNull Long agentId) {
}
