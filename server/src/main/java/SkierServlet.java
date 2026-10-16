import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import model.LiftRide;
import model.ResponseMsg;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

public class SkierServlet extends HttpServlet {
  private Gson gson = new Gson();

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {

    res.setContentType("application/json");
    res.setCharacterEncoding("UTF-8");

    String urlPath = req.getPathInfo();

    // Check we have a URL
    if (urlPath == null || urlPath.isEmpty()) {
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
      res.getWriter().write(gson.toJson(new ResponseMsg("missing parameters")));
      return;
    }

    String[] urlParts = urlPath.split("/");

    if (!isUrlValid(urlParts)) {
      res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      res.getWriter().write(gson.toJson(new ResponseMsg("invalid url")));
    } else {
      res.setStatus(HttpServletResponse.SC_OK);

      // Extract parameters
      String resortID = urlParts[1];
      String seasonID = urlParts[3];
      String dayID = urlParts[5];
      String skierID = urlParts[7];

      // Create response
      ResponseMsg response = new ResponseMsg(
          String.format("GET request processed for skier %s at resort %s, season %s, day %s",
              skierID, resortID, seasonID, dayID)
      );

      res.getWriter().write(gson.toJson(response));
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {

    res.setContentType("application/json");
    res.setCharacterEncoding("UTF-8");

    String urlPath = req.getPathInfo();

    // Check we have a URL
    if (urlPath == null || urlPath.isEmpty()) {
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
      res.getWriter().write(gson.toJson(new ResponseMsg("missing parameters")));
      return;
    }

    String[] urlParts = urlPath.split("/");

    if (!isUrlValid(urlParts)) {
      res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      res.getWriter().write(gson.toJson(new ResponseMsg("invalid url")));
      return;
    }

    // Parse JSON body
    try {
      StringBuilder jsonBody = new StringBuilder();
      BufferedReader reader = req.getReader();
      String line;
      while ((line = reader.readLine()) != null) {
        jsonBody.append(line);
      }

      LiftRide liftRide = gson.fromJson(jsonBody.toString(), LiftRide.class);

      if (liftRide == null || !isLiftRideValid(liftRide)) {
        res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        res.getWriter().write(gson.toJson(new ResponseMsg("invalid JSON body")));
        return;
      }

      // Extract URL parameters
      String resortID = urlParts[1];
      String seasonID = urlParts[3];
      String dayID = urlParts[5];
      String skierID = urlParts[7];

      // Process the lift ride (in real app, save to database)
      res.setStatus(HttpServletResponse.SC_CREATED);
      ResponseMsg response = new ResponseMsg(
          String.format("Lift ride recorded: skier %s, resort %s, season %s, day %s, time %d, lift %d",
              skierID, resortID, seasonID, dayID, liftRide.getTime(), liftRide.getLiftID())
      );

      res.getWriter().write(gson.toJson(response));

    } catch (JsonSyntaxException e) {
      res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      res.getWriter().write(gson.toJson(new ResponseMsg("invalid JSON format")));
    }
  }

  private boolean isUrlValid(String[] urlParts) {
    // Expected URL: /skiers/{resortID}/seasons/{seasonID}/days/{dayID}/skiers/{skierID}
    // urlParts = ["", "1", "seasons", "2019", "days", "1", "skiers", "123"]

    if (urlParts.length != 8) {
      return false;
    }

    // Check structure
    if (!urlParts[2].equals("seasons") ||
        !urlParts[4].equals("days") ||
        !urlParts[6].equals("skiers")) {
      return false;
    }
    // Validate numeric parameters: must be positive integers
    int[] idPositions = {1, 3, 5, 7};  // resortID, seasonID, dayID, skierID
    for (int pos : idPositions) {
      try {
        if (Integer.parseInt(urlParts[pos]) <= 0) {
          return false;
        }
      } catch (NumberFormatException e) {
        return false;
      }
    }
    return true;
  }

  private boolean isLiftRideValid(LiftRide liftRide) {
    return liftRide.getTime() >= 1 && liftRide.getTime() <= 360 &&
        liftRide.getLiftID() >= 1 && liftRide.getLiftID() <= 40;
  }
}