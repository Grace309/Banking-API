package com.example.banking_api.repository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.example.banking_api.repository.dto.AccountData;
import com.example.banking_api.repository.dto.TransactionData;

@Repository
public class InMemoryBankingRepository implements BankingRepository {

	// Readers see one complete, immutable state; they cannot modify stored balances.
	private volatile State state = new State(Map.of(), Map.of());

	@Override
	public synchronized void saveAccount(AccountData account) {
		Map<UUID, AccountData> accounts = new HashMap<>(state.accounts());
		accounts.put(account.id(), account);
		state = new State(Map.copyOf(accounts), state.transactions());
	}

	@Override
	public Optional<AccountData> findAccountById(UUID accountId) {
		return Optional.ofNullable(state.accounts().get(accountId));
	}

	@Override
	public List<AccountData> findAllAccounts() {
		return state.accounts().values().stream()
				.sorted(Comparator.comparing(AccountData::ownerName)
						.thenComparing(AccountData::id))
				.toList();
	}

	@Override
	public List<TransactionData> findTransactionsByAccountId(UUID accountId) {
		return state.transactions().values().stream()
				.filter(transaction -> transaction.fromAccountId().equals(accountId)
						|| transaction.toAccountId().equals(accountId))
				.sorted(Comparator.comparing(TransactionData::createdAt)
						.thenComparing(TransactionData::id))
				.toList();
	}

	@Override
	public synchronized void saveTransfer(
			AccountData source, AccountData destination, TransactionData transaction) {
		Map<UUID, AccountData> accounts = new HashMap<>(state.accounts());
		Map<UUID, TransactionData> transactions = new HashMap<>(state.transactions());
		accounts.put(source.id(), source);
		accounts.put(destination.id(), destination);
		transactions.put(transaction.id(), transaction);

		// Build the full replacement first. A failure before this assignment changes nothing.
		State nextState = new State(Map.copyOf(accounts), Map.copyOf(transactions));
		state = nextState;
	}

	private record State(Map<UUID, AccountData> accounts, Map<UUID, TransactionData> transactions) {
	}
}
