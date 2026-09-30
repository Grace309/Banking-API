# Banking API

A Java / Spring Boot REST API for creating accounts, transferring money, and retrieving account transaction history. Data is stored in memory. The application uses Controller, Service, and Repository layers, constructor dependency injection, DTOs, Bean Validation, and consistent JSON errors.

## Prerequisites

- A JDK compatible with Spring Boot 4.1.1 (Java 17 minimum). The project targets Java 17.
- `JAVA_HOME` pointing to the JDK, with `java` available in your terminal.
- Internet access on the first build to download Maven and dependencies. Maven Wrapper is included; a separate Maven installation is not needed.

## Build, test, and run

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

## API

| Method | Path | Purpose | Success |
| --- | --- | --- | --- |
| POST | `/api/accounts` | Create an account | 201, with `Location` header |
| GET | `/api/accounts` | List all account IDs, owner names, and current balances | 200 |
| GET | `/api/accounts/{accountId}` | Retrieve account details and balance | 200 |
| POST | `/api/transfers` | Transfer funds | 201 |
| GET | `/api/accounts/{accountId}/transactions` | Incoming and outgoing transfer history | 200 |
| GET | `/api/health` | Optional liveness endpoint | 200 |

All POST requests use `Content-Type: application/json`.

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
  "balance": 1000.00
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

Both IDs must refer to existing, different accounts. Amount must be positive and have at most 12 integer digits and 2 decimal places. The sender must have enough funds and the recipient's resulting balance cannot exceed `999999999999.99`.

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

`GET /api/accounts` returns all accounts as an array of `{id, ownerName, balance}` objects, sorted by owner name (case-sensitive), then UUID. No input is required. With no accounts it returns `[]`. Names can repeat; use the unique `id` to identify an account. This list is unpaginated and reflects the current in-memory balances.

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
| 409 | `BALANCE_LIMIT_EXCEEDED` | Transfer would exceed the destination balance limit |
| 404 / 405 / 415 | `NOT_FOUND` / `METHOD_NOT_ALLOWED` / `UNSUPPORTED_MEDIA_TYPE` | Invalid route, method, or content type |
| 500 | `INTERNAL_ERROR` | Unexpected failure; internal details are logged, not returned |

All input and business-rule checks complete before a transfer changes stored state.

## Use Swagger UI in your browser

After starting (or restarting) the application, open **http://localhost:8080/swagger-ui.html**. The page lists the account, transfer, and health endpoints and lets you call them directly. No Postman or demo script is required. The underlying OpenAPI JSON is available at `http://localhost:8080/v3/api-docs`.

1. Expand `POST /api/accounts`, click **Try it out**, enter `{"ownerName":"Alice","initialBalance":1000.00}`, then click **Execute**.
2. Under **Server response**, check the 201 status and copy the returned account `id`.
3. Execute the same endpoint with `{"ownerName":"Bob","initialBalance":100.00}` and copy Bob's new `id`.

If you lose an ID, expand **GET `/api/accounts`**, click **Try it out**, then **Execute**. Under **Server response**, find the account by `ownerName` and copy its `id`. You can also open `http://localhost:8080/api/accounts` directly in a browser to see the JSON list.

4. Expand `POST /api/transfers`, click **Try it out**, and replace the example values with the actual IDs and a positive amount:

```json
{
  "fromAccountId": "paste-Alice-id-here",
  "toAccountId": "paste-Bob-id-here",
  "amount": 200.00
}
```

5. Click **Execute**. Query each account through `GET /api/accounts/{accountId}` to check balances of 800 and 300.
6. Use `GET /api/accounts/{accountId}/transactions` to see the transfer in either account's history.

POST requests in Swagger UI really create accounts and transfer funds in the running application's memory. Use IDs from the current server run; restarting the application clears its accounts and transactions. **Example Value** is sample documentation; **Server response** is the actual result of your request.

Swagger UI is provided by `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1`. Its documentation is generated from the existing controllers and DTOs. The two POST methods have `@ApiResponse` annotations so their documented success status correctly shows 201.

## Try a complete scenario with the optional script

Keep the server running in one terminal. Open a second PowerShell terminal in the project folder:

```powershell
.\scripts\demo.ps1
```

