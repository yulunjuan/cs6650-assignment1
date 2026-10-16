# Server

Build: `mvn clean package`

Deploy:
```
scp -i cs6650-lab1-key.pem target/ski-servlet-1.0-SNAPSHOT.war ec2-user@<EC2_IP>:/opt/tomcat9/webapps/ski-servlet.war
/opt/tomcat9/bin/startup.sh
```

Endpoints (context path `/ski-servlet`):
- `POST /skiers/{resortID}/seasons/{seasonID}/days/{dayID}/skiers/{skierID}`
- `GET /health`
- `WebSocket /leaderboard/{resortID}`

Test cases: `skier-tests.http`