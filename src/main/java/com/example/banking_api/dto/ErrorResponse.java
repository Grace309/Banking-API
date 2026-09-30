package com.example.banking_api.dto;

import java.util.Map;

public record ErrorResponse(String code, String message, Map<String, String> fieldErrors) {
	public ErrorResponse {
		fieldErrors = Map.copyOf(fieldErrors);
	}
}
