package com.example.banking_api.repository.dto;

import java.math.BigDecimal;
import java.util.UUID;

// Immutable data transferred between the service and repository layers.
public record AccountData(UUID id, String ownerName, BigDecimal balance) {
	public AccountData withBalance(BigDecimal newBalance) {
		return new AccountData(id, ownerName, newBalance);
	}
}
