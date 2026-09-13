# Wallet & P2P Transfer Service — Technical Write-up

Supporting details: [README](README.md), [Flyway migration](src/main/resources/db/migration/V1__wallet_schema.sql), [transfer transaction service](src/main/java/com/paytm/wallet/service/impl/TransferServiceImpl.java), [live burst probe](scripts/run-burst.py), and [deployed API](https://wallet-p2p-transfer.onrender.com).

## Data model

PostgreSQL is the source of truth. `users.external_user_id`, `wallets.user_id`, and `transfers.idempotency_key` are unique. Wallet balances and transfer amounts are `BIGINT` integer paise; database constraints require non-negative balances, positive amounts, and distinct source/destination wallets. Transfers reference wallets by foreign key. `POST /wallets` uses `INSERT ... ON CONFLICT DO NOTHING` followed by a read, so concurrent creation for one user returns one wallet across replicas.

## Simplest correct mechanism

Each transfer is one Spring transaction. After validation and ownership checks, the service locks both wallet rows in PostgreSQL UUID order. This happens before inserting the transfer row because its foreign keys could otherwise acquire `KEY SHARE` locks in caller order and deadlock simultaneous A→B and B→A requests. All competing transfers therefore lock a pair in the same order.

The transaction claims the idempotency key using `INSERT ... ON CONFLICT DO NOTHING RETURNING`. A losing request reads the existing transfer: identical source, destination, and amount returns the original result; a changed body returns HTTP 409. A claimed transfer debits atomically with `UPDATE wallets SET balance_paise = balance_paise - :amount WHERE id = :id AND balance_paise >= :amount`. One updated row permits the destination credit and `COMPLETED` status. Zero rows records `DECLINED/INSUFFICIENT_FUNDS` and makes no credit. Unexpected failures roll back the claim and all balance changes together. This preserves conservation, prevents overdrafts, and prevents partial transfers.

Spring Data JPA handles mapped reads and ownership checks. Explicit JDBC is retained for idempotency, locks, and balance updates, keeping the money-moving behavior auditable.

## Alternatives and availability

Serializable isolation was rejected because retry-on-serialization-failure adds complexity without simplifying this workload. Application, Redis, and distributed locks were rejected because PostgreSQL is already the shared source of truth. Read-modify-write balances were rejected because they permit lost updates. The design favors consistency over availability: a database outage or lock problem may fail a transfer rather than risk an uncertain debit.

## Validation, operations, and cost

[`WalletConcurrencyIntegrationTest`](src/test/java/com/paytm/wallet/WalletConcurrencyIntegrationTest.java) uses Testcontainers PostgreSQL 16 for concurrent wallet creation, a 30-request idempotency storm, and contended cross-wallet transfers. [`scripts/run-burst.py`](scripts/run-burst.py) runs the live probe: 50 wallet requests, 30 identical retries, hundreds of cross-wallet transfers after the pre-submission count is set to 120, and a guaranteed overdraft. It asserts one wallet, one idempotent transfer, preserved total balance, non-negative balances, and clean declines.

The Docker image is multi-stage, non-root, and health-checked; Docker Compose starts the app and PostgreSQL locally. Render hosts the image and managed PostgreSQL privately. JSON logs include correlation IDs, structured method entries, and transfer lifecycle events. Actuator provides health and Prometheus HTTP/domain metrics. The deployment uses free tiers and costs ₹0, subject to Render sleep and expiry policies.

## AI disclosure

I architected, designed, and planned the solution, including the technology stack, structure, data model, transaction strategy, and deployment approach. AI assisted with implementation, tests, and documentation through multiple iterations guided by my coding style, folder structure, logging preferences, and design decisions.

I independently provisioned and configured the PostgreSQL database and application on Render, and reviewed and validated the final implementation.
