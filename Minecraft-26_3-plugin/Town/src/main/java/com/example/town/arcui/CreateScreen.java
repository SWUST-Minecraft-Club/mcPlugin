package com.example.town.arcui;

import com.example.town.util.Threads;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** 创建小镇：规则说明 + 名称输入（关闭界面，走聊天栏捕获，成功后自动重开界面）。 */
public final class CreateScreen extends Screen {

    private static final double W = UiMetrics.CANVAS_W;

    public CreateScreen(UiSession ui) {
        super(ui);
    }

    @Override
    public void draw() {
        chrome("<gold>创建小镇");
        double cost = plugin().config().foundingCost();
        int min = plugin().config().minMembers();
        panel(40, 42, W - 80, 120, Material.GRAY_CONCRETE);
        label(W / 2, 64, 12, "<yellow>成立流程");
        label(W / 2, 90, 10, "<gray>1. 起一个响亮的名字（" + plugin().config().nameMinLength()
                + "~" + plugin().config().nameMaxLength() + " 字，中文/字母/数字）");
        label(W / 2, 110, 10, "<gray>2. 邀请 <white>" + min + "</white> 名成员加入");
        label(W / 2, 130, 10, "<gray>3. 成员到齐后成立，支付 <white>" + trim(cost)
                + (cost > 0 ? " " + currencySymbol() : "") + "</white>，领取镇政厅");
        label(W / 2, 150, 10, "<gray>4. 放置镇政厅：免费圈地 + 设定传送点");
        button(W / 2 - 100, 182, 200, 40, "<gold>输入小镇名称", 14, this::beginNaming);
        label(W / 2, 240, 9, "<dark_gray>点击后请在聊天栏输入名称，「取消」可放弃");
    }

    private void beginNaming() {
        Player player = ui.ctx().getPlayer();
        // 优先用 ArcMenu 3.2+ 的原生输入（界面保持打开）；不可用再回落聊天捕获
        if (plugin().arcUi() != null) {
            plugin().arcUi().requestText(player, "输入小镇名称", 12, (ok, name) -> {
                if (ok != null && ok && name != null && !name.isBlank()) {
                    create(player, name);
                }
            });
            return;
        }
        ui.close();
        // 等 ArcMenu 会话收尾后，回到玩家线程开始聊天输入
        Threads.globalLater(plugin(), 10, () -> Threads.forPlayer(plugin(), player, () ->
                plugin().chatInputs().begin(player, name -> create(player, name))));
    }

    private void create(Player player, String name) {
        var service = plugin().service();
        service.create(player, name, result -> {
            service.deliver(player, result);
            if (result.ok()) {
                plugin().openUi(player);
            }
        });
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
