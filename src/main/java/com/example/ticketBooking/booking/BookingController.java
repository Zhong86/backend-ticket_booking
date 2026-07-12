package com.example.ticketBooking.booking;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    public record BookingRequest(Long seatId, Long userId, String idempotencyKey) {}

    @PostMapping
    public ResponseEntity<Booking> bookSeat(
            @RequestBody BookingRequest request,
            @RequestParam(defaultValue = "optimistic") String strategy) {

        var result = "optimistic".equals(strategy)
            ? bookingService.bookSeatOptimistic(request.seatId(), request.userId(), request.idempotencyKey())
            : bookingService.bookSeat(request.seatId(), request.userId(), request.idempotencyKey());

        return result
            .map(booking -> ResponseEntity.status(HttpStatus.CREATED).body(booking))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).build());
    }
}
