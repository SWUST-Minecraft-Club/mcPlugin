package com.example.town.store;

import com.example.town.TownPlugin;
import com.example.town.TownRegistry;
import com.example.town.model.ChunkKey;
import com.example.town.model.Role;
import com.example.town.model.Town;
import com.example.town.model.TownState;
import com.example.town.util.Threads;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 小镇持久化：towns.yml（原子写：临时文件 + move）。
 * 读在启动期同步完成；写在锁内构建 YamlConfiguration，然后丢到异步线程落盘。
 */
public final class TownStore {

    private final TownPlugin plugin;
    private final Path file;
    private final Object fileIoLock = new Object();
    /** 在 registry 锁内递增，序号顺序即快照的数据顺序。 */
    private long nextSnapshotSequence;
    /** 仅在 fileIoLock 内读写。 */
    private long lastWrittenSequence;

    private record SaveSnapshot(long sequence, YamlConfiguration yml) {
    }

    public TownStore(TownPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("towns.yml");
    }

    /** 启动期同步加载（不持锁；registry 尚未对外）。 */
    public List<Town> load() {
        List<Town> result = new ArrayList<>();
        if (!Files.exists(file)) {
            return result;
        }
        YamlConfiguration yml = new YamlConfiguration();
        try { yml.load(file.toFile()); }
        catch(IOException|org.bukkit.configuration.InvalidConfigurationException error) {
            throw new IllegalStateException("towns.yml 无法读取，停止加载以保留原数据",error);
        }
        ConfigurationSection towns = yml.getConfigurationSection("towns");
        if (towns == null) {
            return result;
        }
        for (String id : towns.getKeys(false)) {
            ConfigurationSection s = towns.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            try {
                result.add(parseTown(id, s));
            } catch (Exception e) {
                throw new IllegalStateException("小镇 " + id + " 数据损坏，停止加载以保留原数据",e);
            }
        }
        plugin.getLogger().info("已加载 " + result.size() + " 个小镇。");
        return result;
    }

    private Town parseTown(String id, ConfigurationSection s) {
        String mayorRaw = s.getString("mayor", "");
        Town town = new Town(
                id,
                s.getString("name", id),
                UUID.fromString(mayorRaw),
                s.getLong("created", System.currentTimeMillis()));
        town.state(TownState.valueOf(s.getString("state", "PENDING")));

        ConfigurationSection members = s.getConfigurationSection("members");
        if (members != null) {
            for (String memberKey : members.getKeys(false)) {
                Role role;
                try {
                    role = Role.valueOf(members.getString(memberKey, "MEMBER"));
                } catch (IllegalArgumentException e) {
                    role = Role.MEMBER;
                }
                town.members().put(UUID.fromString(memberKey), role);
            }
        }
        for (Map.Entry<UUID, Role> member : town.members().entrySet()) {
            if (member.getValue() == Role.MAYOR && !member.getKey().equals(town.mayor())) {
                member.setValue(Role.OFFICER);
            }
        }
        town.members().put(town.mayor(), Role.MAYOR);

        ConfigurationSection invites = s.getConfigurationSection("invites");
        if (invites != null) {
            long now = System.currentTimeMillis();
            for (String invitedKey : invites.getKeys(false)) {
                long expire = invites.getLong(invitedKey, 0);
                if (expire > now) {
                    town.invites().put(UUID.fromString(invitedKey), expire);
                }
            }
        }

        for (String raw : s.getStringList("claims")) {
            ChunkKey key = ChunkKey.decode(raw);
            if (key != null) {
                town.claims().add(key);
            }
        }

        ConfigurationSection buffs = s.getConfigurationSection("buffs");
        if (buffs != null) {
            for (String buffId : buffs.getKeys(false)) {
                town.buffs().put(buffId, buffs.getLong(buffId, 0));
            }
        }

        if (s.getBoolean("hall.set", false)) {
            String hallWorld = s.getString("hall.world", "");
            int hx = s.getInt("hall.x"), hy = s.getInt("hall.y"), hz = s.getInt("hall.z");
            float yaw = (float) s.getDouble("hall.yaw");
            float pitch = (float) s.getDouble("hall.pitch");
            town.hall(hallWorld, hx, hy, hz, yaw, pitch);
        }
        if (s.getBoolean("spawn.set", false)) {
            town.spawn(
                    s.getString("spawn.world", ""),
                    s.getDouble("spawn.x"), s.getDouble("spawn.y"), s.getDouble("spawn.z"),
                    (float) s.getDouble("spawn.yaw"), (float) s.getDouble("spawn.pitch"));
        }
        // 旧版本已经自动赠地的城镇升级时额度为零，拆放核心也不能再次领取。
        town.foundingClaimsGranted(s.getBoolean("founding-claims-granted", town.isHallSet() || !town.claims().isEmpty()));
        town.freeClaimsRemaining(Math.max(0,s.getInt("free-claims-remaining",0)));
        for (Map<?,?> raw : s.getMapList("plots")) {
            var plot=PlotCodec.decode(raw);
            if (town.plots().putIfAbsent(plot.id(),plot)!=null) throw new IllegalArgumentException("重复地块 ID "+plot.id());
        }
        town.civic(CivicCodec.decode(s.getConfigurationSection("civic")));
        return town;
    }

