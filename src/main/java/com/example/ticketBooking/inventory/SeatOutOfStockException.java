package com.example.ticketBooking.inventory;

public class SeatOutOfStockException extends RuntimeException {
    public SeatOutOfStockException() {
        super("No seats left to reserve");
    }
}
