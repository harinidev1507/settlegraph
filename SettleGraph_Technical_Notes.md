# SettleGraph — Technical Notes

Known limitations, deliberate trade-offs and follow-ups discovered while building.
Per `CLAUDE.md`, gaps found during a task are logged here rather than silently fixed
or silently ignored. Dates are absolute.

## Deliberate trade-offs (not bugs)

- **Split allocation is exact, by largest remainder.** EQUAL / PERCENTAGE / SHARES:
  each share is floored to 2dp, then the leftover cents (always 0 to n-1) go one at a
  time to the largest fractional remainders, ties by ascending user ID. Shares always
  sum to exactly the expense total, so balances always sum to zero. PERCENTAGE must
  total exactly 100. EXACT must match the total exactly with at most 2dp per value —
  no tolerance: `numeric(12,2)` silently rounds a sub-cent value on insert (33.335 x2 +
  33.33 was stored as 100.01 before this rule, verified 2026-10-06). The frontend
  enforces the same rules. Expenses saved before 2026-10-06 were not migrated; the
  drifting test rows were deleted instead.

- **Recurring expenses are MONTHLY only.** `RecurrenceFrequency` has one value. The
  scheduler anchors on the template's `expense_date`: an occurrence for month M is due
  from day-of-month `min(anchorDay, lengthOf(M))` (a 31st anchor fires on the 30th/28th
  in shorter months). The template itself *is* the first month's expense — it has
  splits and counts in balances — and the scheduler never generates for the
  template's own month.
- **Occurrences copy the template's splits verbatim.** If someone leaves or joins the
  group later, existing recurring templates keep their original participants; the
  user is expected to stop the template and create a new one.
- **Scheduler runs on startup and then every 24h (`fixedDelay`).** In a multi-instance
  deployment every instance runs it; that is safe (the unique index on
  `(recurring_source_id, recurrence_period)` plus per-template transactions guarantee
  at most one occurrence per period — verified against real Postgres on 2026-09-21
  with a genuine concurrent collision) but wasteful. A single instance is assumed.
- **The scheduler only catches `DataIntegrityViolationException`** per template. Any
  other `RuntimeException` on one template (e.g. an unexpected null) still aborts the
  remaining templates in that run — though never any template that already committed.
  Widen the catch to `RuntimeException` with an ERROR log if that ever bites.
- **No rate limiting, load balancing, caching, message queues.** Single-instance app;
  intentionally kept out (decision 2026-09-21).
- **`createGroup` still accepts `memberUserIds`** and adds those users as members
  without an invite/consent step. The frontend always sends `[]`; the invite → accept
  flow is the intended path. Left in place to avoid an API break; recommend removing
  the field so every membership goes through an accepted invite.
- **Entities are returned directly as JSON** (`Expense`, `Settlement`, `Group`,
  `AuditLog`, `Notification`). Fine for now; introduce response DTOs before adding
  fields that shouldn't leave the server.

## Known gaps / follow-ups

- `SecurityConfig` permits `/actuator/health`, but `spring-boot-starter-actuator` is
  not a dependency, so that path currently returns 403. Add the starter if the deploy
  platform needs an HTTP health check.
- No edit/delete for expenses or settlements. Corrections today mean adding a
  compensating expense.
- No frontend tests and no CI. The backend has 41 service-layer tests; the React side
  has none, and nothing runs `mvn test` / `npm run build` on push.
- Page-level `loadAll()` calls in `DashboardPage` / `GroupDetailPage` have no error
  handling, so a 403 or an expired JWT leaves sections blank instead of redirecting to
  login.
- `GET /api/groups/{id}/members` exposes member emails to other members. Acceptable
  for a small-group app; reconsider if groups become large/public.
- `JwtService` secret default in `application.yml` is a placeholder; production must
  set `JWT_SECRET`.

## Verification log (how the non-mockable pieces were checked)

- **2026-09-21 — per-template isolation in `RecurringExpenseService`.** Throwaway
  `@SpringBootTest` against the local Postgres: two `is_recurring` templates in group 8,
  the first with an occurrence row already present for the current period and its
  exists-check spied to return `false` (the concurrent-collision window). Result line:
  `healthyTemplateOccurrenceSurvived=1 brokenTemplateOccurrenceRows=1
  healthyOccurrenceSplits=2 healthyAuditRows=1 notificationsToOtherMember=1
  notificationsToPayer=0` (before the fix: `healthyTemplateOccurrenceSurvived=0`).
  Test file and all scratch rows removed afterwards.
- **2026-09-21 — CORS.** `curl` preflight from `http://localhost:5173` → 200 with
  `Access-Control-Allow-Origin: http://localhost:5173`; from `https://evil.example` →
  `403 Invalid CORS request`. Then in a real browser at `localhost:5173`: login, open
  group 6, add an EQUAL expense — every request 200, all preflights 200, zero console
  errors.
- **2026-10-06 — `markPaid` conditional UPDATE under concurrency.** Against the local
  Postgres via the live API: 20 concurrent `PATCH …/mark-paid` on one PENDING
  settlement → `{200: 1, 400: 19}`, exactly one "Marked settlement…" audit row, status
  PAID once. A sequential second call → 400. A settlement ID from another group and a
  nonexistent ID both → `404 Settlement not found`. The unit tests stub the UPDATE's
  row count; only this run exercises the real row lock. No automated DB test exists.
