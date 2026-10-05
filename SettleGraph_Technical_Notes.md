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
- **`createGroup` rejects a non-empty `memberUserIds` with 400** (since 2026-10-06).
  It used to add those users directly, without an invite or consent, and a nonexistent
  ID was a 500. Every membership other than the creator's now goes through an accepted
  invite. The field is kept rather than removed because Spring ignores unknown JSON
  fields — removing it would turn the 400 into a silent 200. Empty or omitted is fine.
- **Entities are returned directly as JSON** (`Expense`, `Settlement`, `Group`,
  `AuditLog`, `Notification`). Fine for now; introduce response DTOs before adding
  fields that shouldn't leave the server.

## Known gaps / follow-ups

- `SecurityConfig` permits `/actuator/health`, but `spring-boot-starter-actuator` is
  not a dependency, so that path currently returns 403. Add the starter if the deploy
  platform needs an HTTP health check.
- No edit/delete for expenses or settlements. Corrections today mean adding a
  compensating expense.
- No frontend tests and no CI. The backend has 89 unit tests (`mvn test`) and 63
  integration tests against a real Postgres (`mvn verify`); the React side
  has none, and nothing runs `mvn test` / `npm run build` on push.
- Page-level `loadAll()` calls in `DashboardPage` / `GroupDetailPage` have no error
  handling. Since 2026-10-06 an expired/invalid JWT is a 401 and the axios interceptor
  clears the session and redirects to login, but a 403 (e.g. opening a group you're not
  in) still leaves the page stuck on "Loading…" with blank sections.
- **Email uniqueness is case-sensitive.** `AuthService.register` checks
  `existsByEmail` on the raw string (usernames, by contrast, are lowercased), so
  `Alice@x.com` and `alice@x.com` can register as two accounts, and login by email is
  an exact match. Recorded 2026-10-06, not fixed and deliberately not locked in by a
  test; a fix would lowercase on register + login and add a unique index on
  `lower(email)` (existing duplicates would need checking first).
- **Allocation can store 0.00 shares.** When an amount has fewer cents than
  participants (EQUAL 0.01 among 12), most participants get a 0.00 `expense_split` row;
  `share_amount` has no CHECK constraint. Balances stay correct (sum exactly zero), but
  it records someone as a participant who owes nothing. Recorded rather than changed
  (decision 2026-10-06); options if it matters: reject amounts below 0.01 x participants,
  or skip 0.00 rows.
- Any group member can mark any settlement paid, not only its creditor (unchanged by
  the 2026-10-06 `markPaid` fix). Open question: should only the creditor confirm a
  payment was received?
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
- **2026-10-06 — 401 + frontend redirect, in a real browser.** Logged in through the UI
  (both `settlegraph_token` and `settlegraph_user` in localStorage), then restarted the
  backend with a different `JWT_SECRET` so the stored token was invalid. Reloading
  `/dashboard`: `GET /api/groups`, `/api/notifications`, `/api/invites/mine` → 401, the
  page landed on `/login`, and localStorage was empty (both keys `null`). Stayed on
  `/login` with no redirect loop; logging in again worked. Also: `SecurityIT` run
  against the previous `SecurityConfig` failed 25/27 with `expected:<401> but was:<403>`.
