package com.example.town.model;

import com.example.town.model.ChunkKey;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 面向界面 / 外部 API 的小镇只读视图，包装 {@link TownSnapshot} 提供便捷访问。
 * 一律只读；数据来自快照，跨线程安全。
 */
public final class TownSnapshotView {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private final TownSnapshot snapshot;

    private TownSnapshotView(TownSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public static TownSnapshotView of(TownSnapshot snapshot) {
        return snapshot == null ? null : new TownSnapshotView(snapshot);
    }

    public TownSnapshot raw() {
        return snapshot;
    }

    public String id() {
        return snapshot.id();
    }

    public String name() {
        return snapshot.name();
    }

    public UUID mayor() {
        return snapshot.mayor();
    }

    public boolean active() {
        return snapshot.state() == TownState.ACTIVE;
    }

    public TownState state() {
        return snapshot.state();
    }

    public long createdAt() {
        return snapshot.createdAt();
    }

    public String createdDate() {
        return DATE.format(Instant.ofEpochMilli(snapshot.createdAt()));
    }

    public int memberCount() {
        return snapshot.memberCount();
    }

    public Map<UUID, Role> members() {
        return snapshot.members();
    }

    public Map<UUID, Long> invites() {
        return snapshot.invites();
    }

    public int claimCount() {
        return snapshot.claimCount();
    }

    public Set<ChunkKey> claims() {
        return snapshot.claims();
    }

    /** 镇政厅所在区块（未放置返回 null）。 */
    public ChunkKey hallChunk() {
        if (!snapshot.hallSet() || snapshot.hallWorld() == null) {
            return null;
        }
        return new ChunkKey(snapshot.hallWorld(),
                snapshot.hallX() >> 4, snapshot.hallZ() >> 4);
    }

    public Map<String, Long> buffs() {
        return snapshot.buffs();
    }

    public boolean hallSet() {
        return snapshot.hallSet();
    }

    public boolean spawnSet() {
        return snapshot.spawnSet();
    }

    public String spawnWorld() {
        return snapshot.spawnWorld();
    }

    public double spawnX() {
        return snapshot.spawnX();
    }

    public double spawnY() {
        return snapshot.spawnY();
    }

    public double spawnZ() {
        return snapshot.spawnZ();
    }

    public float spawnYaw() {
        return snapshot.spawnYaw();
    }

    public float spawnPitch() {
        return snapshot.spawnPitch();
    }
}
