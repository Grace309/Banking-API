package com.example.banking_api.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.banking_api.repository.dto.AccountData;
import com.example.banking_api.model.AccountStatus;
import com.example.banking_api.repository.dto.TransactionData;

class InMemoryBankingRepositoryTest {

	@Test
	void failedCommitLeavesBothAccountsAndHistoryIntact() {
		InMemoryBankingRepository repository = new InMemoryBankingRepository();
		AccountData alice = new AccountData(UUID.randomUUID(), "Alice", new BigDecimal("100.00"), AccountStatus.ACTIVE);
		AccountData bob = new AccountData(UUID.randomUUID(), "Bob", new BigDecimal("0.00"), AccountStatus.ACTIVE);
		repository.saveAccount(alice);
		repository.saveAccount(bob);
		// Failure occurs after the local copies receive new balances, but before publication.
		assertThrows(NullPointerException.class, () -> repository.saveTransfer(
				alice.withBalance(new BigDecimal("80.00")), bob.withBalance(new BigDecimal("20.00")), null));
		assertEquals(alice, repository.findAccountById(alice.id()).orElseThrow());
		assertEquals(bob, repository.findAccountById(bob.id()).orElseThrow());
		assertTrue(repository.findTransactionsByAccountId(alice.id()).isEmpty());
	}

	@Test
	void historyIsChronologicalEvenWhenTransactionsAreInsertedOutOfOrder() {
		InMemoryBankingRepository repository = new InMemoryBankingRepository();
		AccountData alice = new AccountData(UUID.randomUUID(), "Alice", new BigDecimal("100.00"), AccountStatus.ACTIVE);
		AccountData bob = new AccountData(UUID.randomUUID(), "Bob", new BigDecimal("100.00"), AccountStatus.ACTIVE);
		TransactionData earlier = new TransactionData(UUID.randomUUID(), alice.id(), bob.id(), BigDecimal.ONE,
				Instant.parse("2026-09-30T10:00:00Z"));
		TransactionData later = new TransactionData(UUID.randomUUID(), bob.id(), alice.id(), BigDecimal.ONE,
				Instant.parse("2026-09-30T11:00:00Z"));
		repository.saveTransfer(alice, bob, later);
		repository.saveTransfer(alice, bob, earlier);
		assertEquals(List.of(earlier, later), repository.findTransactionsByAccountId(alice.id()));
	}
}
