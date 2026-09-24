package com.ticketing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank(message = "Subject must not be blank")
        @Size(max = 200, message = "Subject must be 200 characters or fewer")
        String subject,

        @NotBlank(message = "Description must not be blank")
        @Size(max = 5000, message = "Description must be 5000 characters or fewer")
        String description
) {
}
