package com.example.wallet.menu;

import com.example.mainmenu.api.MenuFeature;
import com.example.wallet.WalletConfig;
import com.example.wallet.WalletPlugin;
import com.example.wallet.api.WalletAsset;
import com.example.wallet.api.WalletBalance;
import com.example.wallet.api.WalletException;
import com.example.wallet.api.WalletService;
import com.example.wallet.util.Format;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 主菜单里的钱包功能项（绿宝石图标）。
 *
 * <ul>
 *     <li>普通玩家：悬停逐行显示本服启用货币的余额；点击打开自己的流水页。</li>
 *     <li>管理员（{@code wallet.admin}）：左键打开「玩家货币」页、右键打开「货币类型」页。</li>
 *     <li>本服没有任何启用货币时，图标显示「暂无货币」。</li>
 * </ul>
 *
 * <p>余额全部读 {@link WalletService} 的短时缓存（菜单每秒重绘，绝不能在渲染里发请求）；
 * 缓存由 {@link #refresh(Player)} 按 5 秒节流异步刷新。
 */
public final class WalletFeature implements MenuFeature {

    private final WalletPlugin plugin;
    private final WalletConfig config;
    private final WalletService service;
    private final WalletMenus menus;
    private final Map<UUID, Long> lastRefresh = new ConcurrentHashMap<>();
    private final Map<String, Boolean> unbound = new ConcurrentHashMap<>();

    public WalletFeature(final WalletPlugin plugin, final WalletConfig config, final WalletService service,
                         final WalletMenus menus) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
        this.menus = menus;
    }

    @Override
    public String id() {
        return "wallet:wallet";
    }

    @Override
    public String owner() {
        return "Wallet";
    }

    @Override
    public ItemStack icon() {
        return icon(null);
    }

    @Override
    public ItemStack icon(final Player viewer) {
        final boolean empty = this.menus.enabledAssets().isEmpty();
        return item(Material.EMERALD, Component.text(empty ? "暂无货币" : "钱包", NamedTextColor.WHITE));
    }

    @Override
    public List<Component> description() {
        return List.of(plain("查看货币种类、余额与流水", NamedTextColor.GRAY));
    }

    @Override
    public List<Component> description(final Player viewer) {
        final List<Component> lines = new ArrayList<>();
        if (!this.config.isConfigured()) {
            lines.add(plain("钱包未配置（config.yml 缺 base-url / api-key）", NamedTextColor.RED));
            return lines;
        }

        refresh(viewer);
        final List<WalletAsset> assets = this.menus.enabledAssets();
        if (assets.isEmpty()) {
            lines.add(plain("本服暂无可用货币", NamedTextColor.GRAY));
        } else {
            for (final WalletAsset asset : assets) {
                final WalletBalance balance = this.service.cachedBalance(viewer.getUniqueId(), asset.code());
                final String value;
                if (balance != null) {
                    value = Format.amount(balance.balance(), asset);
                } else if (Boolean.TRUE.equals(this.unbound.get(viewer.getUniqueId() + "|" + asset.code()))) {
                    value = "未绑定网站账号";
                } else {
                    value = "…";
                }
                lines.add(line(asset.name() + "：", value));
            }
        }

        if (viewer.hasPermission("wallet.admin")) {
            lines.add(plain("左键：玩家货币", NamedTextColor.DARK_GRAY));
            lines.add(plain("右键：货币类型", NamedTextColor.DARK_GRAY));
        } else if (!assets.isEmpty()) {
            lines.add(plain("点击查看流水", NamedTextColor.DARK_GRAY));
        }
        return lines;
    }

    @Override
    public boolean clickable() {
        return true;
    }

    @Override
    public void onClick(final Player player, final ClickType clickType) {
        if (!this.config.isConfigured()) {
            player.sendMessage(Component.text("[钱包] 未配置：请在 config.yml 填 base-url 与 api-key。",
                    NamedTextColor.RED));
            return;
        }

        final boolean admin = player.hasPermission("wallet.admin");
        if (admin && clickType.isRightClick()) {
            this.menus.openAssets(player);
            return;
        }
        if (admin && clickType.isLeftClick()) {
            this.menus.openPlayers(player, 1);
            return;
        }
        if (clickType.isLeftClick()) {
            if (this.menus.enabledAssets().isEmpty()) {
                player.sendMessage(Component.text("[钱包] 本服暂无可用货币。", NamedTextColor.GRAY));
                return;
            }
            this.menus.openHistory(player, 1);
        }
    }

    @Override
    public OptionalInt preferredSlot() {
        return OptionalInt.empty();
    }

    @Override
    public int order() {
        return 20;
    }

    /** 异步刷新查看者的余额缓存（5 秒节流；失败只记录下来，不影响渲染）。 */
    private void refresh(final Player viewer) {
        final long now = System.currentTimeMillis();
        final Long last = this.lastRefresh.get(viewer.getUniqueId());
        if (last != null && now - last < 5000L) {
            return;
        }
        this.lastRefresh.put(viewer.getUniqueId(), now);

        this.service.assets().exceptionally(error -> List.of());
        final UUID playerId = viewer.getUniqueId();
        final String playerName = viewer.getName();
        for (final WalletAsset asset : this.menus.enabledAssets()) {
            this.service.balance(playerId, playerName, asset.code())
                    .whenComplete((balance, error) -> {
                        if (error == null) {
                            this.unbound.remove(playerId + "|" + asset.code());
                            return;
                        }
                        if (unboundError(error)) {
                            this.unbound.put(playerId + "|" + asset.code(), Boolean.TRUE);
                        }
                    });
        }
    }

    private static boolean unboundError(final Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof WalletException && current.getMessage() != null
                    && current.getMessage().contains("未绑定")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static Component line(final String label, final String value) {
        return Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(value == null ? "" : value, NamedTextColor.WHITE))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static Component plain(final String text, final NamedTextColor color) {
        return Component.text(text, color)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static ItemStack item(final Material material, final Component name) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            item.setItemMeta(meta);
        }
        return item;
    }
}
