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
- Notifications and audit log (backend endpoints ready; not yet wired into the UI)

## What's intentionally left out of this version

- Recurring expenses, multi-currency conversion, receipts, trust score — all designed in
  the schema but not built here, to keep this version buildable and understandable
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

**No Docker?** Run Postgres yourself (create a database named `settlegraph`), then:
```bash
cd backend
./mvnw spring-boot:run
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
  -d '{"firstName":"Priya","lastName":"S","email":"priya@test.com","password":"test123"}'
```
You should get back a JSON response with a `token`.

## Running the algorithm's tests

```bash
cd backend
./mvnw test
```

## A note on what I could and couldn't verify here

This backend was written carefully following standard, current Spring Boot conventions,
but I was not able to actually compile or run it in the environment I built it in
(no access to Maven Central to download dependencies there). **You should run
`./mvnw spring-boot:run` yourself as the first real test** — if anything doesn't compile,
paste me the exact error and we'll fix it together; that's a completely normal part of
getting a generated project running for the first time.

The **frontend was verified** — `npm run build` completed with zero errors.

## Deploying for real (you'll need your own accounts for this part)

**Backend + database (Render, free tier available):**
1. Push this repo to GitHub.
2. On Render: New → PostgreSQL (note the connection details it gives you).
3. New → Web Service → connect your repo, root directory `backend`, environment: Docker.
4. Set environment variables: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
   (from step 2), and `JWT_SECRET` (any long random string).
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
3. Add the other split types to the "Add Expense" screen UI (backend already supports them).
4. Wire up Notifications and Audit Log screens (backend endpoints already exist).
5. Layer in recurring expenses, multi-currency, and the polished visual design from the
   earlier mockups once the core loop feels solid.
