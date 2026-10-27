package com.example.wallet.menu;

import com.example.wallet.WalletConfig;
import com.example.wallet.WalletPlugin;
import com.example.wallet.api.WalletAsset;
import com.example.wallet.api.WalletBalance;
import com.example.wallet.api.WalletException;
import com.example.wallet.api.WalletService;
import com.example.wallet.api.WalletTransaction;
import com.example.wallet.api.WalletTransactionPage;
import com.example.wallet.internal.PlaytimeLedgerReader;
import com.example.wallet.util.Format;
import com.example.wallet.util.PlayerTasks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Wallet 的三个界面：玩家流水页、货币类型页（管理员）、玩家货币页（管理员）。
 *
 * <p>布局与 MainMenu 一致：第 1~5 行放内容，第 6 行 <b>47 上一页 / 49 返回 / 51 下一页</b>。
 * 所有数据都是异步拉取，界面先开再填，避免卡主线程。
 */
public final class WalletMenus {

    private static final int CONTENT_SLOTS = 45;
    private static final int SLOT_PREVIOUS = 47;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_NEXT = 51;
    private static final int SLOT_CENTER = 22;

    private final WalletPlugin plugin;
    private final WalletConfig config;
    private final WalletService service;
    private final PlaytimeLedgerReader ledger;
    private final com.example.wallet.internal.AssetSwitchStore switches;

