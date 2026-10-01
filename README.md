# Banking API

A Java / Spring Boot REST API for creating accounts, transferring money, and retrieving account balances and transaction history. Use the built-in Swagger UI to try the complete flow in your browser.

Data is stored in memory and cleared when the application restarts. No database setup or credentials are required.

## Tech stack

| Component | Version / implementation |
| --- | --- |
| Java | 17 compilation target |
| Spring Boot | 4.1.1 |
| HTTP API | Spring MVC, JSON request/response DTOs, Jakarta Bean Validation |
| API documentation | springdoc OpenAPI starter 3.1.1 with Swagger UI |
| Storage | In-memory repository with immutable state |
| Build | Maven Wrapper |
| Tests | JUnit, Spring Boot Test, MockMvc |

## Quick start

### Prerequisites

- JDK 17 or later, compatible with the configured Spring Boot version.
- `JAVA_HOME` pointing to the JDK, with `java` available in your terminal.
- Internet access on the first build to download Maven and dependencies. Maven Wrapper is included; a separate Maven installation is not needed.

### Build, test, and run

Run these commands from the folder containing `pom.xml`.

Windows PowerShell:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

macOS / Linux:

```bash
sh mvnw clean verify
sh mvnw spring-boot:run
```

`verify` compiles the application, runs the tests, and builds an executable JAR. To run the packaged application instead:

```powershell
java -jar .\target\banking-api-0.0.1-SNAPSHOT.jar
```

The default address is `http://localhost:8080`. Stop it with Ctrl+C. If another application is using the port, stop that application or run the JAR with `--server.port=8081`.

### Local URLs

Open these links while the application is running:

