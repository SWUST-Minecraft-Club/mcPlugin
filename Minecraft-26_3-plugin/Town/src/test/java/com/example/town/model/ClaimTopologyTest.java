package com.example.town.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClaimTopologyTest {
    private static ChunkKey c(int x,int z) { return new ChunkKey("world",x,z); }

    @Test void removalOfBridgeIsRejectedButADetourRemainsConnected() {
        Set<ChunkKey> claims=new HashSet<>(Set.of(c(0,0),c(1,0),c(2,0)));
        assertTrue(ClaimTopology.connected(claims,c(0,0),false));
        claims.remove(c(1,0));
        assertFalse(ClaimTopology.connected(claims,c(0,0),false));
        claims.addAll(Set.of(c(0,1),c(1,1),c(2,1)));
        assertTrue(ClaimTopology.connected(claims,c(0,0),false));
    }

    @Test void rootMustExistAndForeignWorldsAndDiagonalCornersDoNotJoin() {
        assertFalse(ClaimTopology.connected(Set.of(c(1,0)),c(0,0),false));
        assertFalse(ClaimTopology.connected(Set.of(c(0,0),c(1,1)),c(0,0),false));
        assertTrue(ClaimTopology.connected(Set.of(c(0,0),c(1,1)),c(0,0),true));
        assertFalse(ClaimTopology.connected(Set.of(c(0,0),new ChunkKey("other",0,0)),c(0,0),true));
    }
}
