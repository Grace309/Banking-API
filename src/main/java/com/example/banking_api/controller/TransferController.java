package com.example.banking_api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.banking_api.dto.TransactionResponse;
import com.example.banking_api.dto.TransferRequest;
import com.example.banking_api.service.BankingService;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

	private final BankingService service;

	public TransferController(BankingService service) {
		this.service = service;
	}

	@PostMapping
	@ApiResponse(responseCode = "201", description = "Transfer completed and transaction recorded")
	public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransferRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.transfer(request));
	}
}
