package com.example.banking_api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
		UUID id,
		UUID fromAccountId,
		UUID toAccountId,
		BigDecimal amount,
		Instant createdAt) {
}
