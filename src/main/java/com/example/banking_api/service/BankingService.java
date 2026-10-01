package com.example.banking_api.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.banking_api.dto.AccountResponse;
import com.example.banking_api.dto.CreateAccountRequest;
import com.example.banking_api.dto.TransactionResponse;
import com.example.banking_api.dto.TransferRequest;
import com.example.banking_api.exception.BankingException;
import com.example.banking_api.exception.BankingException.Reason;
import com.example.banking_api.model.AccountStatus;
import com.example.banking_api.repository.BankingRepository;
import com.example.banking_api.repository.dto.AccountData;
import com.example.banking_api.repository.dto.TransactionData;

@Service
public class BankingService {

	private static final BigDecimal MAX_BALANCE = new BigDecimal("999999999999.99");

	private final BankingRepository repository;
	private final Clock clock;

	public BankingService(BankingRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	// All operations use the same singleton service monitor. This serializes each
	// read-check-write workflow and keeps reads consistent with completed transfers.
	public synchronized AccountResponse createAccount(CreateAccountRequest request) {
		AccountData account = new AccountData(UUID.randomUUID(), request.ownerName().strip(),
				money(request.initialBalance()), AccountStatus.ACTIVE);
		repository.saveAccount(account);
		return toResponse(account);
	}

	public synchronized List<AccountResponse> getAccounts() {
		return repository.findAllAccounts().stream()
				.map(BankingService::toResponse)
				.toList();
	}

	public synchronized AccountResponse getAccount(UUID accountId) {
		return toResponse(requireAccount(accountId));
	}

	public synchronized TransactionResponse transfer(TransferRequest request) {
		AccountData source = requireAccount(request.fromAccountId());
		AccountData destination = requireAccount(request.toAccountId());
		requireActive(source);
		requireActive(destination);

		if (source.id().equals(destination.id())) {
			throw new BankingException(Reason.SAME_ACCOUNT, "Source and destination accounts must differ");
		}

		BigDecimal amount = money(request.amount());
		if (source.balance().compareTo(amount) < 0) {
			throw new BankingException(Reason.INSUFFICIENT_FUNDS, "Source account has insufficient funds");
		}
		BigDecimal destinationBalance = destination.balance().add(amount);
		if (destinationBalance.compareTo(MAX_BALANCE) > 0) {
			throw new BankingException(Reason.BALANCE_LIMIT_EXCEEDED, "Destination balance would exceed the allowed limit");
		}

		AccountData updatedSource = source.withBalance(source.balance().subtract(amount));
		AccountData updatedDestination = destination.withBalance(destinationBalance);
		TransactionData transaction = new TransactionData(UUID.randomUUID(), source.id(), destination.id(),
				amount, Instant.now(clock));
		TransactionResponse response = toResponse(transaction);

		// Validation and calculation finish before storage changes. The repository
		// publishes both balances and the history entry in one commit.
		repository.saveTransfer(updatedSource, updatedDestination, transaction);
		return response;
	}

	public synchronized List<TransactionResponse> getTransactions(UUID accountId) {
		requireAccount(accountId);
		return repository.findTransactionsByAccountId(accountId).stream()
				.map(BankingService::toResponse)
				.toList();
	}

	public synchronized AccountResponse updateAccountStatus(UUID accountId, AccountStatus status) {
		java.util.Objects.requireNonNull(status, "Account status is required");
		AccountData account = requireAccount(accountId);
		if (account.status() == status) {
			return toResponse(account);
		}
		AccountData updated = account.withStatus(status);
		repository.saveAccount(updated);
		return toResponse(updated);
	}

	private void requireActive(AccountData account) {
		if (account.status() != AccountStatus.ACTIVE) {
			throw new BankingException(Reason.ACCOUNT_INACTIVE, "Account is inactive: " + account.id());
		}
	}

	private AccountData requireAccount(UUID accountId) {
		return repository.findAccountById(accountId)
				.orElseThrow(() -> new BankingException(Reason.ACCOUNT_NOT_FOUND, "Account not found: " + accountId));
	}

	private static BigDecimal money(BigDecimal value) {
		return value.setScale(2, RoundingMode.UNNECESSARY);
	}

	private static AccountResponse toResponse(AccountData account) {
		return new AccountResponse(account.id(), account.ownerName(), account.balance(), account.status());
	}

	private static TransactionResponse toResponse(TransactionData transaction) {
		return new TransactionResponse(transaction.id(), transaction.fromAccountId(), transaction.toAccountId(),
				transaction.amount(), transaction.createdAt());
	}
}
