package com.example.town.arcui;

import com.example.town.model.TownSnapshotView;
import com.example.town.service.TownService;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;

/**
 * 主界面：三种形态 ——
 * ① 无镇：介绍 + 「创建小镇」入口 + 待处理邀请卡片；
 * ② 筹备中：成员进度 + 邀请 / 成立（扣费确认） / 解散；
 * ③ 已成立：小镇信息 + 成员 / 圈地 / 祝福 / 传送 / 解散或退出。
 */
public final class HomeScreen extends Screen {

    private static final double W = UiMetrics.CANVAS_W;
    private static final double H = UiMetrics.CANVAS_H;

    public HomeScreen(UiSession ui) {
        super(ui);
    }

    @Override
    public void draw() {
        chrome("<gold>小镇");
        UUID self = ui.ctx().getPlayer().getUniqueId();
        TownService service = plugin().service();
        TownSnapshotView view = service.townOf(self);
        if (view != null) {
            if (view.active()) {
                drawActive(view);
            } else {
                drawPending(view);
            }
            return;
        }
        TownSnapshotView invite = service.inviteOf(self);
        if (invite != null) {
            drawInvite(invite);
            return;
        }
        drawTownless();
    }

    // ------------------------------------------------------------------
    // ① 无镇
    // ------------------------------------------------------------------

    private void drawTownless() {
        panel(24, 48, W - 48, 96, Material.GRAY_CONCRETE);
        label(W / 2, 74, 16, "<yellow>欢迎来到小镇系统");
        label(W / 2, 100, 10, "<gray>创立你的小镇：邀请伙伴、圈下领地、购买祝福。");
        label(W / 2, 120, 10, "<gray>成立费用与规则见下一页。");
        button(W / 2 - 90, 168, 180, 40, "<gold>创建小镇", 15,
                () -> ui.open(new CreateScreen(ui)));
    }

    private void drawInvite(TownSnapshotView town) {
        panel(24, 56, W - 48, 88, Material.BLUE_CONCRETE);
        label(W / 2, 80, 13, "<gold>你收到了小镇邀请");
        label(W / 2, 104, 12, "<white>" + town.name());
        button(W / 2 - 150, 172, 130, 36, "<green>接受邀请", 13,
                () -> service().acceptInvite(player(), deliver()));
        button(W / 2 + 20, 172, 130, 36, "<red>拒绝邀请", 13,
                () -> service().rejectInvite(player(), deliver()));
    }

    // ------------------------------------------------------------------
    // ② 筹备中
    // ------------------------------------------------------------------

    private void drawPending(TownSnapshotView town) {
        int min = plugin().config().minMembers();
        double cost = plugin().config().foundingCost();
        panel(24, 44, 220, 150, Material.GRAY_CONCRETE);
        label(134, 62, 13, "<white>" + town.name());
        label(134, 88, 10, "<gray>状态：<yellow>筹备中");
        label(134, 110, 10, "<gray>成员：<white>" + town.memberCount() + " / " + min
                + (town.memberCount() >= min ? " <green>✓" : ""));
        label(134, 132, 10, "<gray>成立费用：<white>" + trim(cost)
                + (cost > 0 ? " <gray>" + currencySymbol() : ""));
        panel(258, 44, 198, 150, Material.GRAY_CONCRETE);
        button(272, 60, 170, 30, "<aqua>邀请成员", 12,
                () -> ui.open(new PickerScreen(ui)));
        boolean enough = town.memberCount() >= min;
        if (enough) {
            button(272, 100, 170, 30, "<gold>成立小镇（确认扣费）", 11,
                    () -> ui.open(new ConfirmScreen(ui,
                            "<gold>成立小镇</gold><gray>？将支付 <white>" + trim(cost)
                                    + (cost > 0 ? " " + currencySymbol() : ""),
                            () -> service().found(player(), deliver()))));
        } else {
            panel(272, 100, 170, 30, Material.LIGHT_GRAY_CONCRETE);
            label(357, 115, 10, "<dark_gray>成员不足，无法成立");
        }
        button(272, 148, 170, 26, "<red>放弃筹备（解散）", 10,
                () -> ui.open(new ConfirmScreen(ui, "<red>确认解散筹备中的小镇？",
                        () -> service().disband(player(), deliver()))));
        label(W / 2, 218, 9, "<dark_gray>提示：也可以用 /town help 查看指令。");
    }

