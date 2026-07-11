package com.example.ticketBooking;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

// NOTE: this hits a real running instance over real HTTP — it is a black-box
// concurrency test, not a @SpringBootTest. Start the app separately
// (./mvnw spring-boot:run) before running this test; it will fail with
// connection errors otherwise. This is intentional: MockMvc doesn't exercise
// real threads/sockets, and that's exactly what a race-condition test needs.
class ConcurrentBookingTest {

    private static final int NUM_REQUESTS = 100;

    // Adjust to match your actual local Postgres credentials/DB name.
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/ticket_booking";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "password";

    @Test
    void compareLockingStrategies() throws Exception {
        Result pessimistic = runConcurrencyTest("pessimistic", 1L);
        Result optimistic = runConcurrencyTest("optimistic", 2L);

        System.out.println("=========== COMPARISON ===========");
        System.out.printf("%-15s %-12s %-12s%n", "", "Pessimistic", "Optimistic");
        System.out.printf("%-15s %-12d %-12d%n", "Successes", pessimistic.success(), optimistic.success());
        System.out.printf("%-15s %-12d %-12d%n", "Conflicts", pessimistic.conflicts(), optimistic.conflicts());
        System.out.printf("%-15s %-12d %-12d%n", "Errors", pessimistic.errors(), optimistic.errors());
        System.out.printf("%-15s %-12d %-12d%n", "Time (ms)", pessimistic.elapsedMs(), optimistic.elapsedMs());

        assertEquals(1, pessimistic.success(), "Pessimistic locking should have exactly 1 winner");
        assertEquals(1, optimistic.success(), "Optimistic locking should have exactly 1 winner");
    }

    private record Result(int success, int conflicts, int errors, long elapsedMs) {}

    private void resetSeat(long seatId) throws Exception {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.execute("UPDATE seats SET status = 'AVAILABLE', version = 0 WHERE id = " + seatId);
            stmt.execute("DELETE FROM bookings WHERE seat_id = " + seatId);
        }
    }

    private Result runConcurrencyTest(String strategy, long seatId) throws Exception {
        resetSeat(seatId);

        HttpClient client = HttpClient.newHttpClient();
        ExecutorService pool = Executors.newFixedThreadPool(NUM_REQUESTS);

        CountDownLatch readyLatch = new CountDownLatch(NUM_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherCount = new AtomicInteger(0);

        for (int i = 0; i < NUM_REQUESTS; i++) {
            final int userId = i;
            pool.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await(); // wait for the starting gun

                    String body = """
                        {"seatId": %d, "userId": %d}
                        """.formatted(seatId, userId);

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/bookings?strategy=" + strategy))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                    HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                    switch (response.statusCode()) {
                        case 201 -> successCount.incrementAndGet();
                        case 409 -> conflictCount.incrementAndGet();
                        default -> {
                            otherCount.incrementAndGet();
                            System.out.println("Unexpected status " + response.statusCode()
                                + ": " + response.body());
                        }
                    }
                } catch (Exception e) {
                    otherCount.incrementAndGet();
                    System.out.println("Request failed: " + e.getClass().getSimpleName()
                        + " - " + e.getMessage());
                }
            });
        }

        readyLatch.await(); // wait until all threads are queued up

        long startNanos = System.nanoTime();
        startLatch.countDown(); // release them all at once

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        System.out.println("=== " + strategy.toUpperCase() + " ===");
        System.out.println("Successful bookings: " + successCount.get());
        System.out.println("Conflicts (409):     " + conflictCount.get());
        System.out.println("Other/errors:        " + otherCount.get());
        System.out.println("Wall clock time:     " + elapsedMs + " ms");
        System.out.println();

        return new Result(successCount.get(), conflictCount.get(), otherCount.get(), elapsedMs);
    }
}
