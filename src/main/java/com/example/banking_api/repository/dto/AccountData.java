package com.example.banking_api.repository.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.example.banking_api.model.AccountStatus;

// Immutable data transferred between the service and repository layers.
public record AccountData(UUID id, String ownerName, BigDecimal balance, AccountStatus status) {
	public AccountData withBalance(BigDecimal newBalance) {
		return new AccountData(id, ownerName, newBalance, status);
	}

	public AccountData withStatus(AccountStatus newStatus) {
		return new AccountData(id, ownerName, balance, newStatus);
	}
}
