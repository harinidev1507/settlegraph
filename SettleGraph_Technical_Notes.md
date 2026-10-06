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

- **No deployment.** Only ever run locally. `backend/Dockerfile` and
  `docker-compose.yml` exist but have never been built or run (no Docker on the dev
  machine), so they're unverified.
- **The payer is always the logged-in user.** `ExpenseController` passes the token's
  user ID as `paidByUserId`; the request has no payer field and the UI has no "paid by"
  selector. The UI's EQUAL split also always sends every member as `participantIds`
  (the API accepts a subset).
- **No leave-group or remove-member flow.** Membership only grows. `markPaid` relies on
  this (participants of a settlement are always members), as do recurring templates
  that copy their original participants.
- `SecurityConfig` permits `/actuator/health`, but `spring-boot-starter-actuator` is
  not a dependency, so that path currently returns 403. Add the starter if the deploy
  platform needs an HTTP health check.
- No edit/delete for expenses or settlements. Corrections today mean adding a
  compensating expense.
- **`npm audit`: one high-severity advisory left, in build tooling only.**
  `source-map-js` 1.2.1 (event-loop DoS via crafted source maps), pulled in by
  vite/postcss and jsdom — not shipped to users, so left as is (decision 2026-10-06).
  The other advisory found the same day, `axios` 1.19.0 (a production dependency;
  prototype-pollution, ReDoS and header-injection advisories), was fixed by upgrading
  to 1.20.0 on its own — see the verification log.
- No CI. The backend has 89 unit tests (`mvn test`) and 63 integration tests against
  a real Postgres (`mvn verify`); the frontend has 25 (`npm test`, added 2026-10-06:
  SHARES preview, split validation, the 401 interceptor, GroupDetailPage load errors).
  Nothing runs any of them, or `npm run build`, on push. Most frontend components
  (dashboard, notifications, analytics, invites) are still untested.
- **A deleted user's JWT is still accepted.** `JwtAuthFilter` trusts the token's
  claims and never checks that the user still exists, so a token for a deleted user
  gets 200s with empty data on reads, and a write fails on a foreign key with a 500
  ("Something went wrong"); nothing is written. Only reachable by deleting a user
  directly in the database — there is no delete-account feature. Found 2026-10-06
  (click-through, after the phase-6 cleanup removed a logged-in fixture user). Not fixed.
- **Settle Up on an already-settled group gives no feedback.** The request succeeds
  (200, an empty plan) but the Settle Up section doesn't change and shows no message;
  only the activity log says "Generated a settlement plan with 0 payment(s)". Recorded
  2026-10-06, not fixed.
- **Notifications go stale and some events send none.** "You owe ₹X to Y" stays unread
  after that settlement is marked paid; the debtor gets no notification that it was
  marked paid; the inviter isn't told when an invite is accepted or declined; invite
  notifications stay unread after the invite has been acted on. Notifications are
  only ever created, never updated or resolved. Recorded 2026-10-06, not fixed.
- **Expense notifications don't name the group or who added it** ("New expense
  "Electricity" for 90 was added"), which is ambiguous for anyone in more than one
  group. Recorded 2026-10-06, not fixed.
- **Amounts are formatted inconsistently across the UI.** Balances and the settlement
  plan show `9.2` / `509.2` (raw JSON numbers), the expense list `90 INR` next to
  `100.01 INR`, analytics `1,000.00`, notifications `₹509.20`. The values are right;
  only the display differs. Recorded 2026-10-06, not fixed.
- **Either side can mark a payment paid with one click.** The UI side of the
  creditor-only open question below: either party's click marks it paid, with no
  confirmation step.
- **Minor UI gaps (2026-10-06, not fixed):** no Log out on the group page (dashboard
  only); per-member EXACT inputs are labelled "Amount", same as the main amount
  field; "Stop repeating" has no confirmation and nothing shows the expense used to
  repeat; a stopped expense keeps `recurrence_frequency = 'MONTHLY'` with
  `is_recurring = false` (harmless today — everything checks `is_recurring` — but
  misleading data).
