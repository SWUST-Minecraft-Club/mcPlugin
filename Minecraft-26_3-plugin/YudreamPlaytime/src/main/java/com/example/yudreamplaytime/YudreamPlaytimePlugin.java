package com.example.yudreamplaytime;

import com.example.mainmenu.api.MainMenuApi;
import java.io.File;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * YudreamPlaytime：把「查看者自己的在线时长」挂进 MainMenu。
 *
 * <p>数据来自同机 yudream-minecraft-server 桥接插件的本地时长台账
 * （{@code <服务端目录>/plugins/YudreamMinecraftServer/playtime.yml}）。
 * 该台账与 YuDream Admin 后端同口径，后端不可用时也照常记账，所以菜单里的数字不依赖后端。
 */
public final class YudreamPlaytimePlugin extends JavaPlugin {

    private static final String BRIDGE_DATA_FOLDER = "YudreamMinecraftServer";
    private static final String LEDGER_FILE = "playtime.yml";

    private PlaytimeStats stats;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        final long cacheMillis = Math.max(getConfig().getLong("cache-seconds", 3L), 1L) * 1000L;
        final int order = getConfig().getInt("order", 10);
        stats = new PlaytimeStats(this, resolveStatsFile(), cacheMillis);

        if (!MainMenuApi.isAvailable()) {
            getLogger().warning("MainMenu 未启用：菜单功能未注册（装好 MainMenu 后重启或 /cd reload 即可）。");
            return;
        }

        MainMenuApi.registry().register(new PlaytimeFeature(stats, order));
        getLogger().info("已注册主菜单功能：我的在线时长（台账 " + stats.file().getPath()
                + "，记录数 " + stats.size() + "，缓存 " + cacheMillis + "ms）。");
    }

    @Override
    public void onDisable() {
        if (MainMenuApi.isAvailable()) {
            try {
                MainMenuApi.registryOrNull().unregister("yudreamplaytime:my-playtime");
            } catch (RuntimeException e) {
                getLogger().log(Level.FINE, "注销主菜单功能时出错（可忽略）", e);
            }
        }
    }

    private File resolveStatsFile() {
        final String configured = getConfig().getString("stats-file", "");
        if (configured != null && !configured.trim().isEmpty()) {
            return new File(configured.trim());
        }

        return new File(new File(getDataFolder().getParentFile(), BRIDGE_DATA_FOLDER), LEDGER_FILE);
    }
}
