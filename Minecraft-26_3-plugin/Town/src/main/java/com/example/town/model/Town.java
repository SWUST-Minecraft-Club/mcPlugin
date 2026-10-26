package com.example.town.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 小镇实体。所有字段必须在持有 {@code TownRegistry#guard()} 期间读写；
 * 单实例锁保证 Folia 多区域线程下的正确性，读取侧用 registry 提供的快照。
 */
public final class Town {

    private final String id;
    private final long createdAt;
    private String name;
    private UUID mayor;
    private TownState state;

    private final Map<UUID, Role> members = new HashMap<>();
    /** 待处理邀请：玩家 -> 过期时间（epoch millis）。 */
    private final Map<UUID, Long> invites = new HashMap<>();
    private final Set<ChunkKey> claims = new HashSet<>();
    /** 生效中的 buff：buffId -> 过期时间（epoch millis；-1 = 永久）。 */
    private final Map<String, Long> buffs = new HashMap<>();
    private final Map<String, Plot> plots = new HashMap<>();
    private int freeClaimsRemaining;
    private boolean foundingClaimsGranted;
    private com.example.town.economy.CivicState civic = com.example.town.economy.CivicState.empty();

    /** 镇政厅方块位置（放置后非空）。 */
    private ChunkKey hallChunk;
    private String hallWorld;
    private int hallX;
    private int hallY;
    private int hallZ;
    private boolean hallSet;

    /** 小镇传送点（放镇政厅时以方块上方为准）。 */
    private String spawnWorld;
    private double spawnX;
    private double spawnY;
    private double spawnZ;
    private float spawnYaw;
    private float spawnPitch;
    private boolean spawnSet;

    public Town(String id, String name, UUID mayor, long createdAt) {
        this.id = id;
        this.name = name;
        this.mayor = mayor;
        this.createdAt = createdAt;
        this.state = TownState.PENDING;
        this.members.put(mayor, Role.MAYOR);
    }

    public String id() {
        return id;
    }

    public long createdAt() {
        return createdAt;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public UUID mayor() {
        return mayor;
    }

    public void mayor(UUID mayor) {
        UUID previous = this.mayor;
        if (previous != null && !previous.equals(mayor)) {
            this.members.put(previous, Role.OFFICER);
        }
        this.mayor = mayor;
        this.members.put(mayor, Role.MAYOR);
    }

    public TownState state() {
        return state;
    }

    public void state(TownState state) {
        this.state = state;
    }

    public Map<UUID, Role> members() {
        return members;
    }

    public Map<UUID, Long> invites() {
        return invites;
    }

    public Set<ChunkKey> claims() {
        return claims;
    }

    public Map<String, Long> buffs() {
        return buffs;
    }

    public Map<String, Plot> plots() { return plots; }
    public com.example.town.economy.CivicState civic() { return civic; }
    public void civic(com.example.town.economy.CivicState civic) { this.civic=java.util.Objects.requireNonNull(civic); }
    public int freeClaimsRemaining() { return freeClaimsRemaining; }
    public void freeClaimsRemaining(int count) {
        if (count < 0) throw new IllegalArgumentException("赠地区块额度不能为负");
        freeClaimsRemaining=count;
    }
    public boolean foundingClaimsGranted() { return foundingClaimsGranted; }
    public void foundingClaimsGranted(boolean granted) { foundingClaimsGranted=granted; }

    /** 额度只在首次登记核心时发放；改变配置或重放核心不重置已消费额度。 */
    public int grantFoundingClaims(int total, int maximum) {
        if (foundingClaimsGranted) return 0;
        int allowance = maximum > 0 ? Math.min(total, maximum) : total;
        freeClaimsRemaining = Math.max(0, allowance - claims.size());
        foundingClaimsGranted = true;
        return freeClaimsRemaining;
    }

    public boolean isHallSet() {
        return hallSet;
    }

    public ChunkKey hallChunk() {
        return hallChunk;
    }

    public String hallWorld() {
        return hallWorld;
    }

    public int hallX() {
        return hallX;
    }

    public int hallY() {
        return hallY;
    }

    public int hallZ() {
        return hallZ;
    }

    /** 设置镇政厅方块与传送点（传送点 = 方块上方一格中心，朝向取放置者的反向）。 */
    public void hall(String world, int x, int y, int z, float yaw, float pitch) {
        this.hallWorld = world;
        this.hallX = x;
        this.hallY = y;
        this.hallZ = z;
        this.hallSet = true;
        this.hallChunk = new ChunkKey(world, x >> 4, z >> 4);
        this.spawnWorld = world;
        this.spawnX = x + 0.5;
        this.spawnY = y + 1.0;
        this.spawnZ = z + 0.5;
        this.spawnYaw = yaw;
        this.spawnPitch = pitch;
        this.spawnSet = true;
    }

    public void clearHall() {
        this.hallSet = false;
        this.hallChunk = null;
        this.hallWorld = null;
        // 传送点保留：镇政厅被拆但领地还在，出生点继续可用
    }

    public boolean isSpawnSet() {
        return spawnSet;
    }

    public String spawnWorld() {
        return spawnWorld;
    }

    public double spawnX() {
        return spawnX;
    }

    public double spawnY() {
        return spawnY;
    }

    public double spawnZ() {
        return spawnZ;
    }

    public float spawnYaw() {
        return spawnYaw;
    }

    public float spawnPitch() {
        return spawnPitch;
    }

    public void spawn(String world, double x, double y, double z, float yaw, float pitch) {
        this.spawnWorld = world;
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
        this.spawnYaw = yaw;
        this.spawnPitch = pitch;
        this.spawnSet = true;
    }
}
