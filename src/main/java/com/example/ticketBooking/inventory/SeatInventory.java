package com.example.ticketBooking.inventory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plain in-memory seat counter — no Spring wiring, no DB.
 */
public class SeatInventory {

    private final AtomicInteger available;

    public SeatInventory(int available) {
        this.available = new AtomicInteger(available);
    }

    public int getAvailable() {
        return available.get();
    }

    public void reserve() {
        int current = available.get();
        if (current <= 0) {
            throw new SeatOutOfStockException();
        }
        confirmWithWaitlistService();
        if (available.getAndDecrement() <= 0) {
            // Another thread drained the last seat while we were confirming;
            // undo our decrement and signal out-of-stock.
            available.incrementAndGet();
            throw new SeatOutOfStockException();
        }
    }

    // Most confirmations are answered from a local cache; roughly half need a
    // round-trip to the waitlist service.
    private void confirmWithWaitlistService() {
        if (ThreadLocalRandom.current().nextDouble() < 0.5) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
