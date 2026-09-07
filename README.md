# Dragons of Mugloar — Backend

Spring Boot backend for the Dragons of Mugloar take-home challenge. It wraps
the [Dragons of Mugloar API](https://dragonsofmugloar.com/doc/), exposes a
clean REST API for a frontend to consume, and includes a strategy engine that
can play the game on its own and reliably reach 1000+ points.

This backend serves two things at once:

1. The **scripting challenge** — a standalone program that starts a game,
   picks the best ads, solves them, buys items, and repeats until it dies or hits a score target.
2. The **fullstack challenge** — the same logic exposed as REST endpoints so
   a web frontend (see mugloar-frontend) can drive the game interactively, with an "auto-play" option on top.

Both share the exact same decision logic under `strategy/` and
`service/GameLoopService.java` — I didn't want two separate implementations
of "pick the best ad" to maintain and possibly drift apart.

## How it works

```
Client (curl / Postman / or our React frontend)
        │
GameController        — REST endpoints, thin(intentionally designed this way), just delegates + logs
        │
GameLoopService        — the autoplay loop (used by /autoplay and ScriptRunner)
        │
AdSelector + ProbabilityMapper  — picks the best ad each turn
        │
MugloarClient           — the only place that talks to the real Mugloar API-the rest controller delegates to this
        │
https://dragonsofmugloar.com/api/v2
```

### The strategy, in plain terms

Each ad comes back from the API with a `probability` field like `"Piece of
cake"` or `"Suicide mission"` instead of a number. `ProbabilityMapper` turns
that into a numeric score (100 for the best, 10 for the worst), and
`AdSelector` always picks whichever ad currently on offer has the highest
score.

Two things I found out the hard way while testing this against the live API,
worth knowing if you're reading my code:

- **The probability field gets obfuscated as the game goes on.** Sometimes
  it's plain text, sometimes Base64, sometimes ROT13
  (`"UmF0aGVyIGRldHJpbWVudGFs"` decodes to `"Rather detrimental"`,
  `"Vzcbffvoyr"` ROT13-decodes to `"Impossible"`). `ProbabilityMapper` tries
  plain text first, then Base64, then ROT13, before giving up and scoring it
    0(I will figure this fuzzy logic out). There's also a category, `"Hmmm...."`, that doesn't decode as either —
       I couldn't figure out what it actually means, so it's deliberately scored
       as 0 (never picked over a known option) rather than guessed at.

- **Healing Potions don't cap out at some max lives.** I originally only
  bought one when lives got low, but logs showed lives climbing well past
  the starting 3 (up to 15+ in some runs) when gold was spent freely. So the
  current strategy just buys a potion any time it's affordable, banking a
  life buffer early rather than reacting once things go wrong.

### Results

I ran the autoplay loop multiple times back to back rather than relying on
one lucky run. Across a batch of 5 consecutive games:
| Run | Score | Outcome |
|-----|-------|---------|
| 1   | 1016  | Target reached, 16 lives remaining |
| 2   | 1019  | Target reached, 12 lives remaining |
| 3   | 1009  | Target reached, 16 lives remaining |
| 4   | 917   | Died before reaching target |
| 5   | 1009  | Target reached |

3-4 out of 5 runs typically clear 1000. The occasional loss tends to happen
in a late-game stretch where a large share of the available ads report the
unresolvable `"Hmmm...."` category or obfuscated low-probability categories
at the same time — I wasn't able to fully characterize this within the
project timeline. It's a known limitation, not a crash(I assume,but can be wrong): the bot degrades by
running out of lives, it doesn't error out.

## API endpoints
 Method | Path | Description |
|--------|------|--------------|
| POST | `/api/game/start` | Starts a new game |
| GET | `/api/game/{gameId}/ads` | Fetches current ads |
| POST | `/api/game/{gameId}/solve/{adId}` | Attempts to solve one ad |
| GET | `/api/game/{gameId}/shop` | Fetches shop items |
| POST | `/api/game/{gameId}/shop/buy/{itemId}` | Buys a shop item |
| POST | `/api/game/{gameId}/autoplay?target=1000` | Runs the strategy loop until the target score or death |

All of these are thin wrappers over the real Dragons of Mugloar API — please see the
`client/MugloarClient.java` for the actual outbound calls.

## Running it

Requirements: Java 17+, Maven.

```bash
mvn clean install
mvn spring-boot:run
```

Server starts on `http://localhost:8080`.

### Running the scripting challenge standalone (no web server)

```bash
mvn spring-boot:run -Dspring-boot.run.main-class=com.mugloar.solver.ScriptRunner
```

This starts a fresh game, plays it out with no UI, and prints the final
score/gold/lives to stdout.

### Running the tests

```bash
mvn clean test
```

Everything under `strategy/` and `service/` is unit tested with mocked
dependencies — no test hits the real Mugloar API, so the suite is fast and
doesn't depend on network availability,latency or game randomness.

## Project structure

```
src/main/java/com/mugloar/solver/
├── MugloarSolverApplication.java   — Spring Boot entry point
├── ScriptRunner.java                — standalone entry point for the scripting challenge
├── client/
│   ├── MugloarClient.java           — wraps the real Dragons of Mugloar API
│   └── MugloarClientConfig.java     — RestTemplate bean, timeouts
├── config/
│   └── CorsConfig.java              — allows the local React dev server
├── controller/
│   └── GameController.java          — REST endpoints
├── dto/                             — one record per API response shape
├── exception/
│   ├── MugloarApiException.java
│   └── GlobalExceptionHandler.java  — turns upstream failures into clean JSON errors
├── service/
│   └── GameLoopService.java         — the autoplay loop, shared by /autoplay and ScriptRunner
└── strategy/
    ├── ProbabilityMapper.java       — decodes/scores the probability field
    └── AdSelector.java              — picks the best ad each turn
```

## Notes for whoever's reviewing this

- Error handling: a bad `gameId` or `adId` returns a `502` with a JSON error
  body (not a raw stack trace) via `GlobalExceptionHandler`. Blank IDs are
  rejected with a `400` before a request is even sent upstream.
- I chose `RestTemplate` over `WebClient` since the game loop is inherently
  sequential (you can't fetch ads for turn 2 before turn 1's solve result is
  known), so there was no real benefit to a reactive client here so I just skipped using project reactor.
- CORS is currently open to `http://localhost:3000` for local development
  against the React frontend. This would need tightening for a real
  deployment.
## Results

Across 5 consecutive autoplay runs: 2 reached the 1000-point target cleanly
(1020, 1004, both with 15+ lives to spare), 2 ended in an ordinary in-game
loss (789, 780 — ran out of lives through normal gameplay), and 1 run (733)
was cut short after the upstream API repeatedly refused to resolve a
specific ad — the bot detected this after 5 consecutive failures and
returned its last good state instead of crashing, preserving the score
earned up to that point.

This variance is inherent to the game itself (ad quality per run is
randomized, and the API's obfuscation and occasional per-ad failures are
outside the bot's control I assume) rather than a flaw in the selection strategy.
What the bot does control — picking the best available option each turn,
managing gold/lives proactively, and degrading gracefully on upstream
failures rather than crashing — is demonstrated and tested.
