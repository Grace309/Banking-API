package com.example.banking_api.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.banking_api.dto.AccountResponse;
import com.example.banking_api.dto.CreateAccountRequest;
import com.example.banking_api.dto.TransactionResponse;
import com.example.banking_api.dto.UpdateAccountStatusRequest;
import com.example.banking_api.service.BankingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

	private final BankingService service;

	public AccountController(BankingService service) {
		this.service = service;
	}

	@PostMapping
	@ApiResponse(responseCode = "201", description = "Account created")
	public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
		AccountResponse account = service.createAccount(request);
		return ResponseEntity.created(URI.create("/api/accounts/" + account.id())).body(account);
	}

	@GetMapping
	@Operation(summary = "List all accounts", description = "Find account IDs, owner names, current balances and statuses, including inactive accounts. Sorted by owner name, then ID.")
	public List<AccountResponse> getAccounts() {
		return service.getAccounts();
	}

	@GetMapping("/{accountId}")
	public AccountResponse getAccount(@PathVariable UUID accountId) {
		return service.getAccount(accountId);
	}

	@PatchMapping("/{accountId}/status")
	@Operation(summary = "Activate or deactivate an account", description = "INACTIVE blocks incoming and outgoing transfers while preserving balances and history. Set ACTIVE to reactivate. Repeating the current status has no effect.")
	public AccountResponse updateAccountStatus(@PathVariable UUID accountId,
			@Valid @RequestBody UpdateAccountStatusRequest request) {
		return service.updateAccountStatus(accountId, request.status());
	}

	@GetMapping("/{accountId}/transactions")
	public List<TransactionResponse> getTransactions(@PathVariable UUID accountId) {
		return service.getTransactions(accountId);
	}
}
