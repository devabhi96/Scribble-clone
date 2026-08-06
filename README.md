# Scribble Clone

A real-time multiplayer drawing and guessing game, similar to skribbl.io. Built with a Spring Boot + WebSocket (STOMP) backend and a React frontend, using plain HTML5 Canvas for drawing — no external drawing library.

**Live demo:** [scribble-clone-bay.vercel.app](https://scribble-clone-bay.vercel.app/)

I built this mainly to get real experience with WebSockets and concurrent state, since most of my prior backend work was standard CRUD/REST. The parts I cared about getting right were reconnection handling and making sure shared room state doesn't break when multiple players act at the same time.

## What it does

- Create a room, share the code, other players join
- Host can set number of rounds (or infinite rounds) and start the game
- Each turn, the drawer gets 3 word choices (auto-picked if they don't choose in time)
- Everyone else guesses in a chat box; correct guesses are detected server-side
- Letters in the word get revealed gradually as hints
- Live canvas sync — strokes drawn by the current drawer show up for everyone else in real time
- If someone disconnects mid-game, they have a 30 second grace period to reconnect before they're removed. If they come back in time, they get the full current drawing re-synced (not just their score/timer), so it doesn't look like the canvas reset for them
- If the host disconnects, host role gets reassigned automatically

## Why I built it this way

A few things I specifically tried to get right instead of doing the easy/obvious version:

- **Reconnection re-syncs the actual drawing.** A lot of clones just restore score and timer on reconnect. Here the backend keeps a stroke history per room and replays it to a reconnecting client, so the canvas looks correct again.
- **Room state is shared and mutable, so it needs locking.** Each `GameRoom` has its own `ReentrantLock`, and all state-changing operations (guesses, disconnects, turn changes, drawing) go through it. This was mainly to avoid race conditions when multiple players act on the same room at once, without locking every room in the app for every action.
- **Messages are typed, not raw JSON.** Every WebSocket message (join, guess, draw batch, word choice, etc.) is a Java record DTO. Made it a lot easier to keep frontend and backend in sync while building this out.

## Stack

**Backend:** Java, Spring Boot 3 (WebSocket, Web, Validation, JPA, Lombok), STOMP over native WebSocket, Maven. Game state lives in memory (`ConcurrentHashMap<String, GameRoom>`) — no DB needed for actual gameplay. Postgres is wired in for later (accounts/history, not used yet).

**Frontend:** React 19 + Vite, `@stomp/stompjs` for the WebSocket client, plain HTML5 Canvas for drawing.

**Deployment:** Frontend on Vercel, backend containerized with a Dockerfile (Render/Railway-style deploy).

## Project structure

```
backend/src/main/java/com/scribble/backend/
├── controller/   REST endpoints (create/find room)
├── websocket/    STOMP message handlers + connect/disconnect listeners
├── service/      game logic — turns, rounds, hints, scoring, timers
├── model/        GameRoom (the lockable, shared game state)
└── dto/          message types (records)

frontend/src/
├── App.jsx            room/game state + STOMP client
├── DrawingCanvas.jsx   canvas drawing + stroke streaming
└── AvatarPicker.jsx
```

## Running it locally

Backend:
```bash
cd backend
./mvnw spring-boot:run
```

Frontend:
```bash
cd frontend
npm install
npm run dev
```

## What's not done yet

- No accounts/persistence — everything is in-memory, resets on server restart
- No auth (JWT planned but not built)
- No networked undo / clear-canvas-for-everyone
- Word list is a fixed word bank, no custom packs yet' 
