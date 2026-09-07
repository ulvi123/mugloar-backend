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
    0. The category `"Hmmm...."` couldn't be decoded and is scored 0.

- **Healing potions can increase lives above the starting value.** Initially the bot bought potions only when lives were low, but tests showed lives climbing past the starting 3 (up to 15+). The strategy now buys a potion whenever affordable to build an early life buffer.


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

## Configuration

- Preferred config file: `src/main/resources/application.yml`.
- Do not keep both `application.yml` and `application.yaml`; pick one to avoid confusion.
- Example property you may set:

```yaml
mugloar:
  api:
    base-url: "https://dragonsofmugloar.com/api/v2"
```

You can also provide these values via environment variables or Spring profiles for local/CI setups.

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
- A sequential game loop means a reactive client offers little benefit; `RestTemplate` keeps the implementation simpler.
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

## Testing & Debugging Process

I didn't just run this once and call it done. Below is the actual process I went through to get from "it technically works" to "it reliably works," because I think the process matters as much as the final numbers.

### 1. Unit tests (no network, fast, run on every change)

`strategy/`, `service/`, and `controller/` are covered by JUnit + Mockito, with `MugloarClient` mocked throughout so the suite never depends on the real API being up or on game randomness:

```bash
mvn clean test
```

These cover the probability-decoding logic (including the obfuscated cases described below), the ad-selection logic, the autoplay loop's exit conditions, and the controller's request handling.

### 2. Manual verification against the live API, endpoint by endpoint

Before trusting any of my own code, I hit the real API directly with curl to confirm the actual response shapes, since the field names and behavior aren't always exactly what the docs suggest:

```bash
curl -s -X POST https://dragonsofmugloar.com/api/v2/game/start | jq .
GAME_ID=$(curl -s -X POST https://dragonsofmugloar.com/api/v2/game/start | jq -r '.gameId')
curl -s https://dragonsofmugloar.com/api/v2/$GAME_ID/messages | jq .
curl -s -X POST https://dragonsofmugloar.com/api/v2/$GAME_ID/solve/{adId} | jq .
curl -s https://dragonsofmugloar.com/api/v2/$GAME_ID/shop | jq .
```

This is how I caught, early on, that the API's `probability` values don't always match the field names or category strings I originally assumed (see below).

### 3. Finding and fixing real bugs through repeated runs, not a single test

I ran the `/autoplay` endpoint dozens of times over several sessions, in batches of 3-5, and read the actual logs each time rather than just checking the final score. That process surfaced four distinct, real issues:

**Bug 1 — wrong probability string.** My initial mapping used `"Gambling"`; the real API returns `"Gamble"`. Every ad in that category was silently scoring 0 (same as `"Impossible"`), degrading the bot's decision quality without any visible error.

**Bug 2 — obfuscated probability values.** As a game progresses, the API sometimes returns `probability` as Base64 or ROT13-encoded text instead of plain English (e.g. `"UmF0aGVyIGRldHJpbWVudGFs"` decodes to `"Rather detrimental"`; `"Vzcbffvoyr"` ROT13-decodes to `"Impossible"`). `ProbabilityMapper` now tries plain text, then Base64, then ROT13, before giving up and logging a warning. One category, `"Hmmm...."`, doesn't decode as either and remains genuinely unidentified — it's deliberately scored at 0 (never preferred over a known option) rather than guessed at.

**Bug 3 — an uncapped healing item.** I assumed potions had some max-lives ceiling and only bought one reactively when lives got low. Logs showed lives climbing past 15 in some runs, which meant gold was being hoarded uselessly instead of converted into a survival buffer. Changed the strategy to buy proactively any time it's affordable.

**Bug 4 — unbounded retries on a permanently-broken ad.** The upstream API occasionally returns an ad it then refuses to solve, no matter how many times it's retried, with an ID that looks similarly obfuscated to the probability field (e.g. `d2hwOXpEVms=`). My first fix retried this indefinitely and burned through ~90 turns doing nothing. The corrected version tracks consecutive failures across both `getAds` and `solve` calls with a single shared counter, and gives up cleanly after 5, returning the game's last known good state instead of crashing or spinning.

Each of these was confirmed with a before-and-after log comparison, not assumed fixed just because the code compiled.

