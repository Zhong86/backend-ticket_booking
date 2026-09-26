package com.example.ticketBooking.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

// Intentionally flaky: this is a fixture for exercising a flaky-test
// detector against a genuine race condition, not a bug to fix. See
// SeatInventory's class-level note before "fixing" the race away.
class SeatInventoryFlakyTest {

    @Test
    void reserveTakesOneSeat() {
        SeatInventory inventory = new SeatInventory(3);

        inventory.reserve();

        assertEquals(2, inventory.getAvailable());
    }

    @Test
    void reserveFailsWhenSoldOut() {
        SeatInventory inventory = new SeatInventory(0);

        assertThrows(SeatOutOfStockException.class, inventory::reserve);
    }

    @Test
    void concurrentReservations_eachTakeOneSeat() throws Exception {
        SeatInventory inventory = new SeatInventory(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<?> first = pool.submit(inventory::reserve);
        Future<?> second = pool.submit(inventory::reserve);

        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        // Fails intermittently (~50% of runs): when both threads read
        // `available` before either writes it back, one reservation is lost
        // and this is 1, not 0.
        assertEquals(0, inventory.getAvailable());
    }
}
