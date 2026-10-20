package com.example.wallet.internal;

import com.example.wallet.api.WalletAsset;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * 本服货币启停的持久化开关（管理员在「货币类型」页点击羊毛切换）。
 *
 * <p>{@code config.yml} 的 {@code assets} 只是**初始**启用列表；游戏内切换后写到插件数据目录的
 * {@code assets.yml}（优先于 config，避免改 config 时把注释写丢）。
 * 无论本地怎么开，官网 {@code enabled=false} 的货币都不生效。
 */
public final class AssetSwitchStore {

    private static final String FILE_NAME = "assets.yml";

    private final File file;
    private final List<String> defaults;
    private Set<String> explicit;

    public AssetSwitchStore(final Plugin plugin, final Collection<String> defaults) {
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
        this.defaults = defaults == null ? List.of() : List.copyOf(defaults);
        this.load();
    }

    /** 当前生效的启用货币 code（按官网顺序，只保留官网启用的）。 */
    public synchronized List<String> effective(final List<WalletAsset> assets) {
        final Set<String> enabled = this.enabledSet(assets);
        final List<String> result = new ArrayList<>();
        for (final WalletAsset asset : assets) {
            if (asset.enabled() && enabled.contains(asset.code().toLowerCase())) {
                result.add(asset.code());
            }
        }
        return result;
    }

    /** 本服是否启用该货币（只看本地开关，不再判断官网状态）。 */
    public synchronized boolean isEnabled(final String code, final List<WalletAsset> assets) {
        return code != null && this.enabledSet(assets).contains(code.toLowerCase());
    }

    /** 切换本服启用状态，返回切换后的状态。 */
    public synchronized boolean toggle(final String code, final List<WalletAsset> assets) {
        final Set<String> enabled = new HashSet<>(this.enabledSet(assets));
        final String key = code == null ? "" : code.toLowerCase();
        final boolean nowEnabled;
        if (enabled.contains(key)) {
            enabled.remove(key);
            nowEnabled = false;
        } else {
            enabled.add(key);
            nowEnabled = true;
        }
        this.explicit = enabled;
        this.save();
        return nowEnabled;
    }

    private Set<String> enabledSet(final List<WalletAsset> assets) {
        if (this.explicit != null) {
            return new HashSet<>(this.explicit);
        }

        if (!this.defaults.isEmpty()) {
            final Set<String> result = new HashSet<>();
            for (final String code : this.defaults) {
                result.add(code.toLowerCase());
            }
            return result;
        }

        // 没配初始列表：官网启用的全部算本服启用
        final Set<String> result = new LinkedHashSet<>();
        for (final WalletAsset asset : assets) {
            if (asset.enabled()) {
                result.add(asset.code().toLowerCase());
            }
        }
        return result;
    }

    private void load() {
        if (!this.file.isFile()) {
            return;
        }

        try {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
            final Set<String> set = new HashSet<>();
            for (final String code : yaml.getStringList("enabled")) {
                if (code != null && !code.trim().isEmpty()) {
                    set.add(code.trim().toLowerCase());
                }
            }
            this.explicit = set;
        } catch (RuntimeException ignored) {
            // 读不了就用配置里的初始列表
        }
    }

    private void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", this.explicit == null ? List.of() : new ArrayList<>(this.explicit));

        final Path target = this.file.toPath();
        final Path parent = target.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            final Path temp = parent == null ? target.resolveSibling(FILE_NAME + ".tmp")
                    : parent.resolve(FILE_NAME + ".tmp");
            Files.write(temp, yaml.saveToString().getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // 保存失败只影响下次启动时的初始状态
        }
    }
}