| Page / endpoint | URL |
| --- | --- |
| Swagger UI | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |
| OpenAPI JSON | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) |
| Account list | [http://localhost:8080/api/accounts](http://localhost:8080/api/accounts) |
| Health check | [http://localhost:8080/api/health](http://localhost:8080/api/health) |

The Swagger entry point redirects to `/swagger-ui/index.html`. The health endpoint returns `{"status":"UP"}`. If you change the server port, update the URLs accordingly.

## Try the API with Swagger UI

Swagger UI lists the account, transfer, and health endpoints. Expand an endpoint, click **Try it out**, supply any required input, and click **Execute**. No authentication is required.

### 1. Create two accounts

Expand **POST `/api/accounts`** and execute this request:

```json
{
  "ownerName": "Alice",
  "initialBalance": 1000.00
}
```

Under **Server response**, expect **201 Created**. Copy the generated `id` from the response body; you will use it as Alice's account ID.

Execute the same endpoint again with:

```json
{
  "ownerName": "Bob",
  "initialBalance": 100.00
}
```

Copy Bob's generated `id` as well. Each execution creates a new account, even if the owner name already exists.

### 2. Find account IDs and balances

Expand **GET `/api/accounts`**, click **Try it out**, then **Execute**. No input is required. The response lists each account's `id`, `ownerName`, current `balance`, and `status` (`ACTIVE` or `INACTIVE`). Inactive accounts remain in the list.

Use this endpoint whenever you need to find an ID again. Names can repeat, so identify accounts by their unique IDs. A fresh application with no accounts returns `[]`.

### 3. Transfer funds

Expand **POST `/api/transfers`** and replace both placeholder strings below with the actual UUIDs returned when creating Alice and Bob:

```json
{
  "fromAccountId": "paste-Alice-id-here",
  "toAccountId": "paste-Bob-id-here",
  "amount": 200.00
}
```

Click **Execute**. Expect **201 Created** with the transaction's `id`, both account IDs, `amount`, and UTC `createdAt` timestamp. The placeholder strings are not valid UUIDs and must be replaced before execution.

### 4. Check balances and history

Execute **GET `/api/accounts`** again, or use **GET `/api/accounts/{accountId}`** with each account's ID. After one successful transfer:

| Account | Opening balance | Current balance |
| --- | ---: | ---: |
| Alice | 1000.00 | 800.00 |
| Bob | 100.00 | 300.00 |

Execute **GET `/api/accounts/{accountId}/transactions`** for Alice and then Bob. Both histories contain the same transfer ID. Opening balances do not create history entries.

### 5. Try an error response

Using the same IDs, attempt to transfer `900.00` from Alice to Bob. Alice now has only `800.00`, so the API returns **409 Conflict** with code `INSUFFICIENT_FUNDS`. Both balances and histories remain unchanged.

**Swagger tips:** **Example Value** shows sample documentation; **Server response** shows the actual result of your request. POST requests change the running application's data, so clicking **Execute** again repeats the operation. Use IDs from the current server run: restarting clears all accounts and transfers, and old IDs return 404.

### 6. Deactivate and reactivate an account

New accounts start as `ACTIVE`. Expand **PATCH `/api/accounts/{accountId}/status`**, click **Try it out**, enter Alice's actual ID in `accountId`, and execute:

```json
{"status":"INACTIVE"}
```

Expect **200 OK** with the account's unchanged ID, name and balance, plus `"status":"INACTIVE"`. Try a transfer from Alice to Bob, then from Bob to Alice: both return **409 Conflict** with `ACCOUNT_INACTIVE`, without changing balances or transaction histories. Account details, listing and existing history remain available.

To reactivate Alice, execute the same PATCH endpoint with `{"status":"ACTIVE"}`. Transfers can then succeed again, subject to the usual validation and balance rules. Repeating the current status is a successful no-op. Status changes do not create transfer-history entries.

Status is required and must be exactly `ACTIVE` or `INACTIVE`. Missing or null status returns 400 `VALIDATION_ERROR`; unknown values, lowercase values and numeric enum ordinals return 400 `INVALID_REQUEST`. An unknown account returns 404 `ACCOUNT_NOT_FOUND`.

## API

| Method | Path | Purpose | Success |
| --- | --- | --- | --- |
| POST | `/api/accounts` | Create an account | 201, with `Location` header |
| GET | `/api/accounts` | List all account IDs, owner names, and current balances | 200 |
| GET | `/api/accounts/{accountId}` | Retrieve account details and balance | 200 |
| PATCH | `/api/accounts/{accountId}/status` | Manually deactivate or reactivate an account | 200 |
| POST | `/api/transfers` | Transfer funds | 201 |
| GET | `/api/accounts/{accountId}/transactions` | Incoming and outgoing transfer history | 200 |
| GET | `/api/health` | Return application liveness status | 200 |

All POST and PATCH requests use `Content-Type: application/json`.

### Create an account

```http
POST /api/accounts
Content-Type: application/json

{"ownerName":"Alice","initialBalance":1000.00}
```

Example response (IDs are generated, so actual values differ):

```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "ownerName": "Alice",
  "balance": 1000.00,
  "status": "ACTIVE"
}
```

`Location: /api/accounts/11111111-1111-4111-8111-111111111111`

Name is required, cannot be blank, and is at most 100 characters before trimming. Initial balance is required, nonnegative, and has at most 12 integer digits and 2 decimal places.

### Transfer funds

```http
POST /api/transfers
Content-Type: application/json

{
  "fromAccountId":"11111111-1111-4111-8111-111111111111",
  "toAccountId":"22222222-2222-4222-8222-222222222222",
  "amount":200.00
}
```

Both IDs must refer to existing, different, ACTIVE accounts. An inactive sender or recipient is rejected before balance changes. Amount must be positive and have at most 12 integer digits and 2 decimal places. The sender must have enough funds and the recipient's resulting balance cannot exceed `999999999999.99`.

Example response:

```json
{
  "id": "33333333-3333-4333-8333-333333333333",
  "fromAccountId": "11111111-1111-4111-8111-111111111111",
  "toAccountId": "22222222-2222-4222-8222-222222222222",
  "amount": 200.00,
  "createdAt": "2026-09-30T12:00:00Z"
}
```

### Retrieve account / transaction history

`GET /api/accounts` returns all accounts as an array of `{id, ownerName, balance, status}` objects, sorted by owner name (case-sensitive), then UUID. Both active and inactive accounts are included. No input is required. With no accounts it returns `[]`. Names can repeat; use the unique `id` to identify an account. This list is unpaginated and reflects the current in-memory balances.

`GET /api/accounts/{accountId}` returns the same account fields as account creation, with the current balance.

`GET /api/accounts/{accountId}/transactions` returns an array of the transaction objects shown above. It includes incoming and outgoing transfers, sorted by UTC timestamp ascending, then UUID for a deterministic tie-break. An existing account with no transfers returns `[]`. An unknown account returns 404.

### Errors

All handled API errors use this structure:

```json
{
  "code": "INSUFFICIENT_FUNDS",
  "message": "Source account has insufficient funds",
  "fieldErrors": {}
}
```

Validation errors identify fields:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "fieldErrors": {
    "initialBalance": "Initial balance cannot be negative"
  }
}
```

| HTTP status | Code | Situation |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Missing, blank, negative, zero transfer amount, or out-of-range DTO fields |
| 400 | `INVALID_REQUEST` | Missing/malformed JSON, invalid field type, or invalid UUID syntax |
| 400 | `SAME_ACCOUNT` | Source and destination are the same existing account |
| 404 | `ACCOUNT_NOT_FOUND` | A well-formed account ID does not exist |
| 409 | `INSUFFICIENT_FUNDS` | Transfer would overdraw the source |
| 409 | `ACCOUNT_INACTIVE` | Transfer involves an inactive sender or recipient |
| 409 | `BALANCE_LIMIT_EXCEEDED` | Transfer would exceed the destination balance limit |
| 404 / 405 / 415 | `NOT_FOUND` / `METHOD_NOT_ALLOWED` / `UNSUPPORTED_MEDIA_TYPE` | Invalid route, method, or content type |
| 500 | `INTERNAL_ERROR` | Unexpected failure; internal details are logged, not returned |

All input and business-rule checks complete before a transfer changes stored state.

## Try the API from PowerShell

Keep the server running in one terminal, then paste these commands into a second PowerShell terminal. They create two new accounts, transfer 200, and retrieve balances and both histories using the IDs returned by the server:

```powershell
$apiBase = 'http://localhost:8080'
$alice = Invoke-RestMethod -Method Post -Uri "$apiBase/api/accounts" -ContentType 'application/json' -Body '{"ownerName":"Alice","initialBalance":1000.00}'
$bob = Invoke-RestMethod -Method Post -Uri "$apiBase/api/accounts" -ContentType 'application/json' -Body '{"ownerName":"Bob","initialBalance":100.00}'
$transferBody = @{ fromAccountId = $alice.id; toAccountId = $bob.id; amount = 200.00 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$apiBase/api/transfers" -ContentType 'application/json' -Body $transferBody
Invoke-RestMethod -Uri "$apiBase/api/accounts/$($alice.id)"
Invoke-RestMethod -Uri "$apiBase/api/accounts/$($bob.id)"
Invoke-RestMethod -Uri "$apiBase/api/accounts/$($alice.id)/transactions"
Invoke-RestMethod -Uri "$apiBase/api/accounts/$($bob.id)/transactions"
```

## Design

```text
HTTP / JSON
  -> AccountController / TransferController
  -> request DTO validation (@Valid)
  -> BankingService
  -> BankingRepository interface
  -> InMemoryBankingRepository
  -> immutable maps in application memory
```

- **Controllers** translate HTTP requests and responses. They do not calculate balances.
- **Service** implements account and transfer rules, money calculations, and DTO mapping. Spring injects its repository and clock through the constructor.
- **Repository** encapsulates storage. Its interface can be implemented differently without changing controllers.
- **DTOs**: `dto` contains HTTP request/response records; `repository.dto` contains immutable records passed between Service and Repository. Internal maps never leave the repository.
- **Errors**: `GlobalExceptionHandler` translates validation/framework exceptions and business failures to consistent JSON responses.
- **Time**: a UTC `Clock` bean makes production timestamps explicit and tests deterministic.
- **API documentation**: springdoc generates the OpenAPI document and Swagger UI from controllers and DTOs. Both POST endpoints explicitly document their 201 success status; the account-list endpoint includes a summary for finding IDs and balances.

### Concurrency and atomicity

All public operations on the singleton `BankingService` synchronize on the same service instance. This covers the full account lookup, validation, balance calculation, and save, so simultaneous requests cannot both spend a stale balance. The same monitor covers account creation and reads. This is deliberately simple and serializes requests for this small, single-instance application.

The repository stores immutable records in an immutable `State`. For a transfer, it prepares copies of the account and transaction maps, then publishes the complete new state with one assignment. If constructing that state fails, the previous state remains intact. Returned account records cannot be modified to mutate stored balances. Repository writes are also synchronized, and the published state is volatile.

Status updates use the same service monitor as transfers. A concurrent transfer either finishes before deactivation or observes the inactive status and fails; it cannot overwrite deactivation using an old account snapshot. `AccountData.withBalance()` preserves status, and `withStatus()` preserves the balance. Re-enabling an account preserves its ID and history.

This is an in-memory consistency mechanism, not a database transaction. Plain `@Transactional` would not roll back these maps. Using only a `ConcurrentHashMap` would not make a multi-step transfer atomic.

Copying maps costs O(accounts + transactions) per transfer; history lookup scans and sorts matching transactions. A database implementation would need database transactions and concurrency controls covering the entire transfer, not just an implementation of the save methods. Multiple service instances sharing one repository would likewise require a different locking boundary.

## Assumptions and scope

- One implicit currency with 2 decimal places; no currency conversion. Amounts use `BigDecimal`, never floating-point arithmetic in the business logic. Extra decimal places are rejected, not silently rounded.
- The maximum account balance and request amount is `999999999999.99`.
- Each API account is a standalone account with an owner name. There is no separate user/login model; names need not be unique.
- Account IDs and transaction IDs are server-generated UUIDs. Owner names are trimmed on creation.
- Account status is manually controlled; there is no inactivity timer. `INACTIVE` blocks incoming and outgoing transfers but allows reads and reactivation. These are this demo's business rules. Status changes are not recorded as money transfers or separate audit events.
- Initial balance is opening funding and is not a transaction-history entry. Only successful transfers enter history; the same transaction ID appears for both parties.
- Transfers have no fees, overdrafts, self-transfers, pending status, or external bank integration.
- A repeated POST creates a new operation. Idempotency/retry deduplication is not implemented.
- All state is process-local, reset on restart, and not shared between application instances.
- Account lists and transaction histories are unpaginated.
- This is a demonstration API. Authentication, authorization, and persistent storage are not implemented.

## Tests

Run `.\mvnw.cmd test` on Windows or `sh mvnw test` on macOS / Linux. The suite includes:

- `BankingServiceTest`: balance calculations, exact-balance transfer, missing accounts, self-transfer, insufficient funds, recipient limit, history filtering, simulated pre-commit failure, concurrent overspending, and concurrent opposite-direction transfers.
- `InMemoryBankingRepositoryTest`: state preservation when constructing a commit fails, and chronological history order.
- `BankingApiIntegrationTest`: full Spring context with MockMvc, JSON mapping, UUID parsing, DTO validation, HTTP statuses, Location headers, error formatting, and account-transfer-history flows.
- `BankingApiApplicationTests`: the generated context-startup smoke test.
- `AccountStatusIntegrationTest`: default ACTIVE status, manual deactivation/reactivation, idempotent status updates, both transfer directions blocked while inactive, preserved balances/history, readable inactive accounts, and invalid status/ID handling. The service suite also checks concurrent deactivation and transfer.
- `SwaggerIntegrationTest`: the UI redirect, HTML page, configuration, documented endpoints, required request fields, and 201 success responses for both POST endpoints.

Reports are generated under `target/surefire-reports/`.

To run just the Swagger and HTTP API integration tests on Windows:

```powershell
.\mvnw.cmd "-Dtest=SwaggerIntegrationTest,BankingApiIntegrationTest" test
```
