# SettleGraph

A group expense management and debt-settlement web app. Log shared expenses, split them
four ways (equal / exact / percentage / shares), and settle up with the minimum number
of payments via a greedy debt-simplification algorithm.

## What's implemented

- JWT authentication (register/login)
- Groups + membership
- Expenses with all 4 split types
- Real-time balance calculation per group
- Debt-simplification "Settle Up" (tested — see `DebtSimplificationServiceTest`)
- Settlement tracking (mark as paid)
- Notifications (dashboard, unread count + mark read) and per-group activity log
- Group spend analytics — `GET /api/groups/{groupId}/analytics/by-category` and
  `/by-month` (member-only, 403 otherwise). Both sum the expense amount once per
  expense, never the split shares. Shown on the group page as a "Spending breakdown"
  section (single-hue bar list, by category and by month).
- Recurring expenses (monthly). `POST /api/expenses` with `"recurring": true,
  "recurrenceFrequency": "MONTHLY"` creates this month's expense *and* marks it as a
  template; a scheduled job (on startup, then every 24h) copies it — same payer, amount
  and splits — once per following month, on/after the same day of the month. Each
  template runs in its own transaction, so one failing template can't take down the
  others, and a DB unique index on `(recurring_source_id, recurrence_period)` makes a
  double-run impossible to duplicate. `PATCH /api/expenses/{id}/stop-recurring` ends it.
  In the UI: "Repeats monthly" checkbox on Add Expense; templates show a MONTHLY badge
  with a "Stop repeating" button.
- Every group-scoped endpoint is member-only (403 otherwise) via `GroupAccessGuard`,
  and notifications can only be marked read by their recipient.
- CORS is locked to the frontend origin (`FRONTEND_ORIGIN`, default
  `http://localhost:5173`) — any other origin gets `403 Invalid CORS request`.

## What's intentionally left out of this version

- Multi-currency conversion, receipts, trust score, email verification — designed in
  the schema but not built here, to keep this version buildable and understandable
- Rate limiting, load balancing, caching layers — deliberately not added; this is a
  single-instance app and that infrastructure would be complexity without a payoff
- Polished UI styling (this uses simple, functional styling, not the full beige/brown
  mockup design — that can be layered on top later, screen by screen)

## Project structure

```
settlegraph/
├── backend/         Java 21 + Spring Boot 3 + PostgreSQL + Flyway + Spring Security (JWT)
├── frontend/        React 19 + Vite + React Router + Axios
└── docker-compose.yml
```

## Running it locally

### 1. Start Postgres + backend together with Docker

```bash
cd settlegraph
docker compose up --build
```

This starts Postgres (port 5432) and the Spring Boot API (port 8080). Flyway will
automatically create all tables on first startup.

**No Docker?** Run Postgres yourself (create a database named `settlegraph`, reachable
as `postgres`/`postgres` on localhost:5432 — or set `DB_*` env vars), then, with JDK 21
and Maven installed (there is no `mvnw` wrapper in this repo):
```bash
cd backend
mvn spring-boot:run
```

### 2. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

Visit the URL it prints (usually `http://localhost:5173`). Sign up, create a group,
add an expense, and try "Simplify & Generate Settlement Plan."

## Verifying the backend works on its own

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"priya","name":"Priya S","email":"priya@test.com","password":"test123"}'
```
You should get back a JSON response with a `token`. Log in with
`{"identifier":"priya@test.com","password":"test123"}` (username works too).

## Running the tests

```bash
cd backend
mvn test
```

57 service-layer tests (plain JUnit + Mockito, no Spring context, no database). They
cover the debt-simplification algorithm, balance netting, settlement idempotency,
split validation and exact cent allocation, mark-paid rejection of already-PAID
settlements, group creation only via invites, the recurring-expense scheduler
(including one template's failure not affecting another), and that every membership check rejects a non-member *before*
touching any data.

## Configuration

All settings come from environment variables with local-dev defaults
(`backend/src/main/resources/application.yml`):

| Variable | Default | Purpose |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | `localhost` / `5432` / `settlegraph` / `postgres` / `postgres` | Postgres connection |
| `JWT_SECRET` | a placeholder — **change it in production** | Signs login tokens (HS512) |
| `FRONTEND_ORIGIN` | `http://localhost:5173` | The only browser origin allowed to call the API (CORS). Must match scheme + host + port exactly; comma-separate for more than one. |
| `PORT` | `8080` | API port |

## Deploying for real (you'll need your own accounts for this part)

**Backend + database (Render, free tier available):**
1. Push this repo to GitHub.
2. On Render: New → PostgreSQL (note the connection details it gives you).
3. New → Web Service → connect your repo, root directory `backend`, environment: Docker.
4. Set environment variables: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
   (from step 2), `JWT_SECRET` (any long random string), and `FRONTEND_ORIGIN` (the
   exact URL Vercel/Netlify gives the frontend below, e.g. `https://settlegraph.vercel.app`
   — without it the browser will block every API call with a CORS error).
5. Deploy — Render builds the Dockerfile and gives you a public URL.

**Frontend (Vercel or Netlify, both free for this):**
1. Set the environment variable `VITE_API_BASE_URL` to your Render backend URL + `/api`.
2. Connect the repo, root directory `frontend`, build command `npm run build`,
   output directory `dist`.
3. Deploy — you'll get a public URL for the app itself.

## Where to go next (in priority order)

1. Get it running locally, end to end.
2. Read through `DebtSimplificationService.java` and its test — this is the piece worth
   understanding deepest and explaining in an interview.
3. Commit the work — everything since the initial commit is still an uncommitted
   working tree.
4. Layer in multi-currency and the polished visual design from the earlier mockups
   once the core loop feels solid.

Done since the original roadmap: all four split types in the Add Expense UI, fixed
category dropdown (Food/Travel/Rent/Utilities/Other), analytics section, notifications
and activity-log screens, recurring checkbox + stop button.

See `SettleGraph_Technical_Notes.md` for known limitations and follow-ups found
along the way.
