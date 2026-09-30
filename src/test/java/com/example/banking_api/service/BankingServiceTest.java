package com.example.banking_api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.banking_api.dto.AccountResponse;
import com.example.banking_api.dto.CreateAccountRequest;
import com.example.banking_api.dto.TransactionResponse;
import com.example.banking_api.dto.TransferRequest;
import com.example.banking_api.exception.BankingException;
import com.example.banking_api.exception.BankingException.Reason;
import com.example.banking_api.repository.InMemoryBankingRepository;
import com.example.banking_api.repository.dto.AccountData;
import com.example.banking_api.repository.dto.TransactionData;

class BankingServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
	private BankingService service;

	@BeforeEach
	void setUp() {
		service = new BankingService(new InMemoryBankingRepository(), CLOCK);
	}

	@Test
	void listsAccountsIncludingDuplicateNamesInStableOrder() {
		assertTrue(service.getAccounts().isEmpty());
		AccountResponse bob = account("Bob", "100");
		AccountResponse alice = account("Alice", "1000");
		AccountResponse anotherAlice = account("Alice", "50");
		List<AccountResponse> alices = new ArrayList<>(List.of(alice, anotherAlice));
		alices.sort(java.util.Comparator.comparing(AccountResponse::id));
		assertEquals(List.of(alices.get(0), alices.get(1), bob), service.getAccounts());
	}

	@Test
	void createsAnAccountWithTrimmedNameAndTwoDecimalPlaces() {
		AccountResponse account = service.createAccount(new CreateAccountRequest(" Alice ", new BigDecimal("1000")));
		assertEquals("Alice", account.ownerName());
		assertEquals(new BigDecimal("1000.00"), account.balance());
		assertEquals(account, service.getAccount(account.id()));
		assertTrue(service.getTransactions(account.id()).isEmpty());
	}

	@Test
	void generatesDistinctIdsEvenWhenNamesMatch() {
		assertNotEquals(account("Alice", "0").id(), account("Alice", "0").id());
	}

	@Test
	void transfersFundsAndRecordsTheSameTransactionForBothAccounts() {
		AccountResponse alice = account("Alice", "1000");
		AccountResponse bob = account("Bob", "100");
		TransactionResponse transaction = transfer(alice, bob, "200");
		assertBalance(alice, "800.00");
		assertBalance(bob, "300.00");
		assertEquals(alice.id(), transaction.fromAccountId());
		assertEquals(bob.id(), transaction.toAccountId());
		assertEquals(new BigDecimal("200.00"), transaction.amount());
		assertEquals(NOW, transaction.createdAt());
		assertEquals(List.of(transaction), service.getTransactions(alice.id()));
		assertEquals(List.of(transaction), service.getTransactions(bob.id()));
	}

	@Test
	void permitsTransferOfTheEntireBalance() {
		AccountResponse alice = account("Alice", "0.30");
		AccountResponse bob = account("Bob", "0.10");
		transfer(alice, bob, "0.30");
		assertBalance(alice, "0.00");
		assertBalance(bob, "0.40");
	}

	@Test
	void insufficientFundsLeaveBothBalancesAndHistoryUnchanged() {
		AccountResponse alice = account("Alice", "100");
		AccountResponse bob = account("Bob", "25");
		BankingException error = assertThrows(BankingException.class, () -> transfer(alice, bob, "100.01"));
		assertEquals(Reason.INSUFFICIENT_FUNDS, error.reason());
		assertBalance(alice, "100.00");
		assertBalance(bob, "25.00");
		assertTrue(service.getTransactions(alice.id()).isEmpty());
		assertTrue(service.getTransactions(bob.id()).isEmpty());
	}

	@Test
	void rejectsSameAccountWithoutChangingAnything() {
		AccountResponse alice = account("Alice", "100");
		BankingException error = assertThrows(BankingException.class, () -> transfer(alice, alice, "10"));
		assertEquals(Reason.SAME_ACCOUNT, error.reason());
		assertBalance(alice, "100.00");
		assertTrue(service.getTransactions(alice.id()).isEmpty());
	}

	@Test
	void missingDestinationDoesNotDebitSource() {
		AccountResponse alice = account("Alice", "100");
		BankingException error = assertThrows(BankingException.class, () -> service.transfer(
				new TransferRequest(alice.id(), UUID.randomUUID(), new BigDecimal("10"))));
		assertEquals(Reason.ACCOUNT_NOT_FOUND, error.reason());
		assertBalance(alice, "100.00");
		assertTrue(service.getTransactions(alice.id()).isEmpty());
	}

	@Test
	void missingSourceDoesNotCreditDestination() {
		AccountResponse bob = account("Bob", "100");
		assertThrows(BankingException.class, () -> service.transfer(
				new TransferRequest(UUID.randomUUID(), bob.id(), new BigDecimal("10"))));
		assertBalance(bob, "100.00");
		assertTrue(service.getTransactions(bob.id()).isEmpty());
	}

	@Test
	void unknownAccountAndItsHistoryAreNotFound() {
		UUID missing = UUID.randomUUID();
		assertEquals(Reason.ACCOUNT_NOT_FOUND,
				assertThrows(BankingException.class, () -> service.getAccount(missing)).reason());
		assertEquals(Reason.ACCOUNT_NOT_FOUND,
				assertThrows(BankingException.class, () -> service.getTransactions(missing)).reason());
	}

	@Test
	void recipientBalanceLimitIsCheckedBeforeChangingBalances() {
		AccountResponse alice = account("Alice", "1");
		AccountResponse bob = account("Bob", "999999999999.99");
		BankingException error = assertThrows(BankingException.class, () -> transfer(alice, bob, "0.01"));
		assertEquals(Reason.BALANCE_LIMIT_EXCEEDED, error.reason());
		assertBalance(alice, "1.00");
		assertBalance(bob, "999999999999.99");
		assertTrue(service.getTransactions(alice.id()).isEmpty());
	}

	@Test
	void storageFailureDoesNotMutatePreviouslyReadAccounts() {
		InMemoryBankingRepository repository = new InMemoryBankingRepository() {
			@Override
			public void saveTransfer(AccountData source, AccountData destination, TransactionData transaction) {
				throw new IllegalStateException("Simulated storage failure before commit");
			}
		};
		service = new BankingService(repository, CLOCK);
		AccountResponse alice = account("Alice", "100");
		AccountResponse bob = account("Bob", "0");
		assertThrows(IllegalStateException.class, () -> transfer(alice, bob, "20"));
		assertBalance(alice, "100.00");
		assertBalance(bob, "0.00");
		assertTrue(service.getTransactions(alice.id()).isEmpty());
	}

	@Test
	void historyIncludesIncomingAndOutgoingButExcludesUnrelatedTransactions() {
		AccountResponse alice = account("Alice", "100");
		AccountResponse bob = account("Bob", "100");
		AccountResponse carol = account("Carol", "100");
		TransactionResponse outgoing = transfer(alice, bob, "10");
		TransactionResponse incoming = transfer(carol, alice, "5");
		TransactionResponse unrelated = transfer(bob, carol, "2");
		List<TransactionResponse> history = service.getTransactions(alice.id());
		assertEquals(2, history.size());
		assertTrue(history.containsAll(List.of(outgoing, incoming)));
		assertFalse(history.contains(unrelated));
		assertThrows(UnsupportedOperationException.class, () -> history.clear());
	}

	@Test
	void concurrentTransfersCannotOverdrawAnAccount() throws Exception {
		AccountResponse alice = account("Alice", "100");
		AccountResponse bob = account("Bob", "0");
		AccountResponse carol = account("Carol", "0");
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Boolean>> results = new ArrayList<>();
			for (AccountResponse recipient : List.of(bob, carol)) {
				results.add(executor.submit(() -> {
					ready.countDown();
					if (!start.await(5, TimeUnit.SECONDS)) {
						throw new IllegalStateException("Start signal timed out");
					}
					try {
						transfer(alice, recipient, "80");
						return true;
					} catch (BankingException exception) {
						assertEquals(Reason.INSUFFICIENT_FUNDS, exception.reason());
						return false;
					}
				}));
			}
			assertTrue(ready.await(5, TimeUnit.SECONDS));
			start.countDown();
			int successful = 0;
			for (Future<Boolean> result : results) {
				if (result.get(5, TimeUnit.SECONDS)) {
					successful++;
				}
			}
			assertEquals(1, successful);
			assertBalance(alice, "20.00");
			BigDecimal total = service.getAccount(alice.id()).balance()
					.add(service.getAccount(bob.id()).balance()).add(service.getAccount(carol.id()).balance());
			assertEquals(new BigDecimal("100.00"), total);
			assertEquals(1, service.getTransactions(alice.id()).size());
		} finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void simultaneousOppositeTransfersCompleteWithoutDeadlockAndConserveMoney() throws Exception {
		AccountResponse alice = account("Alice", "100");
		AccountResponse bob = account("Bob", "100");
		ExecutorService executor = Executors.newFixedThreadPool(4);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<?>> results = new ArrayList<>();
			for (int i = 0; i < 20; i++) {
				boolean forward = i % 2 == 0;
				results.add(executor.submit(() -> {
					if (!start.await(5, TimeUnit.SECONDS)) {
						throw new IllegalStateException("Start signal timed out");
					}
					return forward ? transfer(alice, bob, "1") : transfer(bob, alice, "1");
				}));
			}
			start.countDown();
			for (Future<?> result : results) {
				result.get(5, TimeUnit.SECONDS);
			}
			assertBalance(alice, "100.00");
			assertBalance(bob, "100.00");
			assertEquals(20, service.getTransactions(alice.id()).size());
			assertEquals(20, service.getTransactions(bob.id()).size());
		} finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	private AccountResponse account(String owner, String balance) {
		return service.createAccount(new CreateAccountRequest(owner, new BigDecimal(balance)));
	}

	private TransactionResponse transfer(AccountResponse from, AccountResponse to, String amount) {
		return service.transfer(new TransferRequest(from.id(), to.id(), new BigDecimal(amount)));
	}

	private void assertBalance(AccountResponse account, String expected) {
		assertEquals(new BigDecimal(expected), service.getAccount(account.id()).balance());
	}
}
