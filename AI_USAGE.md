# AI Usage Reflection

## Where I Used AI
I used Claude as an AI assistant to clarify which tools to use and how to use them (Tomcat, IntelliJ, Maven, `java.net.http`, EC2 deployment) and to understand the Lab 2 concurrency concepts. Starting from the Lab 2 code, I had the AI help modify and extend it to meet the assignment requirements, and I checked each change. I deployed and tested every component, debugged the issues I ran into, and interpreted the results.

## Accepted
1. Per-thread record buffers in Part 2, to avoid lock contention while sending (same idea as Lab 2 Exercise 3)
2. Using Java 11's built-in `java.net.http` for HTTP and WebSocket instead of an extra library

## Rejected or Corrected
1. The AI guessed that sharing one `HttpClient` caused most of the 6.7 s push latency. Rerunning Part 1 showed it only added about 18 ms, so the guess was wrong.
2. The AI first thought a Tomcat 404 came from a stale `web.xml`. I found the real cause was my context path `/ski_servlet` vs `/ski-servlet`.

## My Own Reasoning
I found my laptop clock was 2.47 s off, synced it, and reran Part 1 so both parts were measured under the same conditions.