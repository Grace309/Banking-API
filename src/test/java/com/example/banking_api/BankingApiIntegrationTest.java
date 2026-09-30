package com.example.banking_api;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class BankingApiIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ObjectMapper json;

	@Test
	void healthEndpointReturnsJson() throws Exception {
		mvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void createsAnAccountWithLocationAndReturnsItsBalance() throws Exception {
		String accountId = createAccount("Alice", "1000");
		mvc.perform(get("/api/accounts/{id}", accountId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(accountId))
				.andExpect(jsonPath("$.ownerName").value("Alice"))
				.andExpect(jsonPath("$.balance").value(1000.00));
		mvc.perform(get("/api/accounts/{id}/transactions", accountId))
				.andExpect(status().isOk()).andExpect(content().json("[]"));
	}

	@Test
	void listsCreatedAccountIdsAndCurrentBalancesAfterTransfer() throws Exception {
		MvcResult before = mvc.perform(get("/api/accounts"))
				.andExpect(status().isOk()).andReturn();
		int previousCount = json.readTree(before.getResponse().getContentAsString()).size();
		String alice = createAccount("Alice", "1000");
		String bob = createAccount("Bob", "100");
		mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
				.content(transferJson(alice, bob, "200.00")))
				.andExpect(status().isCreated());
		mvc.perform(get("/api/accounts"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$", hasSize(previousCount + 2)))
				.andExpect(jsonPath("$[?(@.id == '" + alice + "')].ownerName").value(org.hamcrest.Matchers.contains("Alice")))
				.andExpect(jsonPath("$[?(@.id == '" + alice + "')].balance").value(org.hamcrest.Matchers.contains(800.00)))
				.andExpect(jsonPath("$[?(@.id == '" + bob + "')].ownerName").value(org.hamcrest.Matchers.contains("Bob")))
				.andExpect(jsonPath("$[?(@.id == '" + bob + "')].balance").value(org.hamcrest.Matchers.contains(300.00)));
	}

	@Test
	void completeTransferUpdatesBothBalancesAndBothHistories() throws Exception {
		String alice = createAccount("Alice", "1000");
		String bob = createAccount("Bob", "100");
		MvcResult transfer = mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
				.content(transferJson(alice, bob, "200.00")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.fromAccountId").value(alice))
				.andExpect(jsonPath("$.toAccountId").value(bob))
				.andExpect(jsonPath("$.amount").value(200.00))
				.andExpect(jsonPath("$.createdAt").isString())
				.andReturn();
		String transactionId = json.readTree(transfer.getResponse().getContentAsString()).get("id").asText();
		assertBalance(alice, 800.00);
		assertBalance(bob, 300.00);
		for (String accountId : new String[] { alice, bob }) {
			mvc.perform(get("/api/accounts/{id}/transactions", accountId))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$", hasSize(1)))
					.andExpect(jsonPath("$[0].id").value(transactionId));
		}
	}

	@ParameterizedTest
	@MethodSource("invalidAccounts")
	void validatesAccountInput(String body, String field) throws Exception {
		mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors." + field).isString());
	}

	static Stream<Arguments> invalidAccounts() {
		return Stream.of(
				Arguments.of("{}", "ownerName"),
				Arguments.of("{\"ownerName\":null,\"initialBalance\":0}", "ownerName"),
				Arguments.of("{\"ownerName\":\"   \",\"initialBalance\":0}", "ownerName"),
				Arguments.of("{\"ownerName\":\"" + "A".repeat(101) + "\",\"initialBalance\":0}", "ownerName"),
				Arguments.of("{\"ownerName\":\"Alice\"}", "initialBalance"),
				Arguments.of("{\"ownerName\":\"Alice\",\"initialBalance\":null}", "initialBalance"),
				Arguments.of("{\"ownerName\":\"Alice\",\"initialBalance\":-1}", "initialBalance"),
				Arguments.of("{\"ownerName\":\"Alice\",\"initialBalance\":0.001}", "initialBalance"),
				Arguments.of("{\"ownerName\":\"Alice\",\"initialBalance\":1000000000000}", "initialBalance"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-1", "0.001", "1000000000000", "null" })
	void rejectsInvalidTransferAmountsWithoutChangingAccounts(String amount) throws Exception {
		String alice = createAccount("Alice", "100");
		String bob = createAccount("Bob", "0");
		mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
				.content(transferJson(alice, bob, amount)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.amount").isString());
		assertBalance(alice, 100.00);
		assertBalance(bob, 0.00);
		assertEmptyHistory(alice);
		assertEmptyHistory(bob);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"amount\":1}", "{\"fromAccountId\":null,\"toAccountId\":null,\"amount\":1}" })
	void rejectsMissingTransferFields(String body) throws Exception {
		mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.fromAccountId").isString())
				.andExpect(jsonPath("$.fieldErrors.toAccountId").isString());
	}

	@Test
	void rejectsMissingAmount() throws Exception {
		String body = "{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\"}".formatted(UUID.randomUUID(), UUID.randomUUID());
		mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.amount").isString());
	}

	@Test
	void insufficientFundsReturnsConflictWithUnchangedState() throws Exception {
		String alice = createAccount("Alice", "100");
		String bob = createAccount("Bob", "0");
		mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
				.content(transferJson(alice, bob, "100.01")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
		assertBalance(alice, 100.00);
		assertBalance(bob, 0.00);
		assertEmptyHistory(alice);
		assertEmptyHistory(bob);
	}

	@Test
	void selfTransferReturnsBadRequest() throws Exception {
		String alice = createAccount("Alice", "100");
		mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
				.content(transferJson(alice, alice, "1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SAME_ACCOUNT"));
		assertBalance(alice, 100.00);
		assertEmptyHistory(alice);
	}

	@Test
	void recipientBalanceOverflowReturnsConflict() throws Exception {
		String alice = createAccount("Alice", "1");
		String bob = createAccount("Bob", "999999999999.99");
		mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
				.content(transferJson(alice, bob, "0.01")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BALANCE_LIMIT_EXCEEDED"));
		assertBalance(alice, 1.00);
		assertEmptyHistory(alice);
		assertEmptyHistory(bob);
	}

	@Test
	void nonexistentAccountIs404ForReadsHistoryAndTransfers() throws Exception {
		String missing = UUID.randomUUID().toString();
		for (String path : new String[] { "/api/accounts/" + missing, "/api/accounts/" + missing + "/transactions" }) {
			mvc.perform(get(path)).andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
		}
		String alice = createAccount("Alice", "100");
		mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
				.content(transferJson(alice, missing, "1")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
		assertBalance(alice, 100.00);
		assertEmptyHistory(alice);
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/accounts/not-a-uuid", "/api/accounts/not-a-uuid/transactions" })
	void malformedPathIdReturns400(String path) throws Exception {
		mvc.perform(get(path)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.fieldErrors.accountId").isString());
	}

	@Test
	void malformedBodyUuidReturns400() throws Exception {
		mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
				.content(transferJson("not-a-uuid", UUID.randomUUID().toString(), "1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "null", "{", "[]", "{\"ownerName\":\"Alice\",\"initialBalance\":\"not-money\"}" })
	void missingOrMalformedJsonHasConsistentErrors(String body) throws Exception {
		mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").isString());
	}

	@Test
	void unsupportedMethodAndContentTypeHaveConsistentErrors() throws Exception {
		mvc.perform(put("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
		mvc.perform(post("/api/accounts").contentType(MediaType.TEXT_PLAIN).content("hello"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	void unknownEndpointHasConsistent404() throws Exception {
		mvc.perform(get("/api/does-not-exist"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	private String createAccount(String name, String balance) throws Exception {
		MvcResult result = mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
				.content("{\"ownerName\":\"%s\",\"initialBalance\":%s}".formatted(name, balance)))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(header().exists("Location"))
				.andReturn();
		String id = json.readTree(result.getResponse().getContentAsString()).get("id").asText();
		UUID.fromString(id);
		assertEquals("/api/accounts/" + id, result.getResponse().getHeader("Location"));
		return id;
	}

	private String transferJson(String from, String to, String amount) {
		return "{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amount\":%s}".formatted(from, to, amount);
	}

	private void assertBalance(String id, double expected) throws Exception {
		mvc.perform(get("/api/accounts/{id}", id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value(expected));
	}

	private void assertEmptyHistory(String id) throws Exception {
		mvc.perform(get("/api/accounts/{id}/transactions", id))
				.andExpect(status().isOk()).andExpect(content().json("[]"));
	}
}
