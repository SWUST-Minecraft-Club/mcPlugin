package com.example.town.arcui;

import com.example.town.model.TownSnapshotView;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** 在线玩家选择器：邀请成员（也可复用于其他“点选玩家”场景）。 */
public final class PickerScreen extends Screen {

    private static final int PAGE_SIZE = 16;
    private int page;

    public PickerScreen(UiSession ui) {
        super(ui);
    }

    @Override
    public void draw() {
        chrome("<aqua>邀请成员");
        Player self = ui.ctx().getPlayer();
        TownSnapshotView town = plugin().service().townOf(self.getUniqueId());
        if (town == null) {
            ui.back();
            return;
        }
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(self.getUniqueId())) {
                continue;
            }
            candidates.add(online);
        }
        int pages = Math.max(1, (candidates.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pages) {
            page = pages - 1;
        }
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = start + i;
            if (index >= candidates.size()) {
                break;
            }
            Player target = candidates.get(index);
            drawCandidate(i % 2, i / 2, target);
        }
        if (candidates.isEmpty()) {
            label(UiMetrics.CANVAS_W / 2, 130, 11, "<gray>当前没有其他在线玩家。");
        }
        if (page > 0) {
            button(40, 236, 90, 22, "<gray>← 上一页", 9, () -> {
                page--;
                ui.redraw();
            });
        }
        label(UiMetrics.CANVAS_W / 2, 247, 9, "<dark_gray>" + (page + 1) + " / " + pages
                + " <gray>· 在线 " + candidates.size() + " 人");
        if (page < pages - 1) {
            button(UiMetrics.CANVAS_W - 130, 236, 90, 22, "<gray>下一页 →", 9, () -> {
                page++;
                ui.redraw();
            });
        }
    }

    private void drawCandidate(int column, int row, Player target) {
        double x = 40 + column * 200;
        double y = 40 + row * 23;
        var service = plugin().service();
        TownSnapshotView targetTown = service.townOf(target.getUniqueId());
        TownSnapshotView invited = service.inviteOf(target.getUniqueId());
        boolean busy = targetTown != null || invited != null;
        panel(x, y, 190, 19, busy ? Material.LIGHT_GRAY_CONCRETE : Material.CYAN_CONCRETE);
        label(x + 60, y + 9.5, 9, "<white>" + target.getName()
                + (targetTown != null ? " <red>已有小镇" : busy ? " <yellow>已受邀" : ""));
        if (!busy) {
            bindPanel(x, y, 190, 19, null, () -> {
                var result = new java.util.concurrent.atomic.AtomicReference<
                        com.example.town.service.TownService.Result>();
                service.invite(self(), target.getName(), result::set);
            });
        }
    }

    private Player self() {
        return ui.ctx().getPlayer();
    }
}
