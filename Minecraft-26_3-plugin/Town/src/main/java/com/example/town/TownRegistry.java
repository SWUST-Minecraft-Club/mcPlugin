package com.example.town;

import com.example.town.model.ChunkKey;
import com.example.town.model.Town;
import com.example.town.model.TownSnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 小镇注册表：全部内存索引 + 一把全局锁。
 * Folia 下不同区域线程都会改小镇状态，单锁串行化是本插件选择的正确性优先方案（数据量小，争用可忽略）。
 * 所有读写必须包在 {@link #guard()} 里；渲染 / 异步侧用 {@link #snapshot(String)} 拿只读快照。
 */
public final class TownRegistry {

    private final ReentrantLock lock = new ReentrantLock();

    private final Map<String, Town> byId = new LinkedHashMap<>();
    private final Map<String, String> idByNameLower = new LinkedHashMap<>();
    private final Map<UUID, String> townIdByMember = new LinkedHashMap<>();
    /** 待处理邀请索引：玩家 -> 小镇 id。 */
    private final Map<UUID, String> townIdByInvited = new LinkedHashMap<>();
    private final Map<ChunkKey, String> townIdByChunk = new LinkedHashMap<>();

    public Guard guard() {
        lock.lock();
        return new Guard(lock);
    }

    public record Guard(ReentrantLock lock) implements AutoCloseable {
        @Override
        public void close() {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------------
    // 索引维护（须持锁调用）
    // ------------------------------------------------------------------

    public void register(Town town) {
        byId.put(town.id(), town);
        idByNameLower.put(town.name().toLowerCase(Locale.ROOT), town.id());
        for (UUID member : town.members().keySet()) {
            townIdByMember.put(member, town.id());
        }
        for (UUID invited : town.invites().keySet()) {
            townIdByInvited.put(invited, town.id());
        }
        for (ChunkKey claim : town.claims()) {
            townIdByChunk.put(claim, town.id());
        }
    }

    /** 从索引摘除小镇并返回（不做持久化，由调用方决定是否落盘）。 */
    public Town unregister(String townId) {
        Town town = byId.remove(townId);
        if (town == null) {
            return null;
        }
        idByNameLower.remove(town.name().toLowerCase(Locale.ROOT));
        townIdByMember.values().removeIf(id -> id.equals(townId));
        townIdByInvited.values().removeIf(id -> id.equals(townId));
        townIdByChunk.values().removeIf(id -> id.equals(townId));
        return town;
    }

    // ------------------------------------------------------------------
    // 查询（须持锁调用；返回的是内部可变对象，禁止在锁外继续改）
    // ------------------------------------------------------------------

    public Town byId(String townId) {
        return townId == null ? null : byId.get(townId);
    }

    public Town byName(String name) {
        String id = idByNameLower.get(name.toLowerCase(Locale.ROOT));
        return id == null ? null : byId.get(id);
    }

    public Town byMember(UUID playerId) {
        String id = townIdByMember.get(playerId);
        return id == null ? null : byId.get(id);
    }

    public Town byInvited(UUID playerId) {
        String id = townIdByInvited.get(playerId);
        return id == null ? null : byId.get(id);
    }

    public Town byChunk(ChunkKey key) {
        String id = townIdByChunk.get(key);
        return id == null ? null : byId.get(id);
    }

    public Collection<Town> all() {
        return byId.values();
    }

    // ------------------------------------------------------------------
    // 动态索引维护（须持锁调用；供 TownService 在增删成员 / 邀请 / 地块时同步）
    // ------------------------------------------------------------------

    public void indexMember(UUID playerId, String townId) {
        townIdByMember.put(playerId, townId);
    }

    public void unindexMember(UUID playerId) {
        townIdByMember.remove(playerId);
    }

    public void indexInvite(UUID playerId, String townId) {
        townIdByInvited.put(playerId, townId);
    }

    public void unindexInvite(UUID playerId) {
        townIdByInvited.remove(playerId);
    }

    public void indexClaim(ChunkKey key, String townId) {
        townIdByChunk.put(key, townId);
    }

    public void unindexClaim(ChunkKey key) {
        townIdByChunk.remove(key);
    }

    // ------------------------------------------------------------------
    // 快照（持锁构建）
    // ------------------------------------------------------------------

    public TownSnapshot snapshot(String townId) {
        try (Guard ignored = guard()) {
            Town town = byId(townId);
            return town == null ? null : TownSnapshot.of(town);
        }
    }

    public TownSnapshot snapshotOfMember(UUID playerId) {
        try (Guard ignored = guard()) {
            Town town = byMember(playerId);
            return town == null ? null : TownSnapshot.of(town);
        }
    }

    /** 该玩家手上的待处理邀请所在小镇（无则 null）。 */
    public TownSnapshot snapshotOfInvited(UUID playerId) {
        try (Guard ignored = guard()) {
            Town town = byInvited(playerId);
            return town == null ? null : TownSnapshot.of(town);
        }
    }

    public TownSnapshot snapshotOfChunk(ChunkKey key) {
        try (Guard ignored = guard()) {
            Town town = byChunk(key);
            return town == null ? null : TownSnapshot.of(town);
        }
    }

    public List<TownSnapshot> snapshots() {
        try (Guard ignored = guard()) {
            List<TownSnapshot> list = new ArrayList<>(byId.size());
            for (Town town : byId.values()) {
                list.add(TownSnapshot.of(town));
            }
            return list;
        }
    }
}
