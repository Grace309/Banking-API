package com.example.banking_api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(UUID id, String ownerName, BigDecimal balance) {
}
