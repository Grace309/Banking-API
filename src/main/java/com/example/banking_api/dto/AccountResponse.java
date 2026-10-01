package com.example.banking_api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.example.banking_api.model.AccountStatus;

public record AccountResponse(UUID id, String ownerName, BigDecimal balance, AccountStatus status) {
}
