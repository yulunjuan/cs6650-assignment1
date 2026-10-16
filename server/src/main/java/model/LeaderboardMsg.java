package model;

import java.util.List;

public class LeaderboardMsg {
  private String resortID;
  private String serverTimestamp;
  private List<LeaderboardEntry> top;

  public LeaderboardMsg(String resortID, String serverTimestamp, List<LeaderboardEntry> top) {
    this.resortID = resortID;
    this.serverTimestamp = serverTimestamp;
    this.top = top;
  }
}