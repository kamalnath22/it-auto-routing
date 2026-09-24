package com.ticketing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Name must not be blank")
        @Size(max = 100, message = "Name must be 100 characters or fewer")
        String name,

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be valid")
        @Size(max = 150, message = "Email must be 150 characters or fewer")
        String email,

        @NotBlank(message = "Password must not be blank")
        @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
        String password
) {
}
