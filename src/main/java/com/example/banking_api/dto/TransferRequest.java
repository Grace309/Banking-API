package com.example.banking_api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferRequest(
		@NotNull(message = "Source account ID is required")
		UUID fromAccountId,

		@NotNull(message = "Destination account ID is required")
		UUID toAccountId,

		@NotNull(message = "Transfer amount is required")
		@Positive(message = "Transfer amount must be greater than zero")
		@Digits(integer = 12, fraction = 2,
				message = "Transfer amount must have at most 12 integer digits and 2 decimal places")
		BigDecimal amount) {
}
