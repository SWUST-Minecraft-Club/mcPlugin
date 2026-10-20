package com.example.wallet.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * 读取 yudream-minecraft-server 写的本地在线时长台账（{@code plugins/YudreamMinecraftServer/playtime.yml}）。
 *
 * <p>用途：管理员的「玩家货币」页只列出**在本服有过在线时长记录**的玩家。
 * 台账格式是稳定接口（该插件 README 里有说明），这里只读 {@code name / online-millis / current-online-since}。
 */
public final class PlaytimeLedgerReader {

    private final File file;
    private List<Entry> cache = List.of();
    private long stamp = -1L;
    private long readAt;

    public PlaytimeLedgerReader(final Plugin plugin) {
        this.file = new File(new File(plugin.getDataFolder().getParentFile(), "YudreamMinecraftServer"),
                "playtime.yml");
    }

    public File file() {
        return this.file;
    }

    /** 按本服在线时长倒序返回；文件不存在或读不了时返回上一次的缓存。 */
    public synchronized List<Entry> read() {
        if (!this.file.isFile()) {
            this.cache = List.of();
            this.stamp = -1L;
            return this.cache;
        }

        final long mtime = this.file.lastModified();
        if (mtime == this.stamp && System.currentTimeMillis() - this.readAt < 5000L) {
            return this.cache;
        }
        this.stamp = mtime;
        this.readAt = System.currentTimeMillis();

        try {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
            final ConfigurationSection players = yaml.getConfigurationSection("players");
            if (players == null) {
                this.cache = List.of();
                return this.cache;
            }

            final long now = System.currentTimeMillis();
            final List<Entry> entries = new ArrayList<>();
            for (final String key : players.getKeys(false)) {
                final ConfigurationSection section = players.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }

                UUID playerId;
                try {
                    playerId = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                long online = section.getLong("online-millis", 0L);
                final long openSince = section.getLong("current-online-since", 0L);
                if (openSince > 0L && now > openSince) {
                    online += now - openSince;
                }

                entries.add(new Entry(playerId, section.getString("name", ""), Math.max(online, 0L)));
            }

            entries.sort(Comparator.comparingLong(Entry::onlineMillis).reversed());
            this.cache = List.copyOf(entries);
            return this.cache;
        } catch (RuntimeException e) {
            return this.cache;
        }
    }

    /** 台账里的一条记录。 */
    public record Entry(UUID playerId, String name, long onlineMillis) {
    }
}
