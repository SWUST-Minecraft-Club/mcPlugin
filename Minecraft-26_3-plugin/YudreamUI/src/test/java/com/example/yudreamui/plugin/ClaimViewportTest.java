package com.example.yudreamui.plugin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClaimViewportTest {
    @Test void usesTownCenterIncludingNegativeCoordinatesAndRejectsOtherWorlds(){
        var map=LegacyPresentation.claimTerrain("verification · -1, -3","verification","minecraft:overworld");
        assertNotNull(map);assertEquals(-4,map.minChunkX());assertEquals(-6,map.minChunkZ());
        assertNull(LegacyPresentation.claimTerrain("another · -1, -3","verification","minecraft:overworld"));
        assertNull(LegacyPresentation.claimTerrain("{meta:claim_center}","verification","minecraft:overworld"));
        assertNull(LegacyPresentation.claimTerrain("verification · 2147483647, 0","verification","minecraft:overworld"));
    }
}
