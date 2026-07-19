package com.example.ticketBooking.booking;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.ticketBooking.ratelimit.RateLimiterService;

@RestController
@RequestMapping("/bookings")
public class BookingController {

  private static final int MAX_BOOKINGS_PER_MINUTE = 5;

  private final BookingService bookingService;
  private final RateLimiterService rateLimiterService;

  public BookingController(BookingService bookingService, RateLimiterService rateLimiterService) {
    this.bookingService = bookingService;
    this.rateLimiterService = rateLimiterService;
  }

  public record BookingRequest(Long showtimeId, Long seatId, Long userId, String idempotencyKey) {
  }

  @PostMapping
  public ResponseEntity<Booking> bookSeat(
      @RequestBody BookingRequest request,
      @RequestParam(defaultValue = "optimistic") String strategy) {
    String rateLimitKey = "ratelimit:booking:" + request.userId();
    if (!rateLimiterService.allow(rateLimitKey, MAX_BOOKINGS_PER_MINUTE)) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }

    var result = bookingService.bookSeatOnShard(
      request.showtimeId(), strategy, request.seatId(), request.userId(), request.idempotencyKey());

    return result
        .map(booking -> ResponseEntity.status(HttpStatus.CREATED).body(booking))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).build());
  }

  @PostMapping("/{id}/confirm")
  public ResponseEntity<Booking> confirmBooking(
    @PathVariable Long id
  ) {
    return bookingService.confirmBooking(id)
      .map(booking -> (ResponseEntity.status(HttpStatus.OK).body(booking)))
      .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).build());
  }
}
