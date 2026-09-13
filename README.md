# Paytm Money Wallet Assignment – P2P Transfer Service

A deployable Spring Boot and PostgreSQL service for wallet creation and peer-to-peer transfers. The design prioritizes correctness under concurrency: money is integer paise, transfers are atomic, balances cannot become negative, and a same-key retry moves money exactly once.

This repository is the source of truth for local development, containerized execution, tests, deployment instructions, and the final technical explanation in [TECHNICAL_WRITEUP.md](TECHNICAL_WRITEUP.md).

## Assignment deliverables

| Deliverable                | Location or status                                                       |
|----------------------------|--------------------------------------------------------------------------|
| Public source repository   | This repository                                                          |
| Live API URL               | `https://wallet-p2p-transfer.onrender.com`                               |
| Swagger UI                 | `https://wallet-p2p-transfer.onrender.com/swagger-ui.html`               |
| Health                     | `https://wallet-p2p-transfer.onrender.com/actuator/health`               |
| Metrics                    | `https://wallet-p2p-transfer.onrender.com/actuator/prometheus`           |
| One-command live probe     | `python scripts/run-burst.py https://wallet-p2p-transfer.onrender.com`   |
| Automated PostgreSQL tests | `mvn test -Dtest=WalletConcurrencyIntegrationTest`                       |
| Log evidence               | Render Live Tail recording during a burst, since the resource is private |
| One-page write-up          | [TECHNICAL_WRITEUP.md](TECHNICAL_WRITEUP.md)                             |

## Features and guarantees

### APIs

| Method | Endpoint | Behavior |
| --- | --- | --- |
| `POST` | `/wallets` | Gets or creates the caller's wallet. |
| `GET` | `/wallets/{id}` | Returns the caller's wallet balance. |
| `POST` | `/transfers` | Creates an idempotent P2P transfer. |
| `GET` | `/transfers/{id}` | Returns a transfer visible to its sender or recipient. |
| `GET` | `/actuator/health` | Application health. |
| `GET` | `/actuator/prometheus` | Prometheus-format metrics. |
| `GET` | `/swagger-ui.html` | Swagger UI. |

### Correctness invariants

- One `wallets` row per user, enforced by a unique `user_id` constraint.
- Amounts and balances are `BIGINT` paise; no float or decimal rupee calculations are used.
- The sum of wallet balances is unchanged by a transfer.
- A wallet never becomes negative; the debit is a conditional database update.
- A transfer either completes atomically or is recorded as declined; it cannot partially apply.
- `transfers.idempotency_key` is unique. Same key plus same body returns the original transfer; a changed body returns `409 Conflict`.
- Opposite-direction transfers use deterministic PostgreSQL UUID lock ordering to avoid avoidable deadlocks.

## Architecture

```text
HTTP client / burst probe
        |
        v
Filters: correlation ID, bearer identity
        |
        v
Controllers and DTOs
        |
        v
Service interfaces and implementations
        |
        +--> Spring Data JPA: mapped reads and ownership checks
        +--> JdbcTemplate: locks, idempotency claim, debit, credit, status updates
        |
        v
PostgreSQL + Flyway migrations
```

```text
api/controller       HTTP endpoints
api/dto              request and response contracts
api/exception        consistent JSON errors
config               correlation ID and OpenAPI configuration
model                domain records and JPA entities
repository           JPA read repositories and explicit critical SQL
security             simple bearer-token caller identity
service / service.impl use-case contracts and transaction logic
observability        Micrometer domain counters
util                 structured entry logging helpers
```

## Prerequisites

- Java 17
- PostgreSQL 16+ installed locally, or Docker Desktop
- Maven Wrapper (`mvnw.cmd` on Windows)
- Python 3.10+ for the live burst script
- Docker Desktop for Testcontainers integration tests

Default local connection:

```text
JDBC URL: jdbc:postgresql://localhost:5432/paytm_wallet
Username: postgres
Password: postgres
```

Override with `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.

## Local setup with installed PostgreSQL

Create the empty database once in DBeaver or `psql`:

```sql
CREATE DATABASE paytm_wallet;
```

In PowerShell, set values only if they differ from defaults:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/paytm_wallet'
$env:DB_USERNAME = 'postgres'
$env:DB_PASSWORD = 'your-local-password'
.\mvnw.cmd spring-boot:run
```

On first startup Flyway creates `users`, `wallets`, `transfers`, and `flyway_schema_history`. Expected evidence:

```text
Migrating schema "public" to version "1 - wallet schema"
Successfully applied 1 migration
```

Spring Boot 4 requires `org.springframework.boot:spring-boot-flyway` in addition to Flyway dependencies. If Hibernate reports a missing table, confirm that dependency exists and that the application started successfully.

Verify in your preferred Database Management Tool:

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;

SELECT version, description, script, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

## Local setup with Docker Compose

Docker Compose starts PostgreSQL and the service:

```powershell
docker compose up --build
```

The API is at `http://localhost:8080`. Stop with `Ctrl+C`. To intentionally delete only the Compose database volume:

```powershell
docker compose down -v
```

This does not affect installed PostgreSQL or Render.

## Authentication and API examples

A bearer token identifies the caller. The raw token is never logged.

