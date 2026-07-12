package com.example.ticketBooking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.ticketBooking.booking.Booking;
import com.example.ticketBooking.booking.BookingRepository;
import com.example.ticketBooking.booking.HoldExpiryScheduler;
import com.example.ticketBooking.booking.Seat;
import com.example.ticketBooking.booking.SeatRepository;

import jakarta.persistence.EntityManagerFactory;

@SpringBootTest
@Testcontainers
public class HoldExpirySchedulerTest {

  // NOTE: withInitScript() runs relative to test/resources by default.
  // Copy your schema.sql into src/test/resources/schema.sql, or swap this
  // for .withCopyFileToContainer(MountableFile.forHostPath("path/to/schema.sql"),
  // ...)
  // if you'd rather point directly at the existing file instead of duplicating
  // it.
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
      .withDatabaseName("ticketbooking")
      .withUsername("postgres")
      .withPassword("postgres")
      .withCopyFileToContainer(
    org.testcontainers.utility.MountableFile.forHostPath("schema.sql"),
    "/docker-entrypoint-initdb.d/schema.sql");

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired
  HoldExpiryScheduler holdExpiryScheduler;

  @Autowired
  SeatRepository seatRepository;

  @Autowired
  BookingRepository bookingRepository;

  @Autowired
  EntityManagerFactory entityManagerFactory;

  // NOTE: with Testcontainers each test class gets its own fresh container,
  // but TRUNCATE between @Test methods within the class is still needed since
  // the container (and its schema) persists across the whole class by default.
  @BeforeEach
  void resetDb() {
    bookingRepository.deleteAll();
    seatRepository.deleteAll();
  }

  @Test
  void releaseExpiredHolds_flipsSeatsAndBookings() {
    // arrange — seed 3 expired holds + 1 that's still valid
    Seat seat1 = seatRepository.save(newSeat("HELD"));
    Seat seat2 = seatRepository.save(newSeat("HELD"));
    Seat seat3 = seatRepository.save(newSeat("HELD"));
    Seat stillHeld = seatRepository.save(newSeat("HELD"));

    Booking b1 = bookingRepository.save(heldBooking(seat1.getId(), Instant.now().minusSeconds(60)));
    bookingRepository.save(heldBooking(seat2.getId(), Instant.now().minusSeconds(30)));
    bookingRepository.save(heldBooking(seat3.getId(), Instant.now().minusSeconds(1)));
    Booking notExpired = bookingRepository.save(heldBooking(stillHeld.getId(), Instant.now().plusSeconds(300)));

    // act
    holdExpiryScheduler.releaseExpiredHolds();

    // assert — expired ones flipped
    assertThat(seatRepository.findById(seat1.getId()).get().getStatus()).isEqualTo("AVAILABLE");
    assertThat(seatRepository.findById(seat2.getId()).get().getStatus()).isEqualTo("AVAILABLE");
    assertThat(seatRepository.findById(seat3.getId()).get().getStatus()).isEqualTo("AVAILABLE");
    assertThat(bookingRepository.findById(b1.getId()).get().getStatus()).isEqualTo("EXPIRED");

    // assert — untouched booking left alone
    assertThat(seatRepository.findById(stillHeld.getId()).get().getStatus()).isEqualTo("HELD");
    assertThat(bookingRepository.findById(notExpired.getId()).get().getStatus()).isEqualTo("HELD");
  }

  @Test
  void releaseExpiredHolds_usesConstantQueryCount() {
    // arrange — seed 50 expired holds
    List<Long> seatIds = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      Seat seat = seatRepository.save(newSeat("HELD"));
      bookingRepository.save(heldBooking(seat.getId(), Instant.now().minusSeconds(60)));
      seatIds.add(seat.getId());
    }

    Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    stats.clear();

    // act
    holdExpiryScheduler.releaseExpiredHolds();

    // assert — should be ~3 queries (1 select + 2 bulk updates), not ~150
    long queryCount = stats.getQueryExecutionCount();
    assertThat(queryCount).isLessThanOrEqualTo(5);
  }

  // --- test helpers ---------------------------------------------------
  // ASSUMPTION: adjust field names/constructor args to match your actual
  // Seat/Booking entities — I don't have those source files to verify against.

  private Seat newSeat(String status) {
    Seat seat = new Seat();
    seat.setStatus(status);
    // showtime_id is NOT NULL in schema.sql — seed a real showtime first
    // if your Seat entity requires a non-null showtimeId at save time.
    return seat;
  }

  private Booking heldBooking(Long seatId, Instant heldUntil) {
    Booking booking = new Booking(seatId, /* userId */ 1L, /* idempotencyKey */ java.util.UUID.randomUUID().toString());
    booking.markHeld(heldUntil);
    return booking;
  }
}
