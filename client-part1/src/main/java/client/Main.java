package client;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

public class Main {

    public static void main(String[] args) throws Exception {
        String baseUrl = args.length > 0 ? args[0] : "http://localhost:8080/ski-servlet";
        int mainThreads = args.length > 1 ? Integer.parseInt(args[1]) : Config.DEFAULT_MAIN_THREADS;
        String wsBaseUrl = baseUrl.replaceFirst("^http", "ws");

        // One shared client = one shared connection pool with keep-alive
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(Config.REQUEST_TIMEOUT_SEC))
                .build();
        Metrics metrics = new Metrics();

        System.out.println("Server: " + baseUrl + ", main phase threads: " + mainThreads);

        // 1. Little's Law baseline: single-threaded round-trip time (not counted in the 500K)
        double rttMs = measureSingleThreadRtt(client, baseUrl, Config.LITTLES_LAW_SAMPLES);
        System.out.printf("Single-thread avg RTT: %.2f ms%n", rttMs);
        System.out.printf("Little's Law prediction: warmup (%d threads) = %.0f req/s, main (%d threads) = %.0f req/s%n",
                Config.WARMUP_THREADS, Config.WARMUP_THREADS / (rttMs / 1000.0),
                mainThreads, mainThreads / (rttMs / 1000.0));

        // 2. WebSocket subscribers, one per resort
        List<LeaderboardSubscriber> subscribers = new ArrayList<>();
        for (int resort = 1; resort <= Config.NUM_RESORTS; resort++) {
            LeaderboardSubscriber sub = new LeaderboardSubscriber(client, wsBaseUrl, resort);
            sub.connect();
            subscribers.add(sub);
        }

        // 3. Single generator thread fills the queue
        BlockingQueue<SwipeEvent> queue = new LinkedBlockingQueue<>();
        new Thread(new SwipeGenerator(queue, Config.TOTAL_SWIPES)).start();

        // 4. Warmup phase
        long startTime = System.currentTimeMillis();
        runPhase(Config.WARMUP_THREADS, Config.WARMUP_THREADS * Config.WARMUP_SWIPES_PER_THREAD,
                client, baseUrl, queue, metrics);
        long warmupEnd = System.currentTimeMillis();

        // 5. Main phase
        int remaining = Config.TOTAL_SWIPES - Config.WARMUP_THREADS * Config.WARMUP_SWIPES_PER_THREAD;
        runPhase(mainThreads, remaining, client, baseUrl, queue, metrics);
        long endTime = System.currentTimeMillis();

        for (LeaderboardSubscriber sub : subscribers) {
            sub.close();
        }

        printResults(metrics, subscribers, startTime, warmupEnd, endTime, mainThreads, remaining);
    }

    private static void runPhase(int numThreads, int totalSwipes, HttpClient client, String baseUrl,
                                 BlockingQueue<SwipeEvent> queue, Metrics metrics) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(numThreads);
        int base = totalSwipes / numThreads;
        int extra = totalSwipes % numThreads;
        for (int i = 0; i < numThreads; i++) {
            int count = base + (i < extra ? 1 : 0);
            new Thread(new SwipeSender(client, baseUrl, queue, count, latch, metrics)).start();
        }
        latch.await();
    }

    private static double measureSingleThreadRtt(HttpClient client, String baseUrl, int samples) throws Exception {
        long totalNanos = 0;
        for (int i = 0; i < samples; i++) {
            HttpRequest request = SwipeSender.buildRequest(baseUrl, SwipeGenerator.randomSwipe());
            long start = System.nanoTime();
            client.send(request, HttpResponse.BodyHandlers.discarding());
            totalNanos += System.nanoTime() - start;
        }
        return totalNanos / (double) samples / 1_000_000.0;
    }

    private static void printResults(Metrics metrics, List<LeaderboardSubscriber> subscribers,
                                     long startTime, long warmupEnd, long endTime,
                                     int mainThreads, int mainSwipes) {
        int warmupSwipes = Config.WARMUP_THREADS * Config.WARMUP_SWIPES_PER_THREAD;
        long warmupMs = warmupEnd - startTime;
        long mainMs = endTime - warmupEnd;
        long totalMs = endTime - startTime;

        System.out.println("\n===== Ingest Path =====");
        System.out.println("Successful swipes:   " + metrics.successful.get());
        System.out.println("Failed swipes:       " + metrics.failed.get());
        System.out.println("Retries:             " + metrics.retries.get());
        System.out.println("Connection errors:   " + metrics.connectionErrors.get());
        System.out.printf("Warmup:  %d threads, %d ms, %.0f swipes/s%n",
                Config.WARMUP_THREADS, warmupMs, warmupSwipes * 1000.0 / warmupMs);
        System.out.printf("Main:    %d threads, %d ms, %.0f swipes/s%n",
                mainThreads, mainMs, mainSwipes * 1000.0 / mainMs);
        System.out.printf("Total wall time: %d ms%n", totalMs);
        System.out.printf("Overall throughput: %.0f swipes/s%n",
                metrics.successful.get() * 1000.0 / totalMs);
        System.out.println("HTTP connections: 1 shared HttpClient (HTTP/1.1 keep-alive pool)");

        System.out.println("\n===== Subscribe Path =====");
        long latencySum = 0;
        int latencyCount = 0;
        for (LeaderboardSubscriber sub : subscribers) {
            System.out.printf("Resort %2d: %d updates, connect %d ms, %d reconnections%n",
                    sub.getResortID(), sub.getUpdatesReceived(), sub.getFirstConnectMs(), sub.getReconnections());
            for (long latency : sub.getPushLatenciesMs()) {
                latencySum += latency;
                latencyCount++;
            }
        }
        System.out.printf("Total leaderboard updates: %d, mean push latency: %.1f ms%n",
                latencyCount, latencyCount == 0 ? 0.0 : latencySum / (double) latencyCount);
    }
}