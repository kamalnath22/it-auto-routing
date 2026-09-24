package com.ticketing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClassificationRequest(@NotBlank @Size(max = 80) String category) {
}