If your PowerShell policy blocks this local script, run a separate process with a one-time override (it does not change the machine's persistent policy):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo.ps1
```

The script creates Alice with 1000 and Bob with 100, transfers 200, checks balances of 800 and 300, and shows both histories. Each execution creates new accounts.

For individual requests without running the script:

```powershell
$apiBase = 'http://localhost:8080'
$alice = Invoke-RestMethod -Method Post -Uri "$apiBase/api/accounts" -ContentType 'application/json' -Body '{"ownerName":"Alice","initialBalance":1000.00}'
$bob = Invoke-RestMethod -Method Post -Uri "$apiBase/api/accounts" -ContentType 'application/json' -Body '{"ownerName":"Bob","initialBalance":100.00}'
$transferBody = @{ fromAccountId = $alice.id; toAccountId = $bob.id; amount = 200.00 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$apiBase/api/transfers" -ContentType 'application/json' -Body $transferBody
Invoke-RestMethod -Uri "$apiBase/api/accounts/$($alice.id)"
Invoke-RestMethod -Uri "$apiBase/api/accounts/$($bob.id)"
Invoke-RestMethod -Uri "$apiBase/api/accounts/$($alice.id)/transactions"
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

### Concurrency and atomicity

All public operations on the singleton `BankingService` synchronize on the same service instance. This covers the full account lookup, validation, balance calculation, and save, so simultaneous requests cannot both spend a stale balance. The same monitor covers account creation and reads. This is deliberately simple and serializes requests for this small, single-instance task.

The repository stores immutable records in an immutable `State`. For a transfer, it prepares copies of the account and transaction maps, then publishes the complete new state with one assignment. If constructing that state fails, the previous state remains intact. Returned account records cannot be modified to mutate stored balances. Repository writes are also synchronized, and the published state is volatile.

This is an in-memory consistency mechanism, not a database transaction. Plain `@Transactional` would not roll back these maps. Using only a `ConcurrentHashMap` would not make a multi-step transfer atomic.

Copying maps costs O(accounts + transactions) per transfer; history lookup scans and sorts matching transactions. These are acceptable for the requested small in-memory application, but would not be the design for a large deployment. A database implementation would need database transactions and concurrency controls covering the entire transfer, not just an implementation of the save methods. Multiple service instances sharing one repository would likewise require a different locking boundary.

## Assumptions and scope

- One implicit currency with 2 decimal places; no currency conversion. Amounts use `BigDecimal`, never floating-point arithmetic in the business logic. Extra decimal places are rejected, not silently rounded.
- The maximum account balance and request amount is `999999999999.99`.
- Each API account is a standalone account with an owner name. There is no separate user/login model; names need not be unique.
- Account IDs and transaction IDs are server-generated UUIDs. Owner names are trimmed on creation.
- Initial balance is opening funding and is not a transaction-history entry. Only successful transfers enter history; the same transaction ID appears for both parties.
- Transfers have no fees, overdrafts, self-transfers, pending status, or external bank integration.
- A repeated POST creates a new operation. Idempotency/retry deduplication is not implemented.
- All state is process-local, reset on restart, and not shared between application instances.
- History is unpaginated for the small take-home scope.
- Authentication/authorization and persistence are outside this task; this is a demonstration API, not a production banking service.
- `GET /api/accounts/{id}` is an added convenience for checking balances. `HealthController` is an optional liveness/learning endpoint and is not one of the three core assignment requirements.
- `GET /api/accounts` is an added convenience for finding account IDs in Swagger UI.

## Tests

`./mvnw test` (Windows: `.\mvnw.cmd test`) runs:

- `BankingServiceTest`: balance calculations, exact-balance transfer, missing accounts, self-transfer, insufficient funds, recipient limit, history filtering, simulated pre-commit failure, concurrent overspending, and concurrent opposite-direction transfers.
- `InMemoryBankingRepositoryTest`: state preservation when constructing a commit fails, and chronological history order.
- `BankingApiIntegrationTest`: full Spring context with MockMvc, JSON mapping, UUID parsing, DTO validation, HTTP statuses, Location headers, error formatting, and account-transfer-history flows.
- `BankingApiApplicationTests`: the generated context-startup smoke test.
- `SwaggerIntegrationTest`: the UI entry point, HTML page, configuration, and generated endpoint/request-field documentation.

Reports are generated under `target/surefire-reports/`.

Verified on Windows with JDK 19.0.2 compiling to Java 17. The latest `mvnw.cmd -B test` passed 57 tests, including Swagger documentation and account listing with duplicate names and updated balances. Earlier real-HTTP verification covers the banking demo (Alice 800, Bob 300, matching histories), a negative-balance validation check (400), Swagger UI, and OpenAPI document generation. Java 17 is the compilation target; verification used the locally installed Java 19 runtime.

## Submission

For submission, put this project in your GitHub repository and provide its link. Include source, tests, `pom.xml`, Maven Wrapper files (including `.mvn`), README, and documentation. Exclude generated `target/`, IDE configuration, and local dependency caches. `.gitignore` is provided. No database or credentials are required to run the project.