- **Zero balance reads "is owed 0".** `GroupDetailPage` labels `amt >= 0` as
  "is owed", so a fully settled member shows "is owed 0" instead of "settled up".
  Cosmetic, pre-existing; recorded 2026-10-06, not fixed.
- **Scheduler-generated audit rows credit a user for a system action.**
  `RecurringExpenseService` logs "Generated recurring expense …" with `performed_by` =
  the template's payer, so once the audit log shows actors (2026-10-06) it reads as if
  that person did it. Wrong for an audit log; recorded, not fixed. A fix needs the
  backend to mark system rows (e.g. nullable `performed_by` + a `system` flag) — not
  string-matching the action text in the frontend.
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

## Hardening pass, 2026-10-06 (six phases)

An audit of the whole app followed by six numbered phases, each committed and pushed
separately. Test counts are `mvn test` / `mvn verify` totals after the phase.

1. **Audit fixes.** `110adac`: split rounding — largest-remainder allocation, exact
   PERCENTAGE/EXACT totals, EXACT ≤ 2dp (balances could drift by a cent; EQUAL 100/7
   was rejected). `f19d9fd`: mark-paid is a conditional UPDATE (double mark-paid was a
   second 200 + audit row; concurrent requests both succeeded), membership checked
   before the settlement is read, cross-group ID → same 404 as missing. `18b42af`:
   `createGroup` rejects `memberUserIds` (users were added without consent; a bad ID
   was a 500). Open question logged: should only the creditor mark a payment paid?
2. **Prove the fixes hold.** `6543ea0`: 612-case allocation sweep and
   `GroupBalanceInvariantTest` (balances sum to zero for every split type; a mixed
   group paid off with the generated plan ends at exactly zero). 5 of 6 new tests fail
   against the pre-fix code. Logged: allocation can store 0.00 shares. 63 unit tests.
3. **Close test gaps.** `d566d43`: invalid/missing token is now 401 (was 403, same as
   "not a member"), with the axios interceptor clearing the session and redirecting to
   login; someone else's invite → 404 like a missing one. Added service tests (auth,
   users, audit, invites, JWT, auth filter) and the first integration tests
   (`SecurityIT`, `EndpointAuthorizationIT`, `MoneyFlowIT`) against a dedicated
   `settlegraph_test` database. Logged: case-sensitive email uniqueness. 89 / 152.
4. **Remaining planned features.** `74263ea`: analytics chart UI (by-month as
   time-ordered columns with empty months as zero; a single category shown as a
   sentence, not a 100% bar), actor names in the audit log. Decline invite, stop
   repeating and mark-read were already wired; browser-checked with DB checks. Logged:
   scheduler audit rows credited to a user.