### 4. Reliability testing — running it enough times to trust the result

A single successful run doesn't prove much given how much variance this game has turn to turn, so I ran it repeatedly and logged the actual outcomes:

```bash
for i in 1 2 3 4 5; do
  GAME_ID=$(curl -s -X POST http://localhost:8080/api/game/start | jq -r '.gameId')
  echo "Run $i (game $GAME_ID):"
  curl -s --max-time 300 -w "\nHTTP_STATUS:%{http_code}\n" \
    -X POST "http://localhost:8080/api/game/$GAME_ID/autoplay?target=1000" \
    -o /tmp/result_$i.json
  cat /tmp/result_$i.json | jq '{score, lives, turn}'
done
```

Across multiple 5-run batches after the fixes above, the bot typically reaches 1000+ in 3-4 out of 5 runs, with occasional legitimate in-game losses (running out of lives through normal, unlucky play) rather than crashes. See the Results table further up in this README for concrete numbers.

### 5. Concurrency and load testing

While reviewing the `/autoplay` endpoint, I realized a slow, blocking request handler could exhaust the server's thread pool under concurrent load — worth checking rather than assuming it would be fine. I stress-tested it directly:

```bash
for i in $(seq 1 10); do
  GAME_ID=$(curl -s -X POST http://localhost:8080/api/game/start | jq -r '.gameId')
  curl -s --max-time 300 -X POST "http://localhost:8080/api/game/$GAME_ID/autoplay?target=1000" -o /tmp/concurrent_$i.json &
done
sleep 2
curl -s -w "\nresponded in %{time_total}s\n" -X POST http://localhost:8080/api/game/start
wait
```

This surfaced three real, sequential bottlenecks, each fixed and re-verified:

1. **Thread blocking.** `/autoplay` originally ran synchronously on the Tomcat request thread for the entire game (often 20-60+ seconds). Fixed by having the controller return `Callable<SolveResponse>`, which releases the request thread immediately and runs the loop on a separate, bounded executor. Verified: a `/game/start` call returned in under a second even with 10 games running concurrently in the background.

2. **Outbound connection exhaustion.** The default `RestTemplate` allows only a handful of concurrent connections per host, causing the 10 concurrent games' calls to the real Mugloar API to queue and eventually fail. Fixed with a pooled Apache HttpClient 5 connection manager (`setDefaultMaxPerRoute(30)`). This alone took concurrent success from 4/10 to 8/10 valid results.

3. **Async timeout too short.** Spring's default async request timeout (2 minutes) was shorter than some legitimately long games take under concurrent load. Raised to 5 minutes based on observed worst-case game duration. After this, 10/10 concurrent autoplay requests completed with real results.

I also found, from a real error captured mid-test (`"Failed to fetch shop for game ..."`), that the shop-fetching call inside the potion-purchasing logic had no failure handling at all, unlike the `getAds`/`solve` calls. Hardened it to skip the purchase and continue the game rather than aborting the whole run on a transient shop failure.

### 6. Frontend integration testing

Once the backend changes above were in, I re-verified the full stack together rather than assuming backend fixes are automatically transparent to the client:

- Manual play (start → solve → shop → autoplay) through the actual UI, confirming the dashboard numbers match the raw API response shown in the browser's network tab exactly.
- The error-banner path, by deliberately triggering the "ad can't be solved" failure and confirming it surfaces cleanly in the UI instead of breaking the app.
- Multiple browser tabs running independent games with autoplay triggered on all of them at once, confirming each tab's state stays correctly isolated to its own `gameId` and doesn't leak or desync — a lighter-weight, real-browser version of the concurrency test above.

### What I'd still improve with more time and also add persistence layer and AI agent to play with me as an opponent that can actuallu learn from my moves and adapt to my strategy

- Investigate what the `"Hmmm...."` probability category actually represents (possibly tied to a reputation or investigation mechanic not otherwise documented) rather than treating it as unknown.
- Move `MugloarClient` from a blocking `RestTemplate` to a fully non-blocking `WebClient`, so outbound calls never hold a thread even conceptually, rather than relying on connection pooling and a bounded executor as a mitigation.
- Blacklist a specific `adId` after a solve failure so the bot tries the next-best ad instead of retrying (and eventually abandoning) the same one.
