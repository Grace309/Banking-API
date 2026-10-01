package com.example.banking_api;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AccountStatusIntegrationTest {

	@Autowired
	private MockMvc mvc;
	@Autowired
	private ObjectMapper json;

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void inactiveAccountBlocksEitherTransferDirectionAndCanBeReactivated(boolean inactiveSender) throws Exception {
		String alice = createAccount("Alice");
		String bob = createAccount("Bob");
		transfer(alice, bob, 201);
		String aliceHistory = history(alice);
		String bobHistory = history(bob);
		String inactive = inactiveSender ? alice : bob;

		setStatus(inactive, "INACTIVE");
		setStatus(inactive, "INACTIVE"); // Repeating the same status is a no-op.
		mvc.perform(get("/api/accounts"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == '" + inactive + "')].status").value(contains("INACTIVE")));
		transfer(alice, bob, 409);
		assertAccount(alice, 90, inactiveSender ? "INACTIVE" : "ACTIVE");
		assertAccount(bob, 110, inactiveSender ? "ACTIVE" : "INACTIVE");
		assertEquals(aliceHistory, history(alice));
		assertEquals(bobHistory, history(bob));

		setStatus(inactive, "ACTIVE");
		setStatus(inactive, "ACTIVE");
		transfer(alice, bob, 201);
		assertAccount(alice, 80, "ACTIVE");
		assertAccount(bob, 120, "ACTIVE");
		mvc.perform(get("/api/accounts/{id}/transactions", inactive))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"status\":null}" })
	void requiresStatusWithoutChangingAccount(String body) throws Exception {
		String id = createAccount("Required status");
		mvc.perform(patch("/api/accounts/{id}/status", id).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.status").isString());
		assertAccount(id, 100, "ACTIVE");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "null", "{", "{\"status\":\"FROZEN\"}", "{\"status\":\"inactive\"}", "{\"status\":1}", "{\"status\":\"1\"}" })
	void rejectsMalformedOrUnknownStatusWithoutChangingAccount(String body) throws Exception {
		String id = createAccount("Invalid status");
		mvc.perform(patch("/api/accounts/{id}/status", id).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		assertAccount(id, 100, "ACTIVE");
	}

	@Test
	void cannotChangeStatusOfUnknownAccount() throws Exception {
		mvc.perform(patch("/api/accounts/{id}/status", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
	}

	@Test
	void rejectsMalformedAccountId() throws Exception {
		mvc.perform(patch("/api/accounts/not-a-uuid/status")
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	private String createAccount(String name) throws Exception {
		String body = mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
				.content("{\"ownerName\":\"%s\",\"initialBalance\":100}".formatted(name)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andReturn().getResponse().getContentAsString();
		return json.readTree(body).get("id").asText();
	}

	private void setStatus(String id, String accountStatus) throws Exception {
		mvc.perform(patch("/api/accounts/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"%s\"}".formatted(accountStatus)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.status").value(accountStatus));
	}

	private void transfer(String from, String to, int expectedStatus) throws Exception {
		var result = mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amount\":10}".formatted(from, to)))
				.andExpect(status().is(expectedStatus));
		if (expectedStatus == 409) {
			result.andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
		}
	}

	private void assertAccount(String id, double balance, String accountStatus) throws Exception {
		mvc.perform(get("/api/accounts/{id}", id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value(balance))
				.andExpect(jsonPath("$.status").value(accountStatus));
	}

	private String history(String id) throws Exception {
		return mvc.perform(get("/api/accounts/{id}/transactions", id)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}
}
