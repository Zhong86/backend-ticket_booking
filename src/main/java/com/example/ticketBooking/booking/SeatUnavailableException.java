package com.example.ticketBooking.booking;

public class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException(Long seatId) {
        super("Seat " + seatId + " is no longer available");
    }
}