5. **Frontend robustness.** `514c496`: page-level error handling on the group page,
   dashboard, notifications and analytics (a 403 shows "You don't have access to this
   group." instead of a stuck "Loading…"; no uncaught rejection on 401); errors shown
   for Settle Up, Mark paid, accept/decline and create group; settle buttons disabled
   while a request is in flight; the 4 lint warnings fixed (`useAuth` moved to its own
   module for fast refresh). Logged: "is owed 0" label.
6. **Cleanup and docs.** Removed all audit/fixture test data from the dev database and
   the stray root `package-lock.json`; README rewritten (setup, architecture, schema,
   test strategy, known limitations); these notes brought up to date. No code changes.
   89 / 152.

**After phase 6 — full click-through and SHARES preview.** A new-user click-through of
every flow (sign up ×2, groups, invite/accept/decline, all four split types,
recurring + stop, Settle Up, Mark paid, analytics, activity log, mark read) found no
broken flows and correct money throughout; the issues it did find are logged above.
One was fixed: SHARES inputs start at "1", and typing into them appended ("1" → "11")
with no preview, so a 1:2 split was saved as 11:21. Added a preview line ("Each pays:
…") computed by a BigInt mirror of `ExpenseService.allocate()`. Chosen over
clear-on-focus, which would turn "clicked in, clicked away" into an empty value — and
the form silently drops members with empty values from the split.

## Verification log (how the non-mockable pieces were checked)

- **axios 1.19.0 → 1.20.0 (2026-10-06).** Installed alone (`npm install axios@^1.20.0`,
  not `npm audit fix`); the lockfile diff touches only axios, and `npm audit` went
  from 2 high advisories to 1 (source-map-js). `npm test` 25/25, build and lint clean.
  In the browser: the Vite dev server had to be restarted first — its pre-bundled deps
  were still 1.19.0 — after which the bundle the app loads reported `VERSION` 1.20.0.
  Then, with a throwaway account: login `POST /auth/login` 200 → dashboard (3 calls,
  200, token attached by the request interceptor) → a group page (6 calls, 200;
  members, balance and expense rendered). Account and group deleted afterwards.

- **SHARES preview vs. stored rows (2026-10-06).** In the browser, the preview was
  compared with what the backend saved: 75 at 11:21 previewed `25.78 · 49.22`, the
  stored row from the click-through is 25.78 / 49.22; 10 at 1:2 previewed
  `3.33 · 6.67` and, submitted, stored 3.33 / 6.67. Also: 100.01 at 1:1 → `50.01 ·
  50.00` (tie to the lower user ID, as the backend does), 10 at 1.5:1 → `6.00 · 4.00`,
  an amount with three decimals → the "Enter an amount…" prompt instead of a guess.
  Now also covered automatically: the rule lives in `frontend/src/money/sharesPreview.js`
  and `sharesPreview.test.js` runs the backend's own `ExpenseServiceTest` cases against
  it. A change to `allocate()` must still be mirrored there — the tests catch a drift
  only for the cases they share, so port any new backend case too.
- **Frontend tests catch real regressions (2026-10-06).** Each check broke the code on
  purpose, ran `npm test`, and restored it (byte-identical / no git diff):
  `previewShares` giving leftover cents to the lowest user ID → 4 tests failed (e.g.
  10.00 at 1:2 got 3.34 / 6.66); ties by member order instead of user ID → both tie
  tests failed (100.01 at 1:1 gave the cent to user 39); the 401 interceptor without
  its `/login` guard → 1 failed; not clearing `settlegraph_user` → 2 failed; logging
  out on any 4xx → the 403 test failed. jsdom note: `window.location.assign` can't be
  spied on (non-configurable), so the tests stub `window.location` instead — no
  production code was changed for testability.

- **Dev-database cleanup (2026-10-06, phase 6).** Before deleting users 21–37 and
  groups 11–26, checked that no row linked them to anything outside those ranges
  (memberships, expenses, splits, settlements, invites, audit rows, notifications: 0
  each). One transaction, children first; delete counts matched the pre-counted
  totals exactly (45 splits, 16 expenses, 5 settlements, 20 invites, 84 audit rows,
  43 memberships, 15 groups, 70 notifications, 17 users). Afterwards: no user ≥ 21,
  no group ≥ 11, no orphaned splits. `mvn verify` on the same day: 89 + 63, 0 failures.

- **Page load errors (2026-10-06).** `DashboardPage` / `GroupDetailPage` /
  `NotificationsSection` / `AnalyticsSection` now catch their load promises. Checked in
  the browser on a fresh fixture (group 26): a 403 (non-member opening group 21) shows
  "You don't have access to this group."; a 401 (backend restarted with a different
  `JWT_SECRET`, so the stored token stops validating) redirects to /login with storage
  cleared and no `Uncaught (in promise)` in the console, on both the group page and the
  dashboard. Control run with the pre-change code under the same 401 logged
  `Uncaught (in promise) AxiosError … at loadAll`, so the check does detect the bug.

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
