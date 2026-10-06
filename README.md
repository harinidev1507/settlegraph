# SettleGraph

SettleGraph is a web app for splitting shared expenses within a group (a trip, a flat,
a dinner club) and then settling up with as few payments as possible.

## The problem it solves

When several people pay for things on each other's behalf, the raw record turns into a
tangle of who-owes-whom: A covered dinner, B paid for the cab, C bought tickets, each
split differently. Settling every pair separately means a lot of payments, many of
which cancel each other out. SettleGraph keeps an exact running balance for each member
and turns those balances into a short list of payments that clears everyone to zero.
For example, if A owes B 100 and B owes C 100, the plan is a single payment from A to C.

## What it does

- **Accounts:** register and log in with a username or email. Sessions use a JWT.
- **Groups by invitation:** the creator is the first member. Everyone else joins by
  accepting an invite, which they can also decline. Pending invites appear on the
  invitee's dashboard.
- **Expenses with four split types:** EQUAL, EXACT amounts, PERCENTAGE and SHARES.
  Every expense is split to the exact cent (details under
  [How money is split](#how-money-is-split)). Each expense gets a category: Food,
  Travel, Rent, Utilities or Other.
- **Balances:** each member's net position in the group, recalculated from the
  expenses and the settlements that have been paid.
- **Settle Up:** generates the simplified payment plan. Members mark payments as paid,
  and those payments then reduce the balances.
- **Recurring expenses (monthly):** a scheduled job adds the expense each month
  automatically. A recurring expense can be stopped at any time.
- **Spending analytics:** totals by category and by month for each group.
- **Notifications:** for new expenses, invites, and new settlement plans (worded
  for whether you pay or receive). Each one can be marked as read.
- **Activity log:** each group has an audit trail showing who did what.
- **Access control:** every group endpoint is limited to members of that group.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.3, Spring Web, Spring Data JPA, Spring Security (stateless JWT, HS512), Bean Validation |
| Database | PostgreSQL 16, with the schema managed by Flyway migrations (`backend/src/main/resources/db/migration`) |
| Frontend | React 19, Vite, React Router 7, Axios |
| Tests | JUnit 5 + Mockito for unit tests; `@SpringBootTest` + MockMvc against a real Postgres for integration tests |

## Running it locally

You need JDK 21, Maven, Node.js (with npm) and PostgreSQL. The repo has no Maven
wrapper (`mvnw`), so use your installed `mvn`.

### 1. Postgres

The app's defaults expect a role `postgres` with password `postgres` on
`localhost:5432`, owning a database called `settlegraph`. On a Homebrew install the
only superuser is your macOS user, so create the role first:

```bash
psql -d postgres -c "CREATE ROLE postgres WITH LOGIN SUPERUSER PASSWORD 'postgres';"
```

```bash
createdb -U postgres -h localhost settlegraph
```

You don't need to create any tables. Flyway creates the schema when the backend first
starts. To use different connection settings, see [Configuration](#configuration).

### 2. Backend (port 8080)

```bash
cd backend
mvn spring-boot:run
```

The backend is ready when the log prints `Started ArtifactsApplication`. A bare
`GET /` returns 403, which is expected because everything except `/api/auth/**` needs
a token.

### 3. Frontend (port 5173)

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`, sign up, create a group, invite a second account, add an
expense, and click **Simplify & Generate Settlement Plan**.

The backend only accepts browser requests from `http://localhost:5173` (CORS). If Vite
starts on a different port, set `FRONTEND_ORIGIN` to match.

### 4. The test database (needed for `mvn verify` only)

The integration tests run against their own database, so they never touch your dev
data. Create it once:

```bash
createdb -U postgres -h localhost settlegraph_test
```

Flyway migrates it on the first test run. See [Tests](#tests) for what runs where.

### Configuration

All settings are environment variables with local-dev defaults, defined in
`backend/src/main/resources/application.yml`:

| Variable | Default | Purpose |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | `localhost` / `5432` / `settlegraph` / `postgres` / `postgres` | Postgres connection |
| `JWT_SECRET` | a placeholder | Key used to sign login tokens. Changing it invalidates every existing token. |
| `FRONTEND_ORIGIN` | `http://localhost:5173` | The only browser origin allowed to call the API. Must match scheme, host and port exactly. Separate several origins with commas. |
| `PORT` | `8080` | API port |
| `VITE_API_BASE_URL` (frontend) | `http://localhost:8080/api` | Where the frontend sends API calls |

## Architecture

```
frontend (React)  ──HTTP + JWT──▶  backend (Spring Boot)  ──JPA──▶  PostgreSQL
                                   controller → service → repository
```

### Backend layers

The backend code is in `backend/src/main/java/com/settlegraph/Artifacts/`.

- **`controller/`** handles HTTP only. It reads the authenticated user from the JWT
  (`@AuthenticationPrincipal`), passes the request to a service and returns the result.
  Controllers contain no business logic.
- **`service/`** holds all the rules: split calculation, balances, settlement plans,
  invites, recurrence and analytics. Services own the transaction boundaries. Anything
  that writes related rows saves them in one transaction, so a failure never leaves
  part of a financial record behind. For example, an expense and its splits are saved
  together.
- **`repository/`** contains Spring Data JPA interfaces, one per table.
- **`security/`** holds `JwtService` and `JwtAuthFilter`. They make a missing, expired
  or forged token a **401**. It also holds `GroupAccessGuard`: every group-scoped
  service method calls `requireMember(groupId, userId)` before it reads or writes
  anything, and a non-member gets a **403**. A group that doesn't exist also returns
  403, so you can't probe for which group IDs exist.
- **`config/`** contains the security filter chain, CORS settings and a global
  exception handler. The handler maps errors to 400/403/404 responses with a JSON
  `{"error": "..."}` body.

### How money is split

Money is always `BigDecimal` in Java and `NUMERIC(12,2)` in Postgres.

- **EQUAL, PERCENTAGE and SHARES** use largest-remainder allocation. Each share is
  rounded down to the cent. The few leftover cents then go one at a time to the
  members with the largest remainders, with ties going to the lower user ID. The
  shares therefore always add up to the expense total exactly. For example, 100 split
  three ways is 33.34 / 33.33 / 33.33.
- **PERCENTAGE** must total exactly 100.
- **EXACT** amounts must add up to the total exactly and have at most two decimal
  places.

Every participant must be a member of the group. The frontend enforces the same rules
before it submits.

### Balances

A member's net balance is calculated as follows:

1. Add the full amount of every expense the member paid.
2. Subtract the member's share of every expense they're in.
3. Adjust for settlements marked **paid**: the payer's balance goes up by the amount
   and the receiver's goes down by the amount.

A **pending** settlement is only a plan and doesn't change any balance. Because every
expense's shares add up to its total exactly, the balances in a group always add up
to exactly zero. A unit test checks this across every split type.

### The debt-simplification algorithm

The algorithm is in `service/DebtSimplificationService.java`. It takes the net
balances and builds two max-heaps: **creditors** (positive balance) and **debtors**
(negative balance, stored as a positive amount). Then it repeats these steps:

1. Pop the largest creditor and the largest debtor.
2. Record a payment of `min(creditor, debtor)` from the debtor to the creditor.
3. Push back whichever side still has a balance left.
4. Stop when either heap is empty.

The algorithm is greedy. Each step clears at least one person, so a group with *n*
non-zero balances needs at most *n − 1* payments. That isn't always the true minimum,
because finding the minimum number of payments in general is NP-hard. In return the
algorithm is simple, fast (*O(n log n)*) and easy to verify.

**Generate** first deletes the group's existing pending plan and then saves the new
one, in a single transaction. Generating twice therefore never creates duplicate
plans. Paid settlements are kept as history. **Mark paid** is a conditional UPDATE
that only succeeds while the settlement is still pending. Of two simultaneous clicks,
exactly one succeeds and the other gets a 400.

### Schema

The schema is defined by four Flyway migrations (`V1` to `V4`):

| Table | Kind | Key | What it holds |
|---|---|---|---|
| `app_user` | strong entity | `id` | Account. `username` and `email` are each unique. |
| `app_group` | strong entity | `id` | A group, with `created_by` → `app_user` |
| `group_member` | **associative entity** | (`user_id`, `group_id`) | Resolves the many-to-many relationship between users and groups. Records `joined_at`. |
| `expense` | strong entity | `id` | `group_id`, `paid_by`, `amount`, `category`, date, and the recurrence fields (`recurring_source_id` → `expense`, `recurrence_period`) |
| `expense_split` | **weak / associative entity** | (`expense_id`, `user_id`) | One member's share of one expense. Its key includes the owning expense's ID, so it has no identity without that expense (weak). It also links expenses to users (associative). |
| `settlement` | strong entity | `id` | A planned or completed payment: `from_user_id` → `to_user_id`, `amount`, `PENDING`/`PAID` |
| `group_invite` | strong entity | `id` | `PENDING`/`ACCEPTED`/`DECLINED`. A partial unique index allows at most one pending invite per user per group. |
| `notification` | strong entity | `id` | A message for one user, with `is_read` |
| `audit_log` | strong entity | `id` | A group's activity trail, with `performed_by` |

In JPA, both composite keys are `@EmbeddedId` classes (`GroupMember.GroupMemberId`,
`ExpenseSplit.ExpenseSplitId`).

Two database constraints enforce rules that application code alone can't guarantee:

- The partial unique index on `group_invite(group_id, invited_user_id) WHERE status =
  'PENDING'` blocks duplicate pending invites.
- The unique index on `expense(recurring_source_id, recurrence_period)` means a
  recurring expense can be generated at most once per month. That holds even if the
  scheduler runs twice at the same time, and each recurring expense is processed in
  its own transaction.

## Tests

```bash
cd backend
mvn test     # 89 unit tests: no database, no Spring context
mvn verify   # the 89 unit tests + 63 integration tests against settlegraph_test
```

These counts come from the latest run: `mvn verify` gave **89 + 63 = 152 tests, 0
failures**.

**Unit tests (89):** plain JUnit + Mockito in `src/test/java`, with no Spring context
and no database.

| Area | Test classes |
|---|---|
| Debt simplification | `DebtSimplificationServiceTest` |
| Balances | `BalanceServiceTest` (paid settlements are netted out, pending ones ignored) and `GroupBalanceInvariantTest` (group balances sum to exactly zero for every split type; paying the generated plan leaves everyone at zero) |
| Splits | `ExpenseServiceTest` (22 tests, including a 612-case sweep: 17 amounts × 1–12 participants × the three allocated split types, checking that shares sum exactly to the total and each is within one cent of its exact proportional value) |
| Settlements | `SettlementServiceTest` (generating twice leaves one pending plan; double mark-paid is rejected) |
| Groups and invites | `GroupServiceTest` |
| Auth and security | `AuthServiceTest` (real BCrypt and JWT), `JwtServiceTest` (expired, malformed, wrong-secret, tampered and unsigned tokens), `JwtAuthFilterTest`, `GroupAccessGuardTest` |
| Other services | `RecurringExpenseServiceTest`, `AnalyticsServiceTest`, `NotificationServiceTest`, `AuditLogServiceTest`, `UserServiceTest` |

Authorization tests check more than the rejection itself. They also verify that the
data layer was never touched (`verify(repo, never())...`).

**Integration tests (63):** `*IT.java`, run by Maven Failsafe during `mvn verify`.
They use `@SpringBootTest` + MockMvc through the real security filter chain, against
the real `settlegraph_test` Postgres database.

- `SecurityIT` (27): every endpoint returns 401 without a valid token.
- `EndpointAuthorizationIT` (35): every group-scoped endpoint returns 403 for a
  non-member and 200 for a member.
- `MoneyFlowIT` (1): the full flow of create group, add expense, check the stored
  splits, generate a plan, mark it paid, and confirm every balance is exactly zero.

Each integration test truncates every table first. A guard refuses to run against
any database other than `settlegraph_test`.

**What the tests don't cover:**

- The frontend has no automated tests.
- No CI is set up.

Behaviour that depends on the database rather than a mock was checked by hand against
the real running database, and those checks are recorded in the "Verification log" in
[`SettleGraph_Technical_Notes.md`](SettleGraph_Technical_Notes.md). Examples are
concurrent mark-paid requests, recurring-expense isolation under a collision, CORS,
and the browser's 401/403 handling.

## Known limitations

These are real gaps in the current version, not planned features that quietly work.
The rationale and possible fixes are in
[`SettleGraph_Technical_Notes.md`](SettleGraph_Technical_Notes.md).

- **No deployment.** The app has only been run locally. `backend/Dockerfile` and
  `docker-compose.yml` exist but have never been built or run, because this project
  was developed without Docker. No deployed instance exists.
- **No editing or deleting expenses (or settlements).** To correct a mistake, you
  have to add a compensating expense.
- **Recurrence is monthly only.** There is no weekly, yearly or custom schedule.
- **The payer is always the logged-in user.** The API takes the payer from the token,
  and the UI has no "paid by" selector. You can't record an expense someone else paid.
  The UI's EQUAL split also always includes every member.
- **No leaving a group or removing a member.** Membership only grows.
- **Scheduler activity is credited to a user.** When the scheduler adds a recurring
  expense, the audit log records it as done by that expense's payer rather than by
  the system.
- **Email uniqueness is case-sensitive.** `Alice@x.com` and `alice@x.com` can register
  as two separate accounts. Usernames are lowercased, but emails are not.
- **A settled balance shows "is owed 0".** A member with a zero balance is labelled
  "is owed 0" rather than "settled up". This is a cosmetic issue.
- **Amounts are formatted inconsistently.** The same kind of value can appear as
  `9.2`, `90 INR`, `1,000.00` or `₹509.20` depending on the screen. The stored
  values are exact; only the display differs.
- **Notifications go stale.** They're never updated once sent. "You owe ₹X" stays
  unread after the payment is marked paid, and nobody is notified when a payment is
  marked paid or an invite is accepted or declined.

Deliberately out of scope for this version: multi-currency conversion (the UI records
every expense in INR, and nothing converts between currencies), receipts, a
trust/reliability score, and email verification.

## Repository layout

```
settlegraph/
├── backend/                       Spring Boot API
│   ├── src/main/java/.../Artifacts/   controller, service, repository, entity, dto, security, config
│   ├── src/main/resources/db/migration/   Flyway migrations V1–V4
│   └── src/test/java/             unit tests (*Test) and integration tests (*IT)
├── frontend/                      React + Vite app (src/pages, src/components, src/api)
├── CLAUDE.md                      engineering standards for changes to this repo
└── SettleGraph_Technical_Notes.md trade-offs, known gaps, verification log, change history
```
