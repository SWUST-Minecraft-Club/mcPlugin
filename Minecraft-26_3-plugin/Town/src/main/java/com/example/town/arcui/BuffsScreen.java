package com.example.town.arcui;

import com.example.town.TownConfig;
import com.example.town.model.TownSnapshotView;
import java.util.function.Consumer;
import org.bukkit.Material;

/**
 * 领地祝福商店：列出配置中的全部 buff，显示价格 / 时长 / 生效状态，点选购买（花费需确认）。
 * 效果由 BuffApplier 周期施加给身处本镇领地的成员。
 */
public final class BuffsScreen extends Screen {

    public BuffsScreen(UiSession ui) {
        super(ui);
    }

    @Override
    public void draw() {
        chrome("<light_purple>领地祝福");
        var view = plugin().service().townOf(selfId());
        if (view == null) {
            ui.back();
            return;
        }
        var defs = plugin().config().buffs();
        if (defs.isEmpty()) {
            label(UiMetrics.CANVAS_W / 2, 120, 11, "<gray>服务器没有配置任何祝福。");
            return;
        }
        int shown = Math.min(defs.size(), 7);
        for (int i = 0; i < shown; i++) {
            drawRow(i, defs.get(i), view);
        }
        label(UiMetrics.CANVAS_W / 2, 247, 9,
                "<dark_gray>祝福对「身处本镇领地的成员」持续生效，可叠加时长。");
    }

    private void drawRow(int index, TownConfig.BuffDef def, TownSnapshotView view) {
        double y = 36 + index * 28;
        Long expiry = view.buffs().get(def.id());
        boolean permanent = expiry != null && expiry == -1;
        boolean active = permanent || (expiry != null && expiry > System.currentTimeMillis());
        panel(30, y, 420, 24, active ? Material.PURPLE_CONCRETE : Material.GRAY_CONCRETE);
        label(160, y + 12, 10, def.displayMini());
        label(300, y + 12, 9, "<white>" + trim(def.cost())
                + " <gray>" + currencySymbol());
        label(380, y + 12, 9, def.permanent() ? "<dark_purple>永久" : "<gray>" + (def.durationMillis() / 60000) + " 分钟");
        label(438, y + 12, 9, active
                ? (permanent ? "<light_purple>生效中" : "<green>生效中")
                : "<dark_gray>未激活");
        if (!active) {
            button(398, y + 3, 44, 18, "<green>购买", 8, () -> tryBuy(def));
        }
    }

    private void tryBuy(TownConfig.BuffDef def) {
        if (def.cost() > 0) {
            ui.open(new ConfirmScreen(ui,
                    "<gold>购买祝福</gold> <white>" + def.displayMini()
                            + "</white><gray>？将支付 <white>" + trim(def.cost()) + " "
                            + currencySymbol(),
                    () -> buy(def)));
        } else {
            buy(def);
        }
    }

    private void buy(TownConfig.BuffDef def) {
        var service = plugin().service();
        service.buyBuff(self(), def.id(), deliver());
    }

    private org.bukkit.entity.Player self() {
        return ui.ctx().getPlayer();
    }

    private Consumer<com.example.town.service.TownService.Result> deliver() {
        return result -> {
            plugin().service().deliver(self(), result);
            ui.ctx().execute(ui::redraw);
        };
    }

    private java.util.UUID selfId() {
        return ui.ctx().getPlayer().getUniqueId();
    }

    private String currencySymbol() {
        var wallet = plugin().service().wallet();
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
