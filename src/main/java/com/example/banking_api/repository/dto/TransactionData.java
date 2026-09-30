package com.example.banking_api.repository.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionData(
		UUID id,
		UUID fromAccountId,
		UUID toAccountId,
		BigDecimal amount,
		Instant createdAt) {
}
