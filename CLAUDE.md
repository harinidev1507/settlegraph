SettleGraph — Engineering Standards
Read this before implementing any feature or fix. These rules exist because each one caught a real issue during development — they're not theoretical.
Core principles

* Correctness and understanding over speed. Don't optimize for "it compiles" or "the demo works" — optimize for "this is actually right and I can explain why."
* Don't blindly agree with a suggested approach. If something is fragile, unnecessary, or likely to cause problems later, say so clearly and explain why, even if it means more work.
* When fixing a bug, find the root cause. Don't patch the symptom you happened to notice — explain why it happened, and check whether the same root cause affects anything else.
* Confirm the design/rule in plain English before writing code, especially for anything involving money, notifications, or scheduling. State the rule, get it confirmed, then implement — not the other way around.
* Explicit is better than implicit. If a decision is a known limitation or deliberate trade-off rather than a bug, say so directly instead of silently working around it.

Verification rules (non-negotiable)

* Never claim something works based only on "it compiled" or "the test count went up." Show real evidence:
   * For a test: paste the actual test file content, not a summary of what it does.
   * For a bug fix: show the real database rows, the real API response, or real browser/DevTools output — not a description of what should have happened.
* Mocked unit tests cannot verify real database behavior — especially around transactions, concurrency, and constraints. If a feature involves `@Transactional` boundaries, concurrent access, or a database-level constraint (unique index, foreign key), it needs a check against the real running database, not just mocks. (Real example from this project: a mocked test suite passed 23/23 while a transaction-rollback bug in real Postgres behavior went uncaught.)
* A "weak" test checks that something happened. A "strong" test checks that the right thing happened, and that a specific wrong thing didn't. Prefer strong tests. Example: don't just check "an exception was thrown" — also `verify(x, never())` that a dangerous fallback path was never reached.
* `BigDecimal` values must be compared with `.compareTo()`, never `.equals()` — `.equals()` treats `50` and `50.00` as different values due to scale.

Security & authorization

* Every new group-scoped endpoint must call `GroupAccessGuard.requireMember(...)` (or equivalent) before touching any data. This project has already had two real IDOR-class bugs from endpoints that checked authentication but not authorization — treat this as a standing requirement, not something to remember case by case.
* Authorization checks must run before any state is read or changed, and should be provably true (verify no interaction with the data layer on rejection), not just "an error eventually happened."

Financial/data-integrity rules

* Money fields use `BigDecimal`, never `double` or `float`.
* Any operation that creates related records (e.g. an expense + its splits) must happen in a single transaction — never allow a partially-saved financial record.
* Split validation must confirm split amounts actually sum to the total (within a small tolerance for rounding), for every split type, not just the default one.
* Before implementing balance or settlement logic, state the expected invariant (e.g. "balances always sum to zero across a group") and write a test that checks it directly.

Testing conventions

* Service-layer tests use plain JUnit + Mockito, no Spring context, no real database (see `BalanceServiceTest`, `SettlementServiceTest` as reference examples).
* Controller and security tests use `@SpringBootTest` + MockMvc, because the security filter chain only exists inside Spring's request pipeline — mocking around it would test nothing. That is a reason to use Spring for this layer, not a reason to skip testing it.
* Keep the Spring-context tests separate from the service tests so the fast suite stays fast: they live in `*IT.java` classes, run by `mvn verify`, while `mvn test` runs only the plain unit tests.
* Frontend tests use Vitest + React Testing Library in jsdom, with no real backend: API modules (`src/api/*`) are mocked at the import boundary with `vi.mock`. They live next to the code as `*.test.js(x)` and run with `npm test` in `frontend/`, separate from both Maven suites.
* Frontend tests assert what the user sees and what gets sent — rendered text, and the exact API call (or `not.toHaveBeenCalled()` when input must be rejected) — never component state or internals.
* Money logic in the frontend lives in plain functions in their own module (e.g. `src/money/sharesPreview.js`) and is unit-tested directly. Anything that mirrors a backend rule must be tested against the backend's own test cases, labelled as such, so the two can't drift silently.
* jsdom tests cannot prove CORS, real page navigation, layout, or behavior against the real API. Changes in those areas still need a check in a real browser against the running backend, per the verification rules above.
* Name tests to describe the specific behavior being locked in (e.g. `generatingTwiceWithUnchangedBalances_leavesExactlyOnePendingSettlement`), not generic names like `testGenerate1`.
* When a real bug is found (through manual testing or otherwise), write a regression test for it as part of the fix — the fix isn't considered complete until a test exists that would fail if the bug came back.

Scope discipline

* Don't add features, endpoints, or abstractions that weren't asked for, even if they seem like natural extensions.
* If something is explicitly out of scope for now (see current deferred list below), don't build it without checking first.
* If a task surfaces a related gap or improvement opportunity, log it in `SettleGraph_Technical_Notes.md` or the README roadmap — don't silently fix it, and don't silently ignore it either.

Currently deferred, do not build without explicit confirmation: multi-currency conversion, receipts, trust/reliability score, email verification, any admin-role/HTTP-triggered maintenance endpoints.
Communication format
For any nontrivial change, structure the response as:

1. What we're doing and why
2. The design decision, stated plainly, before code exists — flag anything genuinely ambiguous for a decision rather than assuming
3. The actual implementation
4. Real verification evidence (real test code, real DB state, real API responses)
5. Anything discovered along the way that's worth a follow-up note

Don't mark something "done" until step 4 has actually happened.