    /** 在 registry 锁内构建独立快照并分配递增序号。 */
    private SaveSnapshot snapshot(TownRegistry registry) {
        YamlConfiguration yml = new YamlConfiguration();
        try (TownRegistry.Guard ignored = registry.guard()) {
            for (Town town : registry.all()) {
                yml.set("towns." + town.id() + ".name", town.name());
                yml.set("towns." + town.id() + ".mayor", town.mayor().toString());
                yml.set("towns." + town.id() + ".created", town.createdAt());
                yml.set("towns." + town.id() + ".state", town.state().name());
                yml.set("towns." + town.id() + ".founding-claims-granted", town.foundingClaimsGranted());
                yml.set("towns." + town.id() + ".free-claims-remaining", town.freeClaimsRemaining());
                yml.set("towns." + town.id() + ".plots", town.plots().values().stream()
                        .sorted(java.util.Comparator.comparing(com.example.town.model.Plot::id))
                        .map(PlotCodec::encode).toList());
                yml.set("towns." + town.id() + ".civic", CivicCodec.encode(town.civic()));
                for (Map.Entry<UUID, Role> e : town.members().entrySet()) {
                    yml.set("towns." + town.id() + ".members." + e.getKey(), e.getValue().name());
                }
                for (Map.Entry<UUID, Long> e : town.invites().entrySet()) {
                    yml.set("towns." + town.id() + ".invites." + e.getKey(), e.getValue());
                }
                List<String> claims = new ArrayList<>(town.claims().size());
                for (ChunkKey key : town.claims()) {
                    claims.add(key.encode());
                }
                yml.set("towns." + town.id() + ".claims", claims);
                for (Map.Entry<String, Long> e : town.buffs().entrySet()) {
                    yml.set("towns." + town.id() + ".buffs." + e.getKey(), e.getValue());
                }
                yml.set("towns." + town.id() + ".hall.set", town.isHallSet());
                if (town.isHallSet()) {
                    yml.set("towns." + town.id() + ".hall.world", town.hallWorld());
                    yml.set("towns." + town.id() + ".hall.x", town.hallX());
                    yml.set("towns." + town.id() + ".hall.y", town.hallY());
                    yml.set("towns." + town.id() + ".hall.z", town.hallZ());
                }
                yml.set("towns." + town.id() + ".spawn.set", town.isSpawnSet());
                if (town.isSpawnSet()) {
                    yml.set("towns." + town.id() + ".spawn.world", town.spawnWorld());
                    yml.set("towns." + town.id() + ".spawn.x", town.spawnX());
                    yml.set("towns." + town.id() + ".spawn.y", town.spawnY());
                    yml.set("towns." + town.id() + ".spawn.z", town.spawnZ());
                    yml.set("towns." + town.id() + ".spawn.yaw", town.spawnYaw());
                    yml.set("towns." + town.id() + ".spawn.pitch", town.spawnPitch());
                }
            }
            return new SaveSnapshot(++nextSnapshotSequence, yml);
        }
    }

    /** 同步写入最新快照；若已有异步 IO，会先等待其完成。 */
    public void saveAll(TownRegistry registry) {
        writeSnapshot(snapshot(registry));
    }

    /** 金融操作在 IO 线程调用，失败抛出异常；调用方回滚内存并保留待恢复支付。 */
    public void saveChecked(TownRegistry registry) throws IOException {
        SaveSnapshot snapshot=snapshot(registry);
        synchronized(fileIoLock) {
            if(snapshot.sequence()<=lastWrittenSequence) return;
            AtomicFiles.write(file,snapshot.yml().saveToString().getBytes(StandardCharsets.UTF_8));
            lastWrittenSequence=snapshot.sequence();
        }
    }

    /** 快照在调用时固定，仅文件 IO 交给异步线程。 */
    public void saveAllAsync(TownRegistry registry) {
        SaveSnapshot snapshot = snapshot(registry);
        Threads.async(plugin, () -> writeSnapshot(snapshot));
    }

    private void writeSnapshot(SaveSnapshot snapshot) {
        synchronized (fileIoLock) {
            if (snapshot.sequence() <= lastWrittenSequence) {
                return;
            }
            if (atomicWrite(snapshot.yml())) lastWrittenSequence = snapshot.sequence();
        }
    }

    private boolean atomicWrite(YamlConfiguration yml) {
        try {
            AtomicFiles.write(file,yml.saveToString().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "towns.yml 写入失败：" + e.getMessage(), e);
            return false;
        }
    }
}
