# K-Pulse

Voter sentiment recording and analysis for a constituency — field agents record
sentiment per voter against a candidate, admins approve access, curate the roll,
and read the rollup from booth to district.

Built from `requirement/files/voter-sentiment-app-requirements.md` and the
`k-pulse-ux-flow.jsx` mockup.

```
workspace/
├── frontend/   React + TypeScript + Vite — one app, three route groups
├── backend/    Spring Boot 3.3 + PostgreSQL — one API, role-split
└── mobile/     Android WebView shell pointed at /agent (see mobile/README.md)
```

## Run it

### Frontend on mock data (nothing else needed)

```bash
cd frontend && npm install && npm run dev
```

Open http://localhost:5173. `VITE_USE_MOCKS=true` (the default in `.env`) answers
every API call from `src/mocks/`, so the whole product is clickable with no
database and no backend.

Demo sign-ins:

| Who | How |
|---|---|
| Admin | `admin@k-pulse.in` / `admin123` at `/admin/login` |
| Field agent | `9861000011` (Prakash Sahoo) or `9861000012` (Anita Das), OTP `123456`, at `/agent/login` |

### Backend profiles

| Profile | Database | Seed data | OTP in response | JWT secret | For |
|---|---|---|---|---|---|
| `dev` (default) | in-memory H2 | yes | yes | built-in dev value | local development |
| `uat` | PostgreSQL `kpulse_uat` | yes, into an empty DB | yes | **required** | shared test environment |
| `prod` | PostgreSQL `kpulse` | **no** | **no** | **required** | direco.co.in/kpulse |
| `test` | in-memory H2 | yes | yes | test value | `mvn test` only |

```bash
cd backend && mvn spring-boot:run                                    # dev
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=uat     # uat
java -jar target/pluse-1.0.0.jar --spring.profiles.active=prod --server.port=8090
```

`dev` needs nothing installed. To run it against a local PostgreSQL, set
`KPULSE_DB_URL`, `KPULSE_DB_USER` and `KPULSE_DB_PASSWORD`.

**`uat` and `prod` contain no secrets and refuse to start without them.** Give
them the database password and JWT secret as environment variables, or in an
untracked file on the server that the profile imports automatically:

| Profile | Secrets file (override with `KPULSE_SECRETS_FILE`) |
|---|---|
| `uat` | `/home/ubuntu/app/kpulse-uat.properties` |
| `prod` | `/home/ubuntu/app/kpulse-prod.properties` |

Start from `backend/config/kpulse-secrets.properties.example`. When a value is
set in more than one place, the environment variable wins, then the secrets
file, then the profile file.

#### Admin login

The admin account comes from two properties, in every profile:

```properties
kpulse.admin.email=admin@k-pulse.in
kpulse.admin.password=Admin@123
```

On every start the account is created if it does not exist, and its password is
reset to this value if it differs, so changing the password here and restarting
changes the login - including against a database that already has users.
`dev` ships with the values above. For `uat` and `prod` put them in the secrets
file, never in git. Leave both blank to leave existing admins untouched. The
password must be at least 8 characters, and the email must not belong to a field
agent.

### Frontend against the real API

Set `VITE_USE_MOCKS=false` in `frontend/.env` and restart `npm run dev`. Vite
proxies `/api` to `http://localhost:8080`, so no CORS setup is needed in dev.

## Demo mode

The production bundle can serve the whole app from an in-browser mock API, so
it can be shown without the Spring Boot backend running.

| | |
|---|---|
| Turn on | `https://direco.co.in/kpulse/?demo=1` (or the "Open the demo" link on the landing page) |
| Turn off | `?demo=0`, or the **x** on the demo pill |
| Admin | `admin@k-pulse.in` / `admin123` |
| Agent | `9861000011` or `9861000012`, OTP `123456` |

Deliberate properties of this design:

- **Opt-in, never a fallback.** The app does not switch to mocks when the API is
  unreachable — a backend outage shows an error, not a screen of convincing
  fake numbers. `/kpulse/api/` returning 502 is supposed to look broken.
- **The choice lives in `sessionStorage`**, so it dies with the tab and cannot
  leak into a colleague's window via a shared link.
- **The mock dataset is a separate lazy chunk** (`assets/mockApi-*.js`, ~19 kB).
  Real users never download it; it is fetched only once demo mode is on.
- **A standing "Demo data" pill** is visible on every screen, so a screenshot
  taken in demo mode can never be mistaken for live data.

Those demo credentials are client-side fiction and grant nothing. The **real**
admin login is set in the properties file with `kpulse.admin.email` and
`kpulse.admin.password` - see Admin login under Backend profiles.

## Deploying to direco.co.in/kpulse

The app is mounted on a **sub-path** of the existing Direco host rather than a
domain of its own. Three things have to agree on the string `/kpulse`, and the
app breaks in a different way for each one you miss:

| Where | Setting | Breaks if wrong |
|---|---|---|
| `frontend/vite.config.ts` | `base: '/kpulse/'` | assets 404 — blank page |
| `frontend/src/app/routes.tsx` | `basename: '/kpulse'` | every route falls to the catch-all |
| `nginx` `direco.co.in.conf` | `location /kpulse/` | the distr SPA answers instead |

`frontend/.env.production` sets `VITE_API_BASE_URL=/kpulse`, so the SPA calls
`/kpulse/api/...` — same origin, no CORS. That path is proxied to the K-Pulse
Spring Boot instance on **port 8090** (8080 is distr, 8060 is pur, 9080 is
cricket).

