# Ticket Booking System

A practice backend project for **Amazon (AWS) Backend Internship** prep — built to cover backend engineering fundamentals, data structures & algorithms, and system design concepts through one cohesive, incrementally-built application.

Think Ticketmaster/BookMyShow: browse events, view seat maps, book a seat, get a confirmation. Simple to describe, deep enough to require real concurrency control, caching, async processing, and rate limiting.

## IBM Bob 2.0 Hackathon
Used as the test demo for the flaky debugger. This repo consists of concurrent calls that can trigger unstable failure tests. Uses Java, Springboot Maven.

## Notes

### Redis Caching 
Caches info acquired from DB with expiration of CACHE_TTL. Set cache as key: value pair. 
Problem: Springboot saves as EventPageResult (premade class) but Redis gets and returns only as JSON - needs additional parsing with GenericJacksonJsonRedisSerializer & BasicPolymorphicTypeValidator

### Indexes
Creates a B-tree for values -> row locations, so database can jump to matching rows instead of checking every row in the table. Trades off write speed and storage for read speed. 
- Every index has to be updated on every write. If events has 5 indexes and a row is inserted, Postgres has to write 6 times. 
- Each index uses around 10 - 50% of the table's size. 
- Diminishing returns on low-cardinality columns. If a .status only has 4 options and 1 status is 40%, that wouldn't be that much faster compared to no index. 

### Locking
- Pessimistic Locking: Assume conflict will happen, lock the row. 
```
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Override
Optional<Seat> findById(Long id);
```
- Optimistic Locking: Assume conflict won't happen, check if already taken by someone at the end.
```
// Add @Version in Entity, Hibernate updates version everytime row is write.
@Version
@Column(nullable = false)
private Integer version;

@Query("SELECT s FROM Seat s WHERE s.id = :id")
Optional<Seat> findByIdNoLock(@Param("id") Long id);
```
- Use ConcurrentBookingTest to find which method is faster
#### HIBERNATE BASED ISSUE
With Hibernate, optimistic-locking can be error. seatRepository DOES NOT run the UPDATE immediately but instead batches changes and flushes at the end of transaction. The REAL UPDATE query can happen after my method has exited my try/block. 
The fix is to force the flush in the try block. ``` saveAndFlush() ```
#### FINAL RESULT - Optimistic Locking
Optimistic: 110 ms
Pessimistic: 305 ms

### Idempotency Key & Hold
Makes sure a double call will not trigger another booking -> just findByIdempotencyKey. 
Hold will be triggered by HoldExpiryScheduler, it is first initiated when booking with HOLD_DURATION saved as heldUntil. 

### N + 1 Problem
```
List<Booking> expired = bookingRepository.findByStatusAndHeldUntilBefore("HELD", Instant.now());
for (Booking booking : expired) {
  seatRepository.findById(booking.getSeatId()).ifPresent(seat -> {
    seat.setStatus("AVAILABLE");
    seatRepository.save(seat);
  });
  booking.setStatus("EXPIRED");
  bookingRepository.save(booking);
}
```
- bookRepo reads once
- seatRepo reads N times from expired
- seat writes N times
- booking sets N times
Solve using: 
```
UPDATE FROM seat s SET s.status = 'AVAILABLE' WHERE s.id in :seatIds
```

### Testcontainers
Java library that allows test code spin up real Docker containers. This tests with real Postgres / Redis / RabbitMQ instead of using a mock or in-memory. 
HoldExpirySchedulerTest : 
1. flipsSeatsAndBookings() -> Seeds Seat with HELD & heldUntil 60 seconds in the past (expired state). Calls releaseExpiredHolds to switch it to AVAILABLE. 
2. useConstantQueryCount() -> Tests queries to stay low; checks if N + 1 is solved with assertThat(queryCount) < 3. 
[!WARNING] 
Use stats.getPrepareStatementCount() instead of stats.getQueryExecutionCount() to track query numbers. Hibernate's Statistics ignores @Query, so need to track the prepared statements instead. 

### Sharding
Splitting rows across multiple databases - need to determine which row lives on where.
Needed because database systems can run into bottlenecks on CPU, memory, disc, etc. 
Shard: a partition of the table.
Types of sharding: 
- Geo-based sharding (based on user's location)
- Range-based sharding (partition based on certain rule)
- Hash-based sharding (hash of key -> good hash algorithm will evenly distribute)
#### Manual vs Automatic sharding
- Automatic will dynamically repartition data when an uneven distribution is detected. Better scalability & performance. 
- Manual (done in application layer), increase complexity for runtime. Uneven distribution.
Hotspots due to uneven distribution can lead to performance issues. Migrations is a lot more difficult, system needs to make sure all shards is migrated correctly. 
#### Advantage of sharding: 
- Allows sytem to scale out, can handle more data can traditional.
- Smaller data on each shard means lower indexes & faster query performance. 
- Reliability and accessibility, downtime does not take the whole system. 
- Can work on comodity hardware
#### Disadvantages of sharding: 
- Not all data can be amendable to sharding. Foreign key relationships can only be maintained in a single shard. 
- Some types of cross shard queries like JOINS might be complex or not even possible.
- Once sharding is set up, it is very difficult / not possible to undo. 
- Need to ensure high availability -> increases operational cost compared to traditional.
Shard key for this project: showtimeId. 
Events or venues are small and low-write, better to stay in one database. 
