# spring-vue-production-starter

A Spring Boot 3 + Vue 3 + Capacitor starter with the boring parts done right:
accounts, sessions, deletion, limits, tests, backups. Runnable as is.

Not a toy scaffold: everything here shipped first in
[MeDeben](https://github.com/iulian640/medeben), a time-tracking app for
hospitality workers in Spain, and was reviewed and hardened there before being
extracted.

## What's inside, and why it's shaped this way

**Auth with revocable sessions.** Short-lived access JWT (15 min, stateless)
plus an opaque 256-bit refresh token stored as a SHA-256 hash. Refresh tokens
rotate on every use with atomic claiming; replaying a spent token reveals a
theft and revokes every session for that user. The revocation survives the 401
on purpose (`noRollbackFor`): without it, the exception rolls back the very
revocation it should protect. That bug shipped past unit tests and was caught
by exercising the real stack, so there's an integration test with real
transactions pinning it.

**Real logout and GDPR account deletion.** Logout revokes server-side.
`DELETE /api/v1/cuenta` re-confirms the password and physically deletes the
user; `ON DELETE CASCADE` sweeps the child tables. Wrong password returns 403,
not 401, because the client treats 401 as an expired session and would kick
the user out for a typo. Verified against a real PostgreSQL (Testcontainers),
including that the email becomes reusable.

**A frontend API client that survives shared devices.** Single-flight refresh
(concurrent 401s share one renewal), a single retry with the rotated token,
and a guard so a stale request from a previous session can never expel whoever
is signed in now. Tokens live in memory only; there is nothing for an XSS to
read from localStorage.

**Rate limiting with separate budgets.** Strict per-IP bucket for login and
password re-confirmation, a wider one for refresh traffic (active users renew
every 15 minutes; putting them in the login bucket lets a shared WiFi lock
itself out), and a general one keyed by IP+subject.

**Errors as RFC 7807.** One `ProblemDetail` shape everywhere, including the
401s the security filter emits. Internal errors log the detail and return a
neutral message.

**CI that runs the whole thing.** Three jobs on every push: backend
(`mvn verify` with a JaCoCo gate, Testcontainers against PostgreSQL 16),
frontend (ESLint, Vitest with coverage gates on all four metrics, type-checked
build), and end-to-end Playwright journeys against the actual running stack: a
service PostgreSQL, the compiled backend, and the Vite dev server.

**Backups you have rehearsed.** `deploy/backup-db.ps1` dumps the database,
validates the archive with `pg_restore --list` before keeping it, and applies
retention. `deploy/restaura-db.ps1` drills the restore into a throwaway
container and counts the rows. A backup you have never restored is a guess.

**Deploy.** Multi-stage Dockerfile, a compose file with PostgreSQL, the
backend and an nginx that ships security headers (CSP, HSTS, nosniff,
frame-deny) and binds to 127.0.0.1 so nobody exposes it without TLS by
accident. The frontend is a PWA and builds to Android with Capacitor.

## Quickstart

```bash
# Database (PostgreSQL 16 on :5432)
docker compose up -d

# Backend (:8080, dev profile, Flyway migrates on boot)
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend (:5173, proxies /api to the backend)
cd frontend && npm ci && npm run dev
```

Tests:

```bash
cd backend && mvn verify          # unit + integration (needs Docker) + coverage gate
cd frontend && npm test           # Vitest
cd frontend && npm run test:e2e   # Playwright against the running stack
```

## Conventions you'll notice

- Comments, identifiers and UI copy are in Spanish. The template comes from a
  Spanish product and I kept its voice; renaming is a find-and-replace away.
- Time comes from an injected `Clock`, never `Instant.now()` scattered around.
  Tests fix it.
- Entities that must not be re-selected on save implement `Persistable`.
- State transitions that matter for security (spending a refresh token) happen
  in atomic UPDATE queries, not read-modify-write.
- Every non-obvious decision has a comment saying why, usually with the bug
  that motivated it.

## What this is not

There is no domain in here: no products, no todos, no sample CRUD. The point
is the part most starters skip. Bring your own domain; the accounts, sessions,
limits and pipelines are already wired and tested.

## License

[MIT](LICENSE). The app it was extracted from is AGPL-3.0; this template is
relicensed by its author for unrestricted use.
