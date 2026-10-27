package com.example.vehicles;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

/** Created once per physics tick, on the vehicle's entity thread. */
final class VehicleTerrain implements DrivePhysics.Terrain {
    private final World world;
    private final Consumer<Long> loadChunk;
    private final Map<String, java.util.List<BoundingBox>> shapes = new HashMap<>();

    VehicleTerrain(World world, Consumer<Long> loadChunk) { this.world = world; this.loadChunk = loadChunk; }

    @Override public boolean clear(DrivePhysics.Bounds bounds) {
        if (bounds.minY() < world.getMinHeight() || bounds.maxY() >= world.getMaxHeight()) return false;
        var border = world.getWorldBorder();
        if (!border.isInside(new Location(world, bounds.minX(), bounds.minY(), bounds.minZ()))
                || !border.isInside(new Location(world, bounds.maxX(), bounds.minY(), bounds.maxZ()))) return false;
        int minX = (int) Math.floor(bounds.minX()), maxX = (int) Math.floor(bounds.maxX());
        int minZ = (int) Math.floor(bounds.minZ()), maxZ = (int) Math.floor(bounds.maxZ());
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (!Bukkit.isOwnedByCurrentRegion(world, cx, cz) || !world.isChunkLoaded(cx, cz)) {
                    loadChunk.accept(((long) cx << 32) | (cz & 0xffffffffL));
                    return false;
                }
            }
        }
        BoundingBox box = new BoundingBox(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            for (int y = (int) Math.floor(bounds.minY()); y <= (int) Math.floor(bounds.maxY()); y++) {
                String key = x + ":" + y + ":" + z;
                final int bx = x, by = y, bz = z;
                var collisions = shapes.computeIfAbsent(key, ignored -> {
                    Block block = world.getBlockAt(bx, by, bz);
                    if (block.isLiquid()) return java.util.List.of(new BoundingBox(bx, by, bz, bx + 1, by + 1, bz + 1));
                    return block.getCollisionShape().getBoundingBoxes().stream()
                            .map(shape -> shape.clone().shift(bx, by, bz)).toList();
                });
                for (BoundingBox collision : collisions) if (box.overlaps(collision)) return false;
            }
        }
        return true;
    }
}
