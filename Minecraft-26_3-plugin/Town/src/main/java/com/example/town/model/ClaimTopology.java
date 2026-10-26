package com.example.town.model;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** 领土以政厅为根的连通性校验，不读取世界，供变更前校验与测试共用。 */
public final class ClaimTopology {
    private ClaimTopology() { }

    public static boolean connected(Set<ChunkKey> claims, ChunkKey root, boolean diagonal) {
        if (root == null || !claims.contains(root)) return false;
        Set<ChunkKey> remaining = new HashSet<>(claims);
        ArrayDeque<ChunkKey> queue = new ArrayDeque<>();
        remaining.remove(root);
        queue.add(root);
        while (!queue.isEmpty()) {
            ChunkKey current = queue.removeFirst();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx == 0 && dz == 0) || (!diagonal && Math.abs(dx) + Math.abs(dz) != 1)) continue;
                    ChunkKey next = new ChunkKey(current.world(), current.x() + dx, current.z() + dz);
                    if (remaining.remove(next)) queue.addLast(next);
                }
            }
        }
        return remaining.isEmpty();
    }
}
