package com.example.yudreamplaytime;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * 本地在线时长台账的只读视图。
 *
 * <p>数据源是 yudream-minecraft-server 桥接插件写在它自己数据目录里的 {@code playtime.yml}
 * （同一台服务端、同一个 plugins 目录），字段与后端一致：
 * 已结算在线 / 挂机毫秒 + 当前未结算区间起点。这里按文件修改时间做缓存，避免每次渲染都解析 YAML。
 */
public final class PlaytimeStats {

    private final Plugin plugin;
    private final File file;
    private final long cacheMillis;

    private Map<String, Record> records = Collections.emptyMap();
    private long refreshedAt;
    private long loadedStamp = -1L;

    public PlaytimeStats(final Plugin plugin, final File file, final long cacheMillis) {
        this.plugin = plugin;
        this.file = file;
        this.cacheMillis = cacheMillis;
    }

    public File file() {
        return file;
    }

    /** 查一个玩家的本地记录；没有记录时返回 {@code null}。 */
    public synchronized Record find(final UUID playerId) {
        refresh();
        return records.get(playerId.toString());
    }

    /** 台账里一共有多少条记录（用于 /cd debug 之外的排查）。 */
    public synchronized int size() {
        refresh();
        return records.size();
    }

    private void refresh() {
        final long now = System.currentTimeMillis();
        if (now - refreshedAt < cacheMillis) {
            return;
        }
        refreshedAt = now;

        if (!file.isFile()) {
            records = Collections.emptyMap();
            loadedStamp = -1L;
            return;
        }

        final long stamp = file.lastModified();
        if (stamp == loadedStamp) {
            return;
        }

        try {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            final ConfigurationSection players = yaml.getConfigurationSection("players");
            final Map<String, Record> loaded = new HashMap<>();
            if (players != null) {
                for (final String playerId : players.getKeys(false)) {
                    final ConfigurationSection section = players.getConfigurationSection(playerId);
                    if (section == null) {
                        continue;
                    }
                    loaded.put(playerId, new Record(
                            section.getString("name", ""),
                            section.getLong("online-millis", 0L),
                            section.getLong("afk-millis", 0L),
                            section.getLong("current-online-since", 0L),
                            section.getLong("current-afk-since", 0L)
                    ));
                }
            }
            records = loaded;
            loadedStamp = stamp;
        } catch (final RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "读取本地时长台账失败：" + file, e);
        }
    }

    /** 一个玩家的本地时长快照。 */
    public static final class Record {

        private final String name;
        private final long onlineMillis;
        private final long afkMillis;
        private final long currentOnlineSince;
        private final long currentAfkSince;

        Record(final String name, final long onlineMillis, final long afkMillis,
               final long currentOnlineSince, final long currentAfkSince) {
            this.name = name;
            this.onlineMillis = onlineMillis;
            this.afkMillis = afkMillis;
            this.currentOnlineSince = currentOnlineSince;
            this.currentAfkSince = currentAfkSince;
        }

        public String name() {
            return name;
        }

        public boolean online() {
            return currentOnlineSince > 0L;
        }

        /** 含当前未结算区间的在线时长（与后端同一算法）。 */
        public long onlineMillisAt(final long now) {
            return onlineMillis + duration(currentOnlineSince, now);
        }

        /** 含当前未结算区间的挂机时长。 */
        public long afkMillisAt(final long now) {
            return afkMillis + duration(currentAfkSince, now);
        }

        private static long duration(final long startAt, final long endAt) {
            if (startAt <= 0L || endAt <= startAt) {
                return 0L;
            }
            return endAt - startAt;
        }
    }
}
