package com.example.mainmenu.internal;

import com.example.mainmenu.MainMenuPlugin;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 功能项管理状态的持久化：{@code plugins/MainMenu/features.yml}。
 *
 * <p>管理界面里改的隐藏 / 排序 / 固定位置 / 位置都会存这里，重启服务端后仍然有效。
 */
public final class FeatureStateStore {

    private static final String FILE_NAME = "features.yml";
    private static final String ROOT = "features";
    private static final String PLACEHOLDER_ROOT = "placeholders";

    private final MainMenuPlugin plugin;
    private final Map<String, FeatureState> states = new LinkedHashMap<>();
    private final Set<String> placeholders = new LinkedHashSet<>();

    public FeatureStateStore(final MainMenuPlugin plugin) {
        this.plugin = plugin;
    }

    /** 取某个功能的状态（不存在时创建空状态，但不落盘） */
    public synchronized FeatureState stateOf(final String id) {
        return states.computeIfAbsent(id, key -> new FeatureState());
    }

    public synchronized void load() {
        states.clear();
        placeholders.clear();
        final File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.isFile()) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (final String id : yaml.getStringList(PLACEHOLDER_ROOT)) {
            if (id != null && !id.isBlank()) {
                placeholders.add(id.trim());
            }
        }
        final ConfigurationSection root = yaml.getConfigurationSection(ROOT);
        if (root == null) {
            return;
        }
        for (final String id : root.getKeys(false)) {
            final ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            final FeatureState state = new FeatureState();
            state.hidden(section.getBoolean("hidden", false));
            state.forcedFixed(section.getBoolean("fixed", false));
            state.forcedFunction(section.getBoolean("function", false));
            if (section.isInt("order")) {
                state.order(section.getInt("order"));
            }
            if (section.isInt("position")) {
                state.position(section.getInt("position"));
            }
            states.put(id, state);
        }
        plugin.getLogger().info("已载入 " + states.size() + " 条功能管理状态（" + FILE_NAME + "）");
    }

    public synchronized void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        if (!placeholders.isEmpty()) {
            yaml.set(PLACEHOLDER_ROOT, new ArrayList<>(placeholders));
        }
        states.forEach((id, state) -> {
            if (state.isEmpty()) {
                return;
            }
            final String path = ROOT + "." + id;
            if (state.hidden()) {
                yaml.set(path + ".hidden", true);
            }
            if (state.forcedFixed()) {
                yaml.set(path + ".fixed", true);
            }
            if (state.forcedFunction()) {
                yaml.set(path + ".function", true);
            }
            if (state.order() != null) {
                yaml.set(path + ".order", state.order());
            }
            if (state.position() != null) {
                yaml.set(path + ".position", state.position());
            }
        });
        try {
            final File folder = plugin.getDataFolder();
            if (!folder.isDirectory() && !folder.mkdirs()) {
                plugin.getLogger().warning("无法创建插件数据目录：" + folder);
                return;
            }
            yaml.save(new File(folder, FILE_NAME));
        } catch (final IOException exception) {
            plugin.getLogger().warning("保存功能管理状态失败：" + exception.getMessage());
        }
    }

    // ------------------------------------------------------------------ 占位符

    /** 已持久化的占位符 id 列表（保持添加顺序）。 */
    public synchronized List<String> placeholders() {
        return List.copyOf(placeholders);
    }

    public synchronized boolean isPlaceholder(final String id) {
        return placeholders.contains(id);
    }

    public synchronized void addPlaceholder(final String id) {
        placeholders.add(id);
    }

    /** 删除占位符定义及其排序覆盖。 */
    public synchronized void removePlaceholder(final String id) {
        placeholders.remove(id);
        states.remove(id);
    }
}
