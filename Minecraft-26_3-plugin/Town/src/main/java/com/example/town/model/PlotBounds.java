package com.example.town.model;

import java.util.HashSet;
import java.util.Set;

/** 闭区间矩形。负坐标按 Minecraft 的向下取整区块规则计算；面积使用 long。 */
public record PlotBounds(String world, int minX, int minZ, int maxX, int maxZ) {
    public PlotBounds {
        if (world == null || world.isBlank() || minX > maxX || minZ > maxZ)
            throw new IllegalArgumentException("选区必须是同一世界内的有效矩形");
        if (minX < -30_000_000 || minZ < -30_000_000 || maxX > 30_000_000 || maxZ > 30_000_000)
            throw new IllegalArgumentException("选区超出世界坐标范围");
    }

    public static PlotBounds between(String world, int ax, int az, int bx, int bz) {
        return new PlotBounds(world, Math.min(ax,bx), Math.min(az,bz), Math.max(ax,bx), Math.max(az,bz));
    }

    public long area() { return ((long)maxX-minX+1) * ((long)maxZ-minZ+1); }
    public boolean contains(String world, int x, int z) {
        return this.world.equals(world) && x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
    public boolean contains(PlotBounds other) {
        return world.equals(other.world) && minX <= other.minX && minZ <= other.minZ && maxX >= other.maxX && maxZ >= other.maxZ;
    }
    public boolean overlaps(PlotBounds other) {
        return world.equals(other.world) && minX <= other.maxX && maxX >= other.minX && minZ <= other.maxZ && maxZ >= other.minZ;
    }
    public PlotBounds translate(int dx, int dz) {
        return new PlotBounds(world, Math.addExact(minX,dx), Math.addExact(minZ,dz), Math.addExact(maxX,dx), Math.addExact(maxZ,dz));
    }
    public Set<ChunkKey> chunks(int limit) {
        int x1 = minX >> 4, x2 = maxX >> 4, z1 = minZ >> 4, z2 = maxZ >> 4;
        long count = ((long)x2-x1+1) * ((long)z2-z1+1);
        if (count > limit) throw new IllegalArgumentException("选区覆盖区块过多");
        Set<ChunkKey> result = new HashSet<>();
        for (int x=x1; x<=x2; x++) for (int z=z1; z<=z2; z++) result.add(new ChunkKey(world,x,z));
        return Set.copyOf(result);
    }
}
