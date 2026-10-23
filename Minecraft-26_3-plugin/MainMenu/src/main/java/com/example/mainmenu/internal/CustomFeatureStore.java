package com.example.mainmenu.internal;

import com.example.mainmenu.MainMenuPlugin;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 管理页创建的自定义功能：定义保存在 {@code plugins/MainMenu/custom-features.yml}。
 *
 * <p>文件可以直接手工编辑；改完执行 {@code /cd reload} 会重新加载并同步到功能注册表。
 * 管理状态（显示 / 隐藏、排序覆盖、固定位置）仍存在 {@code features.yml}，不在这里重复。
 */
public final class CustomFeatureStore {

    private static final String FILE_NAME = "custom-features.yml";
    private static final String ROOT = "features";
    private static final String HEADER = """
            # MainMenu 自定义功能
            # 由管理员在管理页第 1 行第 3 格创建，也可以手工追加条目。
            # 每个条目：
            #   name:        功能显示名（纯文本）
            #   icon:        Bukkit 材质名，默认 PAPER
            #   command:     点击执行的指令（不带 /；留空则点击无效）
            #   description: 悬停文本（MiniMessage 列表）
            #   order:       默认排序值（管理页可覆盖）
            # 修改后执行 /cd reload 生效。
            """;

    private final MainMenuPlugin plugin;
    private final Map<String, Definition> definitions = new LinkedHashMap<>();

    public CustomFeatureStore(final MainMenuPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void load() {
        final Map<String, Definition> loaded = new LinkedHashMap<>();
        final File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (file.isFile()) {
            try {
                final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                final ConfigurationSection root = yaml.getConfigurationSection(ROOT);
                if (root != null) {
                    for (final String id : root.getKeys(false)) {
                        if (id == null || id.isBlank()) {
                            continue;
                        }
                        final ConfigurationSection section = root.getConfigurationSection(id);
                        if (section == null) {
                            continue;
                        }
                        loaded.put(id, new Definition(
                                id,
                                section.getString("name", id),
                                section.getString("icon", "PAPER"),
                                section.getString("command", ""),
                                section.getStringList("description"),
                                section.getInt("order", 0)
                        ));
                    }
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("读取自定义功能配置失败：" + ex.getMessage());
                return;
            }
        }
        definitions.clear();
        definitions.putAll(loaded);
        if (!loaded.isEmpty()) {
            plugin.getLogger().info("已载入 " + loaded.size() + " 个自定义功能（" + FILE_NAME + "）");
        }
    }

    public synchronized List<CustomCommandFeature> features() {
        final List<CustomCommandFeature> result = new ArrayList<>(definitions.size());
        for (final Definition definition : definitions.values()) {
            result.add(new CustomCommandFeature(
                    plugin,
                    definition.id(),
                    definition.name(),
                    definition.icon(),
                    definition.command(),
                    definition.description(),
                    definition.order()
            ));
        }
        return List.copyOf(result);
    }

    /**
     * 新建一个自定义功能并落盘。
     *
     * @return 新功能 id；名称为空时返回 {@code null}
     */
    public synchronized String create(final String rawName) {
        String name = rawName == null ? "" : rawName.replace('\r', ' ').replace('\n', ' ').trim();
        if (name.isEmpty()) {
            return null;
        }
        if (name.length() > 64) {
            name = name.substring(0, 64);
        }

        String id;
        do {
            id = "custom:" + UUID.randomUUID().toString().replace("-", "");
        } while (definitions.containsKey(id));

        final List<String> description = List.of(
                "<gray>自定义功能</gray>",
                "<dark_gray>在 custom-features.yml 中配置图标、描述与点击指令</dark_gray>"
        );
        definitions.put(id, new Definition(id, name, "PAPER", "", description, nextOrder()));
        save();
        return id;
    }

    private int nextOrder() {
        int max = 0;
        for (final Definition definition : definitions.values()) {
            max = Math.max(max, definition.order());
        }
        return max > Integer.MAX_VALUE - 10 ? Integer.MAX_VALUE : max + 10;
    }

    private void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Definition definition : definitions.values()) {
            final String path = ROOT + "." + definition.id();
            yaml.set(path + ".name", definition.name());
            yaml.set(path + ".icon", definition.icon());
            yaml.set(path + ".command", definition.command());
            yaml.set(path + ".description", definition.description());
            yaml.set(path + ".order", definition.order());
        }

        final File folder = plugin.getDataFolder();
        if (!folder.isDirectory() && !folder.mkdirs()) {
            plugin.getLogger().warning("无法创建插件数据目录：" + folder);
            return;
        }
        try {
            Files.writeString(new File(folder, FILE_NAME).toPath(),
                    HEADER + yaml.saveToString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存自定义功能配置失败：" + ex.getMessage());
        }
    }

    private record Definition(String id, String name, String icon, String command,
                              List<String> description, int order) {
    }
}