```powershell
$base = 'http://localhost:8080'

$alice = Invoke-RestMethod "$base/wallets" -Method Post `
  -Headers @{ Authorization = 'Bearer alice' } -ContentType 'application/json'

$bob = Invoke-RestMethod "$base/wallets" -Method Post `
  -Headers @{ Authorization = 'Bearer bob' } -ContentType 'application/json'

$transfer = Invoke-RestMethod "$base/transfers" -Method Post `
  -Headers @{ Authorization = 'Bearer alice' } -ContentType 'application/json' `
  -Body (@{
    from = $alice.wallet_id
    to = $bob.wallet_id
    amount_paise = 5000
    idempotency_key = 'demo-alice-to-bob-001'
  } | ConvertTo-Json)
```

Read balances and transfer details:

```powershell
Invoke-RestMethod "$base/wallets/$($alice.wallet_id)" -Headers @{ Authorization = 'Bearer alice' }
Invoke-RestMethod "$base/wallets/$($bob.wallet_id)" -Headers @{ Authorization = 'Bearer bob' }
Invoke-RestMethod "$base/transfers/$($transfer.transfer_id)" -Headers @{ Authorization = 'Bearer alice' }
```

| Scenario | Expected result |
| --- | --- |
| Same idempotency key and same body | Original transfer returned; no second movement. |
| Same key and changed body | `409 Conflict`. |
| Amount greater than source balance | `422 Unprocessable Entity`, `DECLINED`. |
| Source belongs to another caller | `403 Forbidden`. |
| Invalid request or non-positive amount | `400 Bad Request`. |

Swagger is available at `http://localhost:8080/swagger-ui.html`. Use **Authorize** with a token such as `alice` before calling protected endpoints.

## Testing and correctness evidence

### Automated integration test

The integration suite starts isolated `postgres:16-alpine` through Testcontainers, starts Spring Boot on a random port, and sends real HTTP requests. It does not modify local or Render databases.

```powershell
.\mvnw.cmd test -Dtest=WalletConcurrencyIntegrationTest
```

Docker Desktop must be running. Expected result:

```text
Tests run: 3, Failures: 0, Errors: 0
BUILD SUCCESS
```

Docker, Ryuk, and temporary PostgreSQL log lines are normal. Focus on the Surefire result. If `missing table [transfers]` appears, Flyway did not run in the test context: confirm `spring-boot-flyway`, then rerun `./mvnw.cmd clean test -Dtest=WalletConcurrencyIntegrationTest`.

### Live burst probe

Run against Docker Compose:

```powershell
python scripts/run-burst.py http://localhost:8080
```

Or against Render:

```powershell
python scripts/run-burst.py https://wallet-p2p-transfer.onrender.com
```

The script performs 50 wallet requests, 30 same-key retries, contention transfers, and an explicit overdraft.

Successful output includes:

```text
wallet race (...): PASS
idempotency storm (...): PASS
conservation under contention (...) and clean overdraft decline: PASS
```

### Database validation

After a burst, use the database attached to that target:

```sql
SELECT status, COUNT(*) FROM transfers GROUP BY status;

SELECT SUM(balance_paise) AS total_paise,
       MIN(balance_paise) AS lowest_balance
FROM wallets;

SELECT idempotency_key, COUNT(*)
FROM transfers
GROUP BY idempotency_key
HAVING COUNT(*) > 1;
```

Validate a non-negative lowest balance, unchanged total across transfer-only work, and no duplicate idempotency keys. `DECLINED` rows are expected audit evidence.

## Observability

Every request receives an `X-Correlation-Id`. JSON logs include it plus structured method-entry fields, caller fingerprints, wallet IDs, transfer IDs, and paise amounts. Bearer tokens are never logged.

Domain events:

```text
transfer.created
transfer.debited
transfer.credited
transfer.declined
transfer.idempotent_replay
wallet.created_or_retrieved
```

Actuator exposes `/actuator/health`, `/actuator/prometheus`, and `/actuator/metrics`. Prometheus includes HTTP rate/latency/error data and:

```text
wallet_transfers_total
wallet_transfers_declined_insufficient_funds_total
wallet_transfers_idempotent_replays_total
```

## Render deployment guide

1. Push this repository to GitHub. Never commit passwords, database URLs, or `.env` files.
2. In Render, create PostgreSQL and a Docker Web Service from this repository.
3. Render builds `Dockerfile`; Compose is local-only.
4. Configure these Web Service environment variables:

   ```text
   DB_URL=<Render internal JDBC PostgreSQL URL>
   DB_USERNAME=<Render database user>
   DB_PASSWORD=<Render database password>
   WALLET_INITIAL_BALANCE_PAISE=100000
   ```

5. Keep PostgreSQL private. Use Render's internal database address from the app.
6. Deploy, verify Flyway, health, Swagger, burst, logs, and metrics.

For temporary database access from local system, permit only your current public IP as a `/32`, connect with SSL required, run validation SQL, then remove the rule immediately.

Note: Free Render services may sleep after inactivity, causing the first request to take ~1 minute. Free database plans may also expire, so collect required evidence before the displayed expiry date. To keep the service awake, I have created an AWS EventBridge Scheduler + Lambda that pings the service every 5 minutes.
