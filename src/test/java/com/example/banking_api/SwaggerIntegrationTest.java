package com.example.banking_api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void swaggerEntryRedirectsToTheUi() throws Exception {
		mvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/swagger-ui/index.html"));
	}

	@Test
	void swaggerUiAndItsConfigurationAreAvailable() throws Exception {
		mvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("Swagger UI")));
		mvc.perform(get("/v3/api-docs/swagger-config"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value("/v3/api-docs"));
	}

	@Test
	void openApiDocumentsTheBankingEndpointsAndRequiredRequestFields() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.paths['/api/accounts'].post").exists())
				.andExpect(jsonPath("$.paths['/api/accounts'].get.responses['200']").exists())
				.andExpect(jsonPath("$.paths['/api/accounts'].post.responses['201']").exists())
				.andExpect(jsonPath("$.paths['/api/accounts'].post.responses['200']").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/accounts/{accountId}'].get").exists())
				.andExpect(jsonPath("$.paths['/api/accounts/{accountId}/transactions'].get").exists())
				.andExpect(jsonPath("$.paths['/api/transfers'].post").exists())
				.andExpect(jsonPath("$.paths['/api/transfers'].post.responses['201']").exists())
				.andExpect(jsonPath("$.paths['/api/transfers'].post.responses['200']").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/health'].get").exists())
				.andExpect(jsonPath("$.components.schemas.CreateAccountRequest.required")
						.value(hasItems("ownerName", "initialBalance")))
				.andExpect(jsonPath("$.components.schemas.TransferRequest.required")
						.value(hasItems("fromAccountId", "toAccountId", "amount")));
	}
}
