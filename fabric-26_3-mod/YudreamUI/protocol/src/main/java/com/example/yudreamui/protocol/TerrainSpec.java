package com.example.yudreamui.protocol;

/** Bounded, north-up local terrain viewport. Coordinates are chunk coordinates. */
public record TerrainSpec(String dimension, int chunkX, int chunkZ, int chunks) {
    public TerrainSpec {
        if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || dimension.length() > 128)
            throw new IllegalArgumentException("Invalid terrain dimension");
        if (chunks < 1 || chunks > 15 || chunks % 2 == 0)
            throw new IllegalArgumentException("Terrain extent must be an odd number from 1 to 15");
        if (Math.abs((long) chunkX) + chunks / 2 > 1_874_999 || Math.abs((long) chunkZ) + chunks / 2 > 1_874_999)
            throw new IllegalArgumentException("Terrain coordinates outside world bounds");
    }
    public int minChunkX() { return chunkX - chunks / 2; }
    public int minChunkZ() { return chunkZ - chunks / 2; }
    public int pixels() { return chunks * 16; }
}
