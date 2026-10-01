package com.example.banking_api.dto;

import com.example.banking_api.model.AccountStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(
		@NotNull(message = "Account status is required") AccountStatus status) {
}
