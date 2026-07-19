package com.example.ticketBooking.sharding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;

class ConsistentHashRingTest {

  private static final int TOTAL_KEYS = 20_000;

  @Test
  void distributesKeysReasonablyEvenlyAcrossNodes() {
    ConsistentHashRing<String> ring = new ConsistentHashRing<>();
    ring.addNode("shard-0");
    ring.addNode("shard-1");
    ring.addNode("shard-2");

    Map<String, Integer> counts = new HashMap<>();
    for (int i = 0; i < TOTAL_KEYS; i++) {
      counts.merge(ring.getNode("showtime-" + i), 1, Integer::sum);
    }

    assertThat(counts.keySet()).containsExactlyInAnyOrder("shard-0", "shard-1", "shard-2");

    // With 150 virtual nodes per shard, no shard should land wildly outside
    // its fair 1/3 share. Without virtual nodes this assertion would flake hard.
    int expectedPerShard = TOTAL_KEYS / 3;
    for (int count : counts.values()) {
      assertThat(count).isCloseTo(expectedPerShard, Percentage.withPercentage(20));
    }
  }

  @Test
  void addingANodeOnlyRemapsRoughlyItsFairShareOfKeys() {
    ConsistentHashRing<String> ring = new ConsistentHashRing<>();
    ring.addNode("shard-0");
    ring.addNode("shard-1");
    ring.addNode("shard-2");

    Map<Integer, String> before = new HashMap<>();
    for (int i = 0; i < TOTAL_KEYS; i++) {
      before.put(i, ring.getNode("showtime-" + i));
    }

    ring.addNode("shard-3");

    long moved = 0;
    for (int i = 0; i < TOTAL_KEYS; i++) {
      if (!ring.getNode("showtime-" + i).equals(before.get(i))) {
        moved++;
      }
    }

    double fractionMoved = moved / (double) TOTAL_KEYS;
    // Naive `id % N` hashing would move ~75% of keys going from 3 to 4 nodes.
    // Consistent hashing should move roughly the new node's fair share (~25%).
    assertThat(fractionMoved).isLessThan(0.35);
  }

  @Test
  void removingANodeOnlyRemapsKeysThatWereOnThatNode() {
    ConsistentHashRing<String> ring = new ConsistentHashRing<>();
    ring.addNode("shard-0");
    ring.addNode("shard-1");
    ring.addNode("shard-2");

    Map<Integer, String> before = new HashMap<>();
    for (int i = 0; i < TOTAL_KEYS; i++) {
      before.put(i, ring.getNode("showtime-" + i));
    }

    ring.removeNode("shard-1");

    for (int i = 0; i < TOTAL_KEYS; i++) {
      String previous = before.get(i);
      String current = ring.getNode("showtime-" + i);

      if (previous.equals("shard-1")) {
        assertThat(current).isIn("shard-0", "shard-2");
      } else {
        // keys that were never on the removed shard must not move at all
        assertThat(current).isEqualTo(previous);
      }
    }
  }

  @Test
  void sameKeyAlwaysMapsToSameNodeGivenAFixedRing() {
    ConsistentHashRing<String> ring = new ConsistentHashRing<>();
    ring.addNode("shard-0");
    ring.addNode("shard-1");

    String first = ring.getNode("showtime-42");
    for (int i = 0; i < 100; i++) {
      assertThat(ring.getNode("showtime-42")).isEqualTo(first);
    }
  }
}