    // ------------------------------------------------------------------
    // ③ 已成立
    // ------------------------------------------------------------------

    private void drawActive(TownSnapshotView town) {
        boolean mayor = town.mayor().equals(player().getUniqueId());
        panel(24, 44, 216, 168, Material.GRAY_CONCRETE);
        label(132, 62, 14, "<white>" + town.name());
        label(132, 88, 10, "<gray>镇长：<white>" + nameOf(town.mayor()));
        label(132, 108, 10, "<gray>成员：<white>" + town.memberCount() + maxMembersText());
        label(132, 128, 10, "<gray>领地：<white>" + town.claimCount() + maxChunksText());
        label(132, 148, 10, "<gray>祝福：<white>" + town.buffs().size() + " <gray>个生效中");
        label(132, 168, 9, "<gray>成立于：<white>" + town.createdDate());
        if (town.hallSet()) {
            label(132, 190, 9, "<gold>★ 镇政厅已落成");
        } else {
            label(132, 190, 9, "<yellow>尚未放置镇政厅");
        }

        panel(254, 44, 202, 168, Material.GRAY_CONCRETE);
        button(268, 58, 174, 28, "<aqua>成员管理", 12,
                () -> ui.open(new MembersScreen(ui)));
        button(268, 92, 174, 28, "<green>领地圈地", 12,
                () -> ui.open(new ClaimMapScreen(ui)));
        button(268, 126, 174, 28, "<light_purple>领地祝福", 12,
                () -> ui.open(new BuffsScreen(ui)));
        if (town.spawnSet()) {
            button(268, 160, 174, 28, "<gold>回小镇", 12,
                    () -> service().townSpawn(player(), deliver()));
        } else {
            panel(268, 160, 174, 28, Material.LIGHT_GRAY_CONCRETE);
            label(355, 174, 10, "<dark_gray>镇政厅未放置");
        }
        if (mayor) {
            button(268, 194, 174, 14, "<red>解散小镇", 8,
                    () -> ui.open(new ConfirmScreen(ui, "<red>确认解散小镇？领地将全部释放！",
                            () -> service().disband(player(), deliver()))));
        } else {
            button(268, 194, 174, 14, "<red>退出小镇", 8,
                    () -> ui.open(new ConfirmScreen(ui, "<red>确认退出小镇？",
                            () -> service().leave(player(), deliver()))));
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private org.bukkit.entity.Player player() {
        return ui.ctx().getPlayer();
    }

    private TownService service() {
        return plugin().service();
    }

    /** 统一回调：结果消息交给服务层按线程规则投递，然后重绘界面。 */
    private java.util.function.Consumer<TownService.Result> deliver() {
        return result -> {
            service().deliver(player(), result);
            ui.ctx().execute(ui::redraw);
        };
    }

    private String nameOf(UUID uuid) {
        String name = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? "未知" : name;
    }

    private String maxMembersText() {
        int max = plugin().config().maxMembers();
        return max > 0 ? " <gray>/ " + max : "";
    }

    private String maxChunksText() {
        int max = plugin().config().maxChunksPerTown();
        return max > 0 ? " <gray>/ " + max + " 上限" : "";
    }

    private String currencySymbol() {
        var wallet = service().wallet();
        if (wallet == null) {
            return "";
        }
        return wallet.symbolOf(plugin().config().currencyCode(),
                wallet.resolveCurrency(plugin().config().currencyCode()));
    }

    private static String trim(double amount) {
        return amount == Math.floor(amount)
                ? String.valueOf((long) amount)
                : String.valueOf(amount);
    }
}
