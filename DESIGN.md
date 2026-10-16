# Design Document

## Architecture
```
Client                                              Server (EC2, Tomcat 9)
SwipeGenerator ─► Queue ─► SwipeSender × N ──POST──► SkierServlet
                                                    HealthServlet
LeaderboardSubscriber × 10 ────WebSocket──────────► LeaderboardEndpoint (push every 500 ms)
```

## Major Classes
- `SwipeGenerator`: one thread, creates all 500K swipes into a `LinkedBlockingQueue`
- `SwipeSender`: takes swipes from the queue and sends POSTs, retries up to 5 times with exponential backoff
- `LeaderboardSubscriber`: one WebSocket per resort, records push latency, reconnects on drop
- `Metrics`: shared `AtomicInteger` counters
- `RequestRecord`, `Stats` (Part 2): per-request records and statistics

## Threading Model
- One generator thread fills the queue before sending starts, so senders never wait
- Warmup: 32 threads × 1,000 swipes, timed separately with a `CountDownLatch`
- Main: remaining 468,000 swipes split across 200 threads
- All senders share one `HttpClient` (connection pool, keep-alive)
- Part 2: each sender stores records in its own list; merged and written to CSV after the run

## WebSocket Strategy
- 10 persistent connections (one per resort), opened before ingest and closed after
- On close or error, reconnect with backoff (up to 5 attempts)
- Part 2 uses a separate `HttpClient` for WebSockets

## Little's Law
Throughput = threads / RTT

| Run | RTT | Phase | Predicted | Measured |
|---|---|---|---|---|
| Part 1 | 24.66 ms | Warmup (32) | 1,298/s | 1,239/s |
| Part 1 | 24.66 ms | Main (200) | 8,110/s | 6,699/s |
| Part 2 | 26.37 ms | Warmup (32) | 1,213/s | 1,178/s |
| Part 2 | 26.37 ms | Main (200) | 7,584/s | 7,570/s |

The gap in Part 1's main phase comes from client-side overhead and network variation. Server CPU stayed low during the runs, so the server was not the bottleneck.

Push latency includes clock skew between client and server. My laptop clock was 2.47 s ahead in the first run; results were measured again after syncing.