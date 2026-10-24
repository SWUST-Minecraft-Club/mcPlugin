package com.example.town.arcui;

import com.example.town.model.Role;
import com.example.town.model.TownSnapshot;
import com.example.town.model.TownSnapshotView;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;

/** 成员管理：成员列表分页 + 单人操作（升职 / 降职 / 踢出 / 转让）。 */
public final class MembersScreen extends Screen {

    private static final int PAGE_SIZE = 8;
    private int page;

    public MembersScreen(UiSession ui) {
        super(ui);
    }

    @Override
    public void draw() {
        chrome("<aqua>成员管理");
        TownSnapshotView town = plugin().service().townOf(selfId());
        if (town == null) {
            ui.back();
            return;
        }
        List<UUID> ordered = town.raw().membersByRole().stream()
                .map(java.util.Map.Entry::getKey)
                .toList();
        int pages = Math.max(1, (ordered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pages) {
            page = pages - 1;
        }
        int start = page * PAGE_SIZE;
        int end = Math.min(ordered.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            UUID member = ordered.get(i);
            Role role = town.members().get(member);
            drawMemberRow(i - start, member, role);
        }
        // 翻页
        if (page > 0) {
            button(40, 236, 90, 22, "<gray>← 上一页", 9, () -> {
                page--;
                ui.redraw();
            });
        }
        label(UiMetrics.CANVAS_W / 2, 247, 9, "<dark_gray>" + (page + 1) + " / " + pages);
        if (page < pages - 1) {
            button(UiMetrics.CANVAS_W - 130, 236, 90, 22, "<gray>下一页 →", 9, () -> {
                page++;
                ui.redraw();
            });
        }
    }

    private void drawMemberRow(int row, UUID member, Role role) {
        double y = 36 + row * 24;
        boolean self = member.equals(selfId());
        panel(30, y, 300, 20, self ? Material.BLUE_CONCRETE : Material.GRAY_CONCRETE);
        label(160, y + 10, 9, "<white>" + nameOf(member)
                + (self ? " <aqua>(我)" : ""));
        label(340, y + 10, 9, switch (role) {
            case MAYOR -> "<gold>镇长";
            case OFFICER -> "<green>官员";
            case MEMBER -> "<gray>成员";
        });
        if (!self) {
            button(400, y + 2, 50, 16, "<aqua>操作", 8,
                    () -> ui.open(new MemberActionsScreen(ui, member, nameOf(member))));
        }
    }

    private UUID selfId() {
        return ui.ctx().getPlayer().getUniqueId();
    }

    private String nameOf(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? "未知" : name;
    }

    /** 单个成员的操作弹层。 */
    private static final class MemberActionsScreen extends Screen {

        private final UUID target;
        private final String targetName;

        MemberActionsScreen(UiSession ui, UUID target, String targetName) {
            super(ui);
            this.target = target;
            this.targetName = targetName;
        }

        @Override
        public void draw() {
            chrome("<aqua>成员：<white>" + targetName);
            var resolver = Placeholder.unparsed("player", targetName);
            boolean isMayor = townView().mayor().equals(selfId());
            boolean canManage = isMayor || myRole() == Role.OFFICER;
            if (!canManage) {
                label(UiMetrics.CANVAS_W / 2, 120, 11, "<red>只有镇长或官员可以管理成员。");
                return;
            }
            Role targetRole = townView().members().get(target);
            if (targetRole == Role.MEMBER) {
                button(70, 90, 150, 34, "<green>提升为官员", 12, () ->
                        service().promote(player(), targetName, deliver()));
            }
            if (targetRole == Role.OFFICER) {
                button(260, 90, 150, 34, "<yellow>降为成员", 12, () ->
                        service().demote(player(), targetName, deliver()));
            }
            button(70, 140, 150, 34, "<red>踢出小镇", 12, () ->
                    ui.open(new ConfirmScreen(ui,
                            "<red>将 <white>" + targetName + "</white> 踢出小镇？",
                            () -> service().kick(player(), targetName, deliver()))));
            if (isMayor) {
                button(260, 140, 150, 34, "<gold>转让镇长之位", 12, () ->
                        ui.open(new ConfirmScreen(ui,
                                "<gold>把镇长转让给 <white>" + targetName + "</white>？",
                                () -> service().transfer(player(), targetName, deliver()))));
            }
            label(UiMetrics.CANVAS_W / 2, 210, 9,
                    "<dark_gray>邀请新成员请返回后点击「邀请成员」或前往成员页底部按钮。");
        }

        private TownSnapshotView townView() {
            return plugin().service().townOf(selfId());
        }

        private Role myRole() {
            return townView().members().get(selfId());
        }

        private UUID selfId() {
            return ui.ctx().getPlayer().getUniqueId();
        }

        private org.bukkit.entity.Player player() {
            return ui.ctx().getPlayer();
        }

        private com.example.town.service.TownService service() {
            return plugin().service();
        }

        private java.util.function.Consumer<com.example.town.service.TownService.Result> deliver() {
            return result -> {
                service().deliver(player(), result);
                if (result.ok()) {
                    // 操作成功：弹层与成员列表一并刷新（回退两层）
                    ui.back();
                }
                ui.ctx().execute(ui::redraw);
            };
        }
    }
}
