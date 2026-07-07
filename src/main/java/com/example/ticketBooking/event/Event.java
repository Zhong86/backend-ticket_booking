package com.example.ticketBooking.event;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "venue_id", nullable = false)
    private Long venueId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Event() {
    }

    public Event(String name, Long venueId) {
        this.name = name;
        this.venueId = venueId;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getVenueId() {
        return venueId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
