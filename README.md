# Sketch Sync

A real-time multiplayer drawing and guessing game, similar to skribbl.io. Built with a Spring Boot + WebSocket (STOMP) backend and a React frontend, using plain HTML5 Canvas for drawing — no external drawing library.

**Live demo:** [scribble-clone-bay.vercel.app](https://scribble-clone-bay.vercel.app/)

I built this to get real experience with WebSockets, concurrent shared state, and securing a real-time app end to end — most of my prior backend work was standard CRUD/REST, where "two users touching the same data at the same time" isn't usually a live concern. Here it's the whole point: multiple players can guess, draw, disconnect, and reconnect within the same second, and the server has to stay correct through all of it.

## What it does

- Create a room, share the code, other players join
- Host can set the number of rounds (or infinite rounds) and start the game
- Each turn, the drawer gets 3 word choices (auto-picked if they don't choose in time)
- Everyone else guesses in a chat box; correct guesses are detected server-side
- Letters in the word get revealed gradually as hints
- Live canvas sync — strokes drawn by the current drawer show up for everyone else in real time
- If someone disconnects mid-game, they get a grace period to reconnect before they're removed. If they come back in time, they get the full current drawing re-synced (not just their score/timer), so it doesn't look like the canvas reset for them
- If the host disconnects, host role gets reassigned automatically
- Guest play (no signup needed) or a real account (username/password) if you want your session to persist

## Why I built it this way

A few things I specifically tried to get right instead of doing the easy/obvious version:

- **Reconnection re-syncs the actual drawing.** A lot of clones just restore score and timer on reconnect. Here the backend keeps a stroke history per room and replays it to a reconnecting client, so the canvas looks correct again — not just the scoreboard.
- **Room state is shared and mutable, so it needs locking.** Each `GameRoom` has its own `ReentrantLock`, and every state-changing operation (guesses, disconnects, turn changes, drawing) goes through it. This avoids race conditions when multiple players act on the same room at once, without locking every room in the app for every action — only the room actually being touched blocks.
- **Player identity is authorized server-side, not trusted from the client.** Every WebSocket action (guess, draw, start game, choose word) resolves *who's doing it* from the authenticated identity established during the JWT-verified connection handshake — not from any field the client sends in the message body. Early on I had this backwards (the server trusted a client-supplied player ID inside each message), which meant a malicious client could technically claim to be someone else. Catching and fixing that was one of the more valuable parts of building this — see [Challenges & fixes](#challenges--fixes) below.
- **Messages are typed, not raw JSON.** Every WebSocket message (join, guess, draw batch, word choice, etc.) is a Java record DTO. Made it a lot easier to keep frontend and backend in sync while building this out, and means malformed messages fail fast at deserialization instead of causing weird downstream bugs.

## Stack

**Backend:** Java, Spring Boot 3 (WebSocket, Security, Web, Validation, JPA, Lombok), STOMP over native WebSocket, Maven. Game state lives in memory (`ConcurrentHashMap<String, GameRoom>`) — no DB needed for actual gameplay. Postgres/JPA is wired in for accounts (guest + registered login); match history/persistence beyond that is on the roadmap, not built yet.

**Frontend:** React 19 + Vite, `@stomp/stompjs` for the WebSocket client, plain HTML5 Canvas for drawing.

**Security:** JWT-based auth (guest tokens + registered accounts with BCrypt-hashed passwords), Spring Security filter chain, and a custom in-memory sliding-window rate limiter — 5 login/register attempts per IP per minute, 5 guesses per player per 3 seconds — to blunt brute-forcing and spam without needing an external service like Redis for a project this size.

**Testing:** JUnit 5 + Mockito, 26 unit tests covering turn progression, scoring, host permission checks, duplicate-guess prevention, and rate-limit enforcement.

**Deployment:** Frontend on Vercel, backend containerized with a Dockerfile (Render-style deploy).

## Project structure

```
backend/src/main/java/com/scribble/backend/
├── controller/   REST endpoints (auth, room creation)
├── websocket/    STOMP message handlers + connect/disconnect listeners
├── service/      game logic — turns, rounds, hints, scoring, timers
├── model/        GameRoom (the lockable, shared game state)
├── dto/          message types (records)
└── security/     JWT filter/service, rate limiter

backend/src/test/java/com/scribble/backend/
├── model/        GameRoom unit tests
└── service/      GameService unit tests (mocked dependencies)

frontend/src/
├── App.jsx            room/game state + STOMP client
├── DrawingCanvas.jsx   canvas drawing + stroke streaming
├── auth.js             session/token handling
└── AvatarPicker.jsx
```

## Running it locally

**Prerequisites:** Java 17+, Node 18+, Maven (or use the included `./mvnw` wrapper — no separate Maven install needed).

**Backend:**
```bash
cd backend
./mvnw spring-boot:run
```
Runs on `http://localhost:8080` by default. Uses an in-memory H2 database out of the box, so there's nothing extra to set up to run it locally — accounts just won't persist across restarts unless you point `DB_URL` at a real Postgres instance (see [Environment variables](#environment-variables) below).

**Frontend:**
```bash
cd frontend
npm install
npm run dev
```
Runs on `http://localhost:5173` by default and expects the backend on `localhost:8080` unless overridden (see below).

**Then:** open two browser tabs (or one normal + one incognito, so they get separate sessions) pointed at `localhost:5173`, create a room in one, and join it from the other using the room code — that's the fastest way to see the real-time sync in action.

### Environment variables

None of these are required for local dev — every one has a sensible default baked in. You'd only set these for a real deployment:

| Variable | Used by | Default | Purpose |
|---|---|---|---|
| `PORT` | backend | `8080` | server port |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | backend | in-memory H2 | swap in a real Postgres instance |
| `JWT_SECRET` | backend | dev-only placeholder | **must** be overridden in any real deployment |
| `VITE_API_URL` | frontend | `http://localhost:8080` | backend REST base URL |
| `VITE_WS_URL` | frontend | `ws://localhost:8080/ws` | backend WebSocket URL |

## Challenges & fixes

A few things that came up while building this that I think are worth mentioning, since the fix mattered more than the bug:

**Player identity was spoofable over WebSocket.** Initially, every game action (guess, draw, start game, choose word) trusted a `playerId` field sent in the message payload itself, even though the JWT handshake already established who the client really was. In practice this meant a browser devtools user could submit a guess, or start a game, while claiming to be a different player. The fix: every WebSocket handler now pulls the player's identity from the authenticated `Principal` set during the STOMP handshake, and the client-supplied `playerId` field was removed from every message DTO entirely — there's no longer a field to lie in. I verified this by writing unit tests around the affected service methods and manually testing with two concurrent sessions.

**Turn order can be non-deterministic.** `GameRoom` tracks players in a `ConcurrentHashMap`, and turn order is built from that map's key order — which isn't guaranteed to match join order. I caught this because a unit test asserting "the host should draw first" failed with the *second* player listed as the drawer instead, which led me to the underlying data structure choice rather than assuming it was a test bug. Currently tracked as a known limitation (see below) rather than silently ignored.

**Getting Maven's test setup actually working.** The project's `pom.xml` originally referenced non-existent split test-starter artifacts (like `spring-boot-starter-webmvc-test`), which would have made `mvn test` fail to resolve dependencies entirely. Consolidating to the single real `spring-boot-starter-test` dependency, plus working through Mockito's strict-stubbing behavior (tests failing because a mock was stubbed but never called in that particular test path) while building out the test suite, was its own small lesson in how much test tooling has moving parts of its own.

## Known limitations

Being upfront about what's not finished, rather than hiding it:

- **Turn order isn't guaranteed to follow join order** (see above) — cosmetic, not a correctness or security issue, but on the fix list.
- **Game state is in-memory only.** A server restart clears every active room. Fine for a project at this scale; a real deployment serving live traffic would need this in a shared store (Redis) to survive restarts or scale horizontally.
- **No persisted match history yet.** Login/accounts work, but past scores, wins, and stats aren't saved anywhere — Postgres/JPA is wired in for this but not yet built out.
- **No CI pipeline yet** — tests are run manually rather than automatically on every push.

## Roadmap

- [ ] Fix turn-order determinism (swap to an ordered map or maintain a separate join-order list)
- [ ] Persist match history and basic player stats via the existing Postgres/JPA setup
- [ ] GitHub Actions CI running the test suite and frontend build on every push
- [ ] Split `App.jsx` into smaller components/hooks (currently one large file handling auth, game state, and rendering together)
- [ ] Docker Compose for one-command local setup (app + Postgres together)

## Testing

```bash
cd backend
./mvnw clean test
```
26 tests covering `GameRoom` (masked word/hint logic, host reassignment, turn-order list maintenance, stroke history) and `GameService` (start/join permission checks, word choice validation, guess scoring, duplicate-guess prevention, rate-limit enforcement) with Spring beans mocked out via Mockito so these run in milliseconds with no server or database needed.
