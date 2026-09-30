package com.example.banking_api.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.banking_api.repository.dto.AccountData;
import com.example.banking_api.repository.dto.TransactionData;

public interface BankingRepository {
	void saveAccount(AccountData account);

	Optional<AccountData> findAccountById(UUID accountId);

	/** Return a snapshot sorted by owner name, then ID. */
	List<AccountData> findAllAccounts();

	List<TransactionData> findTransactionsByAccountId(UUID accountId);

	/** Publish both updated accounts and their transaction together, or change nothing. */
	void saveTransfer(AccountData source, AccountData destination, TransactionData transaction);
}
