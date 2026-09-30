package com.example.banking_api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
		@NotBlank(message = "Owner name is required")
		@Size(max = 100, message = "Owner name must not exceed 100 characters")
		String ownerName,

		@NotNull(message = "Initial balance is required")
		@DecimalMin(value = "0.00", message = "Initial balance cannot be negative")
		@Digits(integer = 12, fraction = 2,
				message = "Initial balance must have at most 12 integer digits and 2 decimal places")
		BigDecimal initialBalance) {
}
