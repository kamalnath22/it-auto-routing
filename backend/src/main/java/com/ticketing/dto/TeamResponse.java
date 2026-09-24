package com.ticketing.dto;

import java.time.Instant;

public record TeamResponse(Long id, String name, String category, boolean active, Instant createdAt) {
}
