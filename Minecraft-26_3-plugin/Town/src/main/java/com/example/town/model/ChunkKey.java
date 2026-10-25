package com.example.town.model;

import org.bukkit.Chunk;
import org.bukkit.Location;

/**
 * 区块坐标键（世界名 + 区块坐标）。
 * world 用世界名而非 UUID： towns.yml 里可读、可跨重启稳定匹配；世界改名属运维事故，不做兼容。
 */
public record ChunkKey(String world, int x, int z) {

    public static ChunkKey of(Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public static ChunkKey of(Location location) {
        return new ChunkKey(
                location.getWorld().getName(),
                location.getBlockX() >> 4,
                location.getBlockZ() >> 4);
    }

    /** 持久化格式：world:x:z */
    public String encode() {
        return world + ":" + x + ":" + z;
    }

    /** 解析持久化字符串；格式非法返回 null。 */
    public static ChunkKey decode(String raw) {
        String[] parts = raw.split(":");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new ChunkKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 斜角是否也算相邻（diagonal=true 时 8 邻域，否则 4 邻域）。 */
    public boolean isAdjacentTo(ChunkKey other, boolean diagonal) {
        if (!world.equals(other.world)) {
            return false;
        }
        int dx = Math.abs(x - other.x);
        int dz = Math.abs(z - other.z);
        if (diagonal) {
            return Math.max(dx, dz) == 1;
        }
        return (dx == 1 && dz == 0) || (dx == 0 && dz == 1);
    }
}
