package com.example.ticketBooking.inventory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Plain in-memory seat counter — no Spring wiring, no DB. Exists purely as a
 * deliberately unsynchronized target for {@code SeatInventoryFlakyTest}: two
 * threads can both read {@code available} before either writes it back,
 * losing one of the reservations. Do not "fix" this by adding
 * synchronized/AtomicInteger — the race is the point.
 */
public class SeatInventory {

    private int available;

    public SeatInventory(int available) {
        this.available = available;
    }

    public int getAvailable() {
        return available;
    }

    public void reserve() {
        int current = available;
        if (current <= 0) {
            throw new SeatOutOfStockException();
        }
        confirmWithWaitlistService();
        available = current - 1;
    }

    // Most confirmations are answered from a local cache; roughly half need a
    // round-trip to the waitlist service, which briefly suspends this thread
    // and widens the window for another thread to interleave.
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
