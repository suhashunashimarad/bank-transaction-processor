# Bank Transaction Processor

A small Spring Boot service that holds bank accounts and processes deposits, withdrawals and
transfers between them, keeping a timestamped ledger per account.

## Running it

Requires Java 21. The Maven wrapper is included.

```bash
./mvnw test                 # 60 tests: domain, service (incl. concurrency) and HTTP acceptance
JWT_ISSUER_URI=https://your-idp/realms/bank ./mvnw spring-boot:run   # starts on http://localhost:8080
```

## Authentication and authorisation

The service is an OAuth2 resource server. Every request needs an `Authorization: Bearer <JWT>`
header issued by the identity provider at `JWT_ISSUER_URI` (any OIDC provider - Keycloak,
Cognito, Entra ID). The token's `sub` claim identifies the customer.

| Rule | Result |
|---|---|
| No token, or an invalid/expired one | 401 |
| Opening an account | The caller becomes its owner |
| Balance, history, deposit, withdrawal on an account you don't own | 403 |
| Transfer *out of* an account you don't own | 403 |
| Transfer *into* someone else's account | Allowed - that is how payments work |

## API

| Method | Path                               | Body                                         | Success |
|--------|------------------------------------|----------------------------------------------|---------|
| POST   | `/accounts`                        | -                                            | 201     |
| GET    | `/accounts/{id}`                   | -                                            | 200     |
| GET    | `/accounts/{id}/transactions`      | -                                            | 200     |
| POST   | `/accounts/{id}/deposits`          | `{"amount": 100.00}`                         | 201     |
| POST   | `/accounts/{id}/withdrawals`       | `{"amount": 40.00}`                          | 201     |
| POST   | `/transfers`                       | `{"fromAccountId", "toAccountId", "amount"}` | 201     |

Errors are returned as [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details:

| Status | When                                                                    |
|--------|-------------------------------------------------------------------------|
| 400    | Amount is missing, zero, negative or has fractions of a penny; transfer to self; malformed body |
| 401    | Missing or invalid bearer token                                         |
| 403    | The caller does not own the account being read or debited               |
| 404    | An account in the request does not exist                                |
| 422    | The request is valid, but the account cannot afford it (overdraft)     |

Example:

```bash
A=$(curl -s -X POST localhost:8080/accounts | jq -r .accountId)
B=$(curl -s -X POST localhost:8080/accounts | jq -r .accountId)
curl -s -X POST localhost:8080/accounts/$A/deposits -H 'Content-Type: application/json' -d '{"amount": 100}'
curl -s -X POST localhost:8080/transfers -H 'Content-Type: application/json' \
     -d "{\"fromAccountId\":\"$A\",\"toAccountId\":\"$B\",\"amount\":30.25}"
curl -s localhost:8080/accounts/$A/transactions
```

## How the code is organised

```
domain/          Amount, Account, LedgerEntry - the rules live here, no Spring dependencies*
service/         BankingService - finds accounts, stamps time, coordinates transfers
infrastructure/  InMemoryAccountRepository
api/             Controllers, request/response records, error mapping
security/        JWT resource-server config, account ownership checks
```
\* apart from `@Nullable` used as documentation on `LedgerEntry`.

Reading order that tells the story: `AmountTest` -> `AccountTest` -> `BankingServiceTest` ->
`BankingApiAcceptanceTest`. Each test name is a sentence describing a rule.

## Decisions and trade-offs

**`Amount` value object instead of validating `BigDecimal`s everywhere.**
"Prevent invalid amounts" becomes a construction rule: once you hold an `Amount` it is positive
and has at most two decimal places. Sub-penny values are *rejected*, not rounded - silently
changing how much money moves is worse than refusing the request. The API layer only checks
the field is present; the domain owns what "valid" means, so there is one source of truth.

**`Account` owns both its balance and its ledger.**
They only change together, inside one `synchronized` method, so the balance can never disagree
with the ledger and `balanceAfter` on every entry is accurate. The debit check happens before any
mutation, so a rejected operation leaves no trace. `ledger()` returns an immutable copy.

**Transfers are atomic and deadlock-free.**
`BankingService.transfer` locks both accounts, always in account-id order, then debits (the only
leg that can fail) before crediting. Both ledger entries share one `transactionId` so the two
sides can be reconciled. Same-account transfers are rejected explicitly rather than becoming a
confusing no-op.

**Overdraft rule: the balance may reach exactly zero but never go below it.**
No overdraft facility was asked for, so none is modelled.

**Clock is injected.** Timestamps are deterministic in tests (`Clock.fixed`) and UTC in production.

**Server-generated UUIDs as account ids.** Guarantees uniqueness without coordinating with the
caller. Accounts open with a zero balance; money enters only via a deposit, so every penny of
the balance is explained by the ledger.

**In-memory storage behind an `AccountRepository` interface.** Persistence was out of scope for the
timebox. The interface is the seam where a database implementation would go.

**No mocks.** The in-memory repository is a real, fast implementation, so service tests exercise
real behaviour instead of verifying interactions.

**Authentication is delegated to an identity provider; the service only validates JWTs.**
No passwords or user store live here. The service is stateless (no session, CSRF disabled
because there are no cookies), so it scales horizontally without sticky sessions.

**Ownership is a domain fact, the check is a web concern.** `Account` records its owner and
answers `isOwnedBy`; `security/AccountAccess` applies it via `@PreAuthorize` on each endpoint.
For an unknown account the check passes so the service still returns 404 - account ids are
random UUIDs, so this does not help anyone enumerate accounts. Only the debited side of a
transfer is checked: paying into another customer's account is the point of a transfer.

## What I learned while iterating

- The first version of the concurrency helper used `try (ExecutorService ...)`. To check the
  deadlock test was genuinely protective, I temporarily changed `transfer` to lock `from` then
  `to` (naive order). The test *did* deadlock - but it hung the build instead of failing, because
  `ExecutorService.close()` waits for tasks blocked on a monitor forever. The fix: run the test
  body under `@Timeout(threadMode = SEPARATE_THREAD)`, use daemon worker threads and
  `shutdownNow()`. With the mutation re-applied the test now fails in 10 seconds; with the
  ordered locking restored it passes.
- I considered letting the controller convert the request into an `Amount` via Jackson, but that
  would leak serialisation annotations into the domain. Request/response records in `api/` keep
  the domain framework-free at the cost of a few small mapping methods.

## Limitations and what I would do next

| Limitation | Why it matters | Next step |
|---|---|---|
| In-memory only | Data is lost on restart | JPA/JDBC repository; balance updated in the same DB transaction as the ledger insert, with optimistic locking (version column) or `SELECT ... FOR UPDATE` |
| JVM-level locking | Only correct for a single instance | Move concurrency control to the database once persisted |
| No idempotency | A client retrying a timed-out POST could deposit/transfer twice | `Idempotency-Key` header stored with the transaction |
| Single currency (GBP implied) | Multi-currency needs FX rules | Add `Currency` to `Amount`, reject cross-currency operations |
| Unbounded history endpoint | Large ledgers return everything | Pagination and date-range filtering |
| Ownership only, no roles | Staff (support, audit) cannot view customer accounts | Map a scope/role claim to authorities and allow read-only access for it in `AccountAccess` |
| Single owner per account | No joint accounts | Replace `ownerId` with a set of holders |
| Balance stored alongside ledger | Two representations of the same truth | Could derive balance from the ledger (event-sourced) with snapshots |
