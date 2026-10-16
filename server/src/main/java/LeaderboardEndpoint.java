import com.google.gson.Gson;
import model.LeaderboardEntry;
import model.LeaderboardMsg;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@ServerEndpoint("/leaderboard/{resortID}")
public class LeaderboardEndpoint {

  private static final int TOP_N = 5;
  private static final Gson gson = new Gson();

  // resortID -> sessions subscribed to that resort
  private static final Map<String, Set<Session>> subscribers = new ConcurrentHashMap<>();
  private static final ScheduledExecutorService pusher =
      Executors.newSingleThreadScheduledExecutor();

  static {
    // Dummy: every 500ms, push a synthetic top-N to each resort's subscribers
    pusher.scheduleAtFixedRate(LeaderboardEndpoint::pushAll, 500, 500, TimeUnit.MILLISECONDS);
  }

  @OnOpen
  public void onOpen(Session session, @PathParam("resortID") String resortID) {
    subscribers.computeIfAbsent(resortID, k -> ConcurrentHashMap.newKeySet()).add(session);
  }

  @OnClose
  public void onClose(Session session, @PathParam("resortID") String resortID) {
    removeSession(session, resortID);
  }

  @OnError
  public void onError(Session session, Throwable t, @PathParam("resortID") String resortID) {
    System.out.println("WebSocket error on session " + session.getId() + ": " + t.getMessage());
    removeSession(session, resortID);
  }

  private static void removeSession(Session session, String resortID) {
    Optional.ofNullable(subscribers.get(resortID)).ifPresent(s -> s.remove(session));
  }

  private static void pushAll() {
    // An uncaught exception would silently cancel all future runs of this scheduled task,
    // so catch everything to keep the timer alive
    try {
      for (Map.Entry<String, Set<Session>> e : subscribers.entrySet()) {
        String json = syntheticLeaderboard(e.getKey());
        for (Session s : e.getValue()) {
          if (s.isOpen()) {
            s.getAsyncRemote().sendText(json);
          }
        }
      }
    } catch (Exception ex) {
      System.out.println("Leaderboard push failed: " + ex.getMessage());
    }
  }

  private static String syntheticLeaderboard(String resortID) {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    List<LeaderboardEntry> top = new ArrayList<>();
    int vertical = 50000;
    for (int rank = 1; rank <= TOP_N; rank++) {
      vertical -= random.nextInt(100, 1000);  // keep verticals in descending order
      int skierID = random.nextInt(1, 100001);
      top.add(new LeaderboardEntry(rank, skierID, vertical));
    }
    LeaderboardMsg msg = new LeaderboardMsg(resortID, Instant.now().toString(), top);
    return gson.toJson(msg);
  }
}