```bash
# 1. Build. .env.production is picked up automatically by `vite build`.
cd frontend && npm run build

# 2. Ship dist/ to where the nginx container expects it.
rsync -av --delete dist/ ubuntu@direco.co.in:~/direcon/kpulse-dist/

# 3. Put /home/ubuntu/app/kpulse-prod.properties in place (see Backend
#    profiles), then start the jar with setup/direcon/app/run-app.sh:
#    port 8090, profile prod.

# 4. Reload nginx — always check the config first, a bad reload takes the
#    other two sites down with it.
docker exec n8n-nginx nginx -t && docker exec n8n-nginx nginx -s reload
```

The mock dataset is compiled out of a production build: `USE_MOCKS` folds to a
constant and `src/mocks` is behind a dynamic import, so no seed data or demo
credentials reach the bundle. `npm run build && grep -c Padmalochan dist/assets/*.js`
should print `0`.

## The three route groups

| Route | Who | What |
|---|---|---|
| `/` | anyone | Splash — pick Admin or Field agent |
| `/admin/*` | admin | Dashboard, access requests, voter lists, change requests, field agents, reports |
| `/agent/*` | field agent | Status, request access, voter list, sentiment entry, insights |

`/admin` and `/agent` share components, theme tokens and the API client but are
separate route trees. The mobile shell is a thin native WebView pointed straight
at `https://k-pulse.in/agent`, skipping the splash — so an agent-side UI change
ships with a web redeploy, not an app-store review.

Role separation is enforced server-side in
[`SecurityConfig`](backend/src/main/java/com/kahga/pluse/config/SecurityConfig.java)
(`/api/admin/**` → `ROLE_ADMIN`, `/api/agent/**` → `ROLE_FIELD_AGENT`), not just
hidden in the UI. Every agent read is additionally clamped to the booths their
live grants cover.

## How the core rules are implemented

| Requirement | Where |
|---|---|
| Access request = one unit + one candidate (FR-U2, FR-U7) | `AccessRequest` entity; `SentimentService.record` rejects a candidate that isn't the grant's |
| A higher-level grant covers everything under it (FR-U3) | `LocationService.boothIdsUnder` walks the unit tree once |
| Access expires after 6 months by default, admin can override or revoke | `AccessRequestService.approve/revoke`, `AccessExpiryScheduler` |
| Sentiment is editable forever, no time window (FR-U10) | One row per voter+candidate; re-recording updates it |
| Agents propose roll changes, admins apply them (FR-A8, FR-U11) | `VoterChangeRequestService` — it never writes `voter` itself, it calls `VoterService` on approval |
| Excel roll upload (FR-A6) | `VoterImportService`, header-driven so column order doesn't matter |
| PDF and Excel reports (FR-A2, REP-3) | `PdfReportGenerator` (OpenPDF), `ExcelReportGenerator` (Apache POI) |
| Booth → panchayat → block → district rollup (REP-2) | `ReportAggregationService` aggregates at booth level once, then sums |

## Notable design decision

The requirement sketches `District.java`, `Block.java`, `Panchayat.java` and
`Booth.java` as four entities. They are implemented instead as **one
self-referencing `Unit` table** with a `level` enum, because every consumer —
access requests, voter rolls, report rollups — targets "a unit at some level".
Four tables would have meant four near-identical code paths and a polymorphic
foreign key on `access_request` anyway. Levels and hierarchy are unchanged; only
the storage shape differs.

## Voter roll file format

`.csv`, `.xlsx` or `.xls`, header row, any column order. The **Template** button
on the admin screen downloads the exact shape:

```
house_no,Name,Relation,Relation_Name,Age,Gender,Assembly Part,Epic_no,Ward_No
87,Sabitri Nayak,W/O,Ranjit Nayak,39,F,40/117,ODA1234567,9
```

- Header matching ignores case, spaces and dots, so `Epic_no`, `epic no` and
  `EPIC No.` are the same column. Older spellings (`epic`, `voter_name`, `ward`,
  `booth_name`) still work, and `booth` is required when one upload spans
  several booths.
- **Relation and Relation_Name are stored as one line** ("W/O Ranjit Nayak"),
  which is how the roll prints it and how every screen shows it.
- **Assembly Part is accepted but not stored** — there is no field for it.
- Gender takes `M`/`F`, `Male`/`Female` or the Odia words ପୁରୁଷ and ମହିଳା.
- A row with no EPIC number gets a temporary one so it stays addressable;
  duplicates, inside the file or already in the database, are skipped and
  reported.
- CSV is read as **UTF-8**, so Odia names survive. The template download starts
  with a byte order mark, because Excel otherwise saves a UTF-8 CSV in a way
  that turns Odia into `?` characters.

## Tests

```bash
cd backend && mvn test          # 6 tests: access scoping, revocation, change-request approval
cd frontend && npm run lint     # tsc --noEmit across the app
```

The backend tests run on their own `test` profile (in-memory H2) against the seeded dataset, so they
cover the two rules most expensive to get wrong: which booths a grant unlocks,
and the fact that a proposal only moves the roll on approval.

## Still open

- **Agents cannot sign in to production yet.** `prod` hides OTPs, so agents have
  no way to receive a code, and it seeds nothing, so real units and candidates
  still have to be loaded. Before go-live it needs both, plus an SMS gateway for
  OTP delivery. The admin login comes from `kpulse.admin.*`.

- **Target constituency** is hard-coded as the S18-98 / Bhadrak sample in the
  seed data and the landing page. Point it at the real region by replacing the
  seed or uploading real rolls — no code change needed.
- **SMS gateway.** Agent OTP is generated and logged; `kpulse.otp.expose-in-response`
  also returns it in dev so the demo works. Wire a gateway in `AuthService.requestOtp`
  and set that flag to false.
- **Admin password reset** and audit-trail views were not in scope for v1.
- **The Android shell has not been compiled** — there is no Android SDK in this
  workspace. The source is complete; it needs one Android Studio sync and a
  launcher icon before it will build. See `mobile/README.md`.
