package com.example.town.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChunkKeyTest {

    @Test
    void adjacencyHonorsWorldAndDiagonalSetting() {
        ChunkKey center = new ChunkKey("world", 4, -2);
        assertTrue(center.isAdjacentTo(new ChunkKey("world", 5, -2), false));
        assertTrue(center.isAdjacentTo(new ChunkKey("world", 5, -1), true));
        assertFalse(center.isAdjacentTo(new ChunkKey("world", 5, -1), false));
        assertFalse(center.isAdjacentTo(new ChunkKey("world_nether", 5, -2), true));
        assertFalse(center.isAdjacentTo(center, true));
    }

    @Test
    void persistenceRoundTripAndInvalidInput() {
        ChunkKey key = new ChunkKey("world", -120, 88);
        assertEquals(key, ChunkKey.decode(key.encode()));
        assertNull(ChunkKey.decode("world:1"));
        assertNull(ChunkKey.decode("world:x:2"));
    }
}
