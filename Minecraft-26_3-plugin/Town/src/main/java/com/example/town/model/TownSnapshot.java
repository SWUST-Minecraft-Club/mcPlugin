package com.example.town.model;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 小镇的只读快照，供渲染线程 / 异步回调安全读取（持有 registry 锁期间整体构建）。
 */
public record TownSnapshot(
        String id,
        String name,
        UUID mayor,
        TownState state,
        long createdAt,
        int memberCount,
        Map<UUID, Role> members,
        Map<UUID, Long> invites,
        int claimCount,
        Set<ChunkKey> claims,
        Map<String, Long> buffs,
        boolean hallSet,
        boolean spawnSet,
        String hallWorld,
        int hallX,
        int hallY,
        int hallZ,
        String spawnWorld,
        double spawnX,
        double spawnY,
        double spawnZ,
        float spawnYaw,
        float spawnPitch) {

    public static TownSnapshot of(Town town) {
        return new TownSnapshot(
                town.id(),
                town.name(),
                town.mayor(),
                town.state(),
                town.createdAt(),
                town.members().size(),
                new LinkedHashMap<>(town.members()),
                new LinkedHashMap<>(town.invites()),
                town.claims().size(),
                new HashSet<>(town.claims()),
                new LinkedHashMap<>(town.buffs()),
                town.isHallSet(),
                town.isSpawnSet(),
                town.hallWorld(),
                town.hallX(),
                town.hallY(),
                town.hallZ(),
                town.spawnWorld(),
                town.spawnX(),
                town.spawnY(),
                town.spawnZ(),
                town.spawnYaw(),
                town.spawnPitch());
    }

    public List<Map.Entry<UUID, Role>> membersByRole() {
        return members.entrySet().stream()
                .sorted((a, b) -> {
                    int byRole = Integer.compare(a.getValue().ordinal(), b.getValue().ordinal());
                    return byRole != 0 ? byRole : a.getKey().compareTo(b.getKey());
                })
                .toList();
    }
}
