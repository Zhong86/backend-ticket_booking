package com.example.ticketBooking.sharding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * A generic consistent-hash ring. Nodes and keys are hashed onto the same
 * circular keyspace (0 .. 2^64-1, using the first 8 bytes of an MD5 digest);
 * a key belongs to whichever node's point is the next one clockwise.
 *
 * Each node gets {@code virtualNodesPerNode} points on the ring instead of
 * one, so that a small number of physical nodes still end up with a roughly
 * even share of the keyspace. Without virtual nodes, 3-4 randomly placed
 * points can easily produce a 60/30/10 split.
 */
public class ConsistentHashRing<T> {

  private static final int DEFAULT_VIRTUAL_NODES = 150;

  private final int virtualNodesPerNode;
  private final NavigableMap<Long, T> ring = new TreeMap<>();

  public ConsistentHashRing() {
    this(DEFAULT_VIRTUAL_NODES);
  }

  public ConsistentHashRing(int virtualNodesPerNode) {
    this.virtualNodesPerNode = virtualNodesPerNode;
  }

  public void addNode(T node) {
    for (int i = 0; i < virtualNodesPerNode; i++) {
      ring.put(hash(node.toString() + "#vn" + i), node);
    }
  }

  public void removeNode(T node) {
    for (int i = 0; i < virtualNodesPerNode; i++) {
      ring.remove(hash(node.toString() + "#vn" + i));
    }
  }

  /** Returns the node owning this key: the next ring point clockwise, wrapping past the top back to 0. */
  public T getNode(String key) {
    if (ring.isEmpty()) {
      throw new IllegalStateException("Ring has no nodes");
    }
    long h = hash(key);
    var entry = ring.ceilingEntry(h);
    if (entry == null) {
      entry = ring.firstEntry();
    }
    return entry.getValue();
  }

  public Set<T> nodes() {
    return new LinkedHashSet<>(ring.values());
  }

  private long hash(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      long h = 0;
      for (int i = 0; i < 8; i++) {
        h = (h << 8) | (digest[i] & 0xFF);
      }
      return h;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