    public WalletMenus(final WalletPlugin plugin, final WalletConfig config, final WalletService service,
                       final PlaytimeLedgerReader ledger,
                       final com.example.wallet.internal.AssetSwitchStore switches) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
        this.ledger = ledger;
        this.switches = switches;
    }

    /** 本服启用且对应来源可用的货币（管理员开关优先取 assets.yml）。 */
    public List<WalletAsset> enabledAssets() {
        final List<String> codes = enabledCodes();
        final List<WalletAsset> result = new ArrayList<>();
        for (final WalletAsset asset : this.service.cachedAssets()) {
            if (codes.contains(asset.code())) {
                result.add(asset);
            }
        }
        return result;
    }

    /** 本服启用的货币 code（流水页按它过滤：停用的币种不显示流水）。 */
    public List<String> enabledCodes() {
        return this.switches.effective(this.service.cachedAssets());
    }

    public void clickLater(Player player, WalletHolder holder, Runnable action) {
        player.getScheduler().execute(plugin, () -> {
            if (viewing(player, holder)) action.run();
        }, null, 1);
    }

    /** 返回主菜单；未安装 MainMenu 时关闭钱包。点击入口已延后到下一 tick。 */
    public void back(final Player player) {
        PlayerTasks.run(this.plugin, player, () -> {
            final var mainMenu = Bukkit.getPluginManager().getPlugin("MainMenu");
            if (mainMenu == null || !mainMenu.isEnabled()) {
                player.closeInventory();
                return;
            }
            boolean opened = false;
            try {
                opened = (boolean) Class.forName("com.example.mainmenu.api.MainMenuApi")
                        .getMethod("openOrNull", Player.class).invoke(null, player);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // 运行期是旧版 MainMenu（没有 MainMenuApi.open）时会抛 LinkageError，退回命令
            }
            if (!opened) {
                Bukkit.dispatchCommand(player, "cd");
            }
        });
    }

    /** 管理员点击「货币类型」页里的羊毛：切换本服启用状态。 */
    public void toggleAsset(final Player player, final WalletHolder holder, final int slot) {
        if (!player.hasPermission("wallet.admin")) {
            return;
        }

        final List<WalletAsset> assets = this.service.cachedAssets();
        final int position = (holder.page() - 1) * CONTENT_SLOTS + slot;
        if (slot < 0 || slot >= CONTENT_SLOTS || position >= assets.size()) {
            return;
        }

        final WalletAsset asset = assets.get(position);
        if (!asset.enabled()) {
            player.sendMessage(Component.text("[钱包] 配置已停用该货币，无法在本服启用。", NamedTextColor.RED));
            return;
        }

        final boolean enabled = this.switches.toggle(asset.code(), assets);
        player.sendMessage(Component.text("[钱包] " + (enabled ? "已在本服启用：" : "已在本服停用：") + asset.name(),
                enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        drawAssets(holder);
    }

    public void reopen(final Player player, final WalletHolder.Kind kind, final int page) {
        switch (kind) {
            case HISTORY -> openHistory(player, page);
            case PLAYERS -> openPlayers(player, page);
            case ASSETS -> openAssets(player, page);
        }
    }

    public void reopen(final Player player, final WalletHolder holder, final int page) {
        if (holder.kind() == WalletHolder.Kind.HISTORY) openHistory(player, page, holder.currency());
        else reopen(player, holder.kind(), page);
    }

    // ------------------------------------------------------------------ 流水页

    public void openHistory(final Player player, final int page) {
        openHistory(player, page, null);
    }

    public void openHistory(final Player player, final int page, final String currency) {
        if (!PlayerTasks.ownedBy(player)) {
            PlayerTasks.run(this.plugin, player, () -> openHistory(player, page, currency));
            return;
        }
        final WalletHolder holder = new WalletHolder(WalletHolder.Kind.HISTORY, Math.max(page, 1),
                player.getUniqueId());
        holder.currency(currency);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("钱包流水 · 第 " + holder.page() + " 页"));
        holder.inventory(inventory);
        inventory.setItem(SLOT_CENTER, item(Material.PAPER, Component.text("正在加载流水…", NamedTextColor.GRAY),
                List.of()));
        drawControls(inventory, holder, 1);
        player.openInventory(inventory);

        final List<String> codes = currency == null ? enabledCodes()
                : enabledCodes().stream().filter(code -> code.equalsIgnoreCase(currency)).toList();
        this.service.transactions(player.getUniqueId(), player.getName(), codes, holder.page(),
                        this.config.historyPageSize())
                .whenComplete((result, error) -> runFor(player, () -> {
                    if (viewing(player, holder)) {
                        drawHistory(holder, result, error);
                    }
                }));
    }

    public void cycleHistoryCurrency(Player player, WalletHolder holder) {
        final List<String> options = enabledCodes();
        final int current = holder.currency() == null ? -1 : options.indexOf(holder.currency());
        final String next = current + 1 >= options.size() ? null : options.get(current + 1);
        openHistory(player, 1, next);
    }

    private void drawHistory(final WalletHolder holder, final WalletTransactionPage page, final Throwable error) {
        final Inventory inventory = holder.getInventory();
        for (int slot = 0; slot < CONTENT_SLOTS; slot++) {
            inventory.setItem(slot, null);
        }

        if (error != null) {
            inventory.setItem(SLOT_CENTER, item(Material.PAPER, Component.text("加载失败", NamedTextColor.RED),
                    List.of(Component.text(message(error), NamedTextColor.GRAY))));
        } else if (page == null || page.records().isEmpty()) {
            inventory.setItem(SLOT_CENTER, item(Material.PAPER, Component.text("暂无流水", NamedTextColor.GRAY),
                    List.of()));
        } else {
            final Map<String, WalletAsset> assets = assetIndex();
            int slot = 0;
            for (final WalletTransaction transaction : page.records()) {
                if (slot >= CONTENT_SLOTS) {
                    break;
                }
                inventory.setItem(slot++, transactionItem(transaction, assets.get(transaction.assetCode())));
            }
        }

        final long total = page == null ? 0L : page.total();
        final int pages = (int) Math.max(1L, (total + this.config.historyPageSize() - 1L) / this.config.historyPageSize());
        drawControls(inventory, holder, pages);
    }

    private ItemStack transactionItem(final WalletTransaction transaction, final WalletAsset asset) {
        final String type = transaction.type() == null ? "" : transaction.type().toUpperCase();
        final boolean credit = "CREDIT".equals(type) || "IN".equalsIgnoreCase(transaction.direction());
        final boolean debit = "DEBIT".equals(type) || "OUT".equalsIgnoreCase(transaction.direction());
        final String label = switch (type) {
            case "CREDIT" -> "入账";
            case "DEBIT" -> "出账";
            case "TRANSFER" -> "转账";
            case "SET" -> "设余额";
            default -> type.isEmpty() ? "流水" : type;
        };
        final String sign = credit ? "+" : debit ? "-" : "";

        final Component name = Component.text(label + " " + sign + Format.amount(transaction.amount(), asset),
                credit ? NamedTextColor.GREEN : debit ? NamedTextColor.RED : NamedTextColor.YELLOW);

        final List<Component> lore = new ArrayList<>();
        lore.add(line("币种：", asset == null ? transaction.assetCode()
                : asset.name() + "（" + asset.code() + "）"));
        lore.add(line("时间：", Format.time(transaction.createdAt(), this.config.zone())));
        lore.add(line("余额：", Format.amount(transaction.balanceAfter(), asset)));
        if (transaction.remark() != null && !transaction.remark().isBlank()) {
            lore.add(line("备注：", transaction.remark()));
        }
        if (transaction.businessNo() != null && !transaction.businessNo().isBlank()) {
            lore.add(Component.text("业务号：" + transaction.businessNo(), NamedTextColor.DARK_GRAY)
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        return item(Material.PAPER, name, lore);
    }

    // ------------------------------------------------------------------ 货币类型页（管理员）

    public void openAssets(final Player player) {
        openAssets(player, 1);
    }

    public void openAssets(final Player player, final int page) {
        if (!player.hasPermission("wallet.admin")) return;
        if (!PlayerTasks.ownedBy(player)) {
            PlayerTasks.run(this.plugin, player, () -> openAssets(player, page));
            return;
        }
        final int pages = Math.max(1, (service.cachedAssets().size() + CONTENT_SLOTS - 1) / CONTENT_SLOTS);
        final WalletHolder holder = new WalletHolder(WalletHolder.Kind.ASSETS, Math.clamp(page, 1, pages), player.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("货币类型"));
        holder.inventory(inventory);
        drawAssets(holder);
        player.openInventory(inventory);

        this.service.assets().whenComplete((assets, error) -> runFor(player, () -> {
            if (viewing(player, holder)) {
                drawAssets(holder);
            }
        }));
    }

    private void drawAssets(final WalletHolder holder) {
        final Inventory inventory = holder.getInventory();
        for (int slot = 0; slot < CONTENT_SLOTS; slot++) {
            inventory.setItem(slot, null);
        }

        final List<WalletAsset> assets = this.service.cachedAssets();
        if (assets.isEmpty()) {
            inventory.setItem(SLOT_CENTER, item(Material.PAPER,
                    Component.text("暂无货币（请检查配置）", NamedTextColor.GRAY), List.of()));
        } else {
            int slot = 0;
            for (final WalletAsset asset : assets.subList(Math.min((holder.page() - 1) * CONTENT_SLOTS, assets.size()), assets.size())) {
                if (slot >= CONTENT_SLOTS) {
                    break;
                }
                final boolean localEnabled = this.switches.isEnabled(asset.code(), assets);
                final boolean active = localEnabled && asset.enabled();
                final List<Component> lore = new ArrayList<>();
                lore.add(line("code：", asset.code()));
                lore.add(line("符号：", asset.symbol() == null || asset.symbol().isBlank() ? "（无）" : asset.symbol()));
                lore.add(line("小数位：", String.valueOf(asset.scale())));
                lore.add(line("来源：", this.config.testMode() ? "测试账本"
                        : this.config.source(asset.code()).equals("website") ? "官网" : "本地"));
                lore.add(line("配置启用：", asset.enabled() ? "是" : "否"));
                lore.add(line("本服启用：", localEnabled ? "是" : "否"));
                if (asset.enabled()) {
                    lore.add(plain("点击切换本服启用状态", NamedTextColor.YELLOW));
                } else {
                    lore.add(plain("配置已停用，无法在本服启用", NamedTextColor.DARK_GRAY));
                }
                inventory.setItem(slot++, item(active ? Material.LIME_WOOL : Material.RED_WOOL,
                        Component.text(asset.name() + "（" + asset.code() + "）",
                                active ? NamedTextColor.GREEN : NamedTextColor.RED), lore));
            }
        }

        drawControls(inventory, holder, Math.max(1, (assets.size() + CONTENT_SLOTS - 1) / CONTENT_SLOTS));
    }

    // ------------------------------------------------------------------ 玩家货币页（管理员）

    public void openPlayers(final Player player, final int page) {
        if (!player.hasPermission("wallet.admin")) return;
        if (!PlayerTasks.ownedBy(player)) {
            PlayerTasks.run(this.plugin, player, () -> openPlayers(player, page));
            return;
        }
        final List<PlaytimeLedgerReader.Entry> entries = this.ledger.read();
        final int size = this.config.adminPageSize();
        final int pages = Math.max(1, (entries.size() + size - 1) / size);
        final int safePage = Math.max(1, Math.min(page, pages));

        final WalletHolder holder = new WalletHolder(WalletHolder.Kind.PLAYERS, safePage, player.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("玩家货币 · 第 " + safePage + " 页"));
        holder.inventory(inventory);
        drawPlayers(holder, entries, pages);
        player.openInventory(inventory);

        final List<WalletAsset> assets = enabledAssets();
        final List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            final int position = (safePage - 1) * size + index;
            if (position >= entries.size()) {
                break;
            }
            final PlaytimeLedgerReader.Entry entry = entries.get(position);
            for (final WalletAsset asset : assets) {
                futures.add(this.service.balance(entry.playerId(), entry.name(), asset.code())
                        .exceptionally(error -> null));
            }
        }

        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .whenComplete((ignored, error) -> runFor(player, () -> {
                        if (viewing(player, holder)) {
                            drawPlayers(holder, entries, pages);
                        }
                    }));
        }
    }

    private void drawPlayers(final WalletHolder holder, final List<PlaytimeLedgerReader.Entry> entries,
                             final int pages) {
        final Inventory inventory = holder.getInventory();
        for (int slot = 0; slot < CONTENT_SLOTS; slot++) {
            inventory.setItem(slot, null);
        }

        final int size = this.config.adminPageSize();
        final List<WalletAsset> assets = enabledAssets();
        int slot = 0;
        for (int index = (holder.page() - 1) * size; index < entries.size() && slot < CONTENT_SLOTS; index++) {
            final PlaytimeLedgerReader.Entry entry = entries.get(index);
            final List<Component> lore = new ArrayList<>();
            lore.add(Component.text(entry.playerId().toString(), NamedTextColor.DARK_GRAY)
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            lore.add(line("本服在线时长：", Format.duration(entry.onlineMillis())));
            for (final WalletAsset asset : assets) {
                final WalletBalance balance = this.service.cachedBalance(entry.playerId(), asset.code());
                lore.add(line(asset.name() + "：", balance == null ? "…" : Format.amount(balance.balance(), asset)));
            }
            if (assets.isEmpty()) {
                lore.add(Component.text("本服暂无可用货币", NamedTextColor.GRAY));
            }
            inventory.setItem(slot++, head(entry.playerId(), entry.name(), lore));
        }

        if (entries.isEmpty()) {
            inventory.setItem(SLOT_CENTER, item(Material.PAPER,
                    Component.text("本服还没有在线时长记录", NamedTextColor.GRAY), List.of()));
        }

        drawControls(inventory, holder, pages);
    }

    // ------------------------------------------------------------------ 公共：控制行与物品

    private void drawControls(final Inventory inventory, final WalletHolder holder, final int pages) {
        final boolean hasPrevious = holder.page() > 1;
        final boolean hasNext = holder.page() < pages;
        if (holder.kind() == WalletHolder.Kind.HISTORY) {
            inventory.setItem(45, item(Material.BOOK, Component.text("币种：" + (holder.currency() == null ? "全部" : holder.currency()), NamedTextColor.YELLOW),
                    List.of(Component.text("点击切换；官网不可用时可单独查看本地币种", NamedTextColor.GRAY))));
        }
        inventory.setItem(SLOT_PREVIOUS, item(Material.ARROW,
                Component.text(hasPrevious ? "上一页" : "已经是第一页",
                        hasPrevious ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY),
                List.of(Component.text("第 " + holder.page() + " / " + pages + " 页", NamedTextColor.GRAY))));
        inventory.setItem(SLOT_BACK, item(Material.BARRIER, Component.text("返回主菜单", NamedTextColor.GRAY),
                List.of()));
        inventory.setItem(SLOT_NEXT, item(Material.ARROW,
                Component.text(hasNext ? "下一页" : "已经是最后一页",
                        hasNext ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY),
                List.of(Component.text("第 " + holder.page() + " / " + pages + " 页", NamedTextColor.GRAY))));
    }

    private Map<String, WalletAsset> assetIndex() {
        return this.service.cachedAssets().stream()
                .collect(Collectors.toMap(WalletAsset::code, asset -> asset, (left, right) -> left));
    }

    private void runFor(final Player player, final Runnable task) {
        PlayerTasks.run(this.plugin, player, task);
    }

    private static boolean viewing(final Player player, final WalletHolder holder) {
        return player.getOpenInventory().getTopInventory().getHolder() == holder;
    }

    private static String message(final Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof WalletException) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return error == null ? "未知错误" : String.valueOf(error.getMessage());
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

    private static ItemStack item(final Material material, final Component name, final List<Component> lore) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack head(final UUID playerId, final String name, final List<Component> lore) {
        final ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(playerId));
            skull.displayName(Component.text(name == null || name.isBlank() ? playerId.toString() : name,
                            NamedTextColor.WHITE)
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            if (!lore.isEmpty()) {
                skull.lore(lore);
            }
            item.setItemMeta(skull);
        }
        return item;
    }
}
