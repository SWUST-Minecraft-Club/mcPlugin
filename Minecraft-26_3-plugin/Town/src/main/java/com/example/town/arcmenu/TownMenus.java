package com.example.town.arcmenu;

import com.example.town.TownConfig;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.example.town.arcmenu.MenuBuilder.Align.CENTER;
import static com.example.town.arcmenu.MenuBuilder.Align.LEFT;

/** Town 的 11 份 ArcMenu 页面；全部坐标使用 800×450 设计坐标。 */
public final class TownMenus {

    public static final String BG = "#0B1211";
    public static final String PANEL = "#111D1A";
    public static final String RAISE = "#182923";
    public static final String LINE = "#2B3C35";
    public static final String TEXT = "#F0EBDD";
    public static final String MUTE = "#9AADA4";
    public static final String ACC = "#D8AE63";
    public static final String GREEN = "#52C48B";
    public static final String RED = "#D97676";
    public static final String CYAN = "#76B7C6";

    public static final String HOME_NONE = "town-home-none";
    public static final String HOME_INVITED = "town-home-invited";
    public static final String HOME_PENDING = "town-home-pending";
    public static final String HOME_ACTIVE = "town-home-active";
    public static final String MEMBERS = "town-members";
    public static final String MEMBER_DETAIL = "town-member-detail";
    public static final String INVITE_PICKER = "town-invite-picker";
    public static final String CLAIM = "town-claim";
    public static final String BUFFS = "town-buffs";
    public static final String CONFIRM = "town-confirm";
    public static final String CREATE = "town-create";

    public record Written(String id, Path file) {
    }

    private TownMenus() {
    }

    private static final int TOP = 52;
    private static final int BOT = 40;

    private static JsonObject dyn() {
        return MenuBuilder.updateEvery(40);
    }

    private static MenuBuilder chrome(final String title, final String hint, final boolean showBack) {
        final MenuBuilder m = new MenuBuilder();
        m.rect("bg", 0, 0, MenuBuilder.W, MenuBuilder.H, BG, 0);
        m.rect("bar-top", 0, 0, MenuBuilder.W, TOP, PANEL, 1);
        m.rect("bar-bot", 0, MenuBuilder.H - BOT, MenuBuilder.W, BOT, PANEL, 1);
        m.rect("line-top", 0, TOP, MenuBuilder.W, 1, LINE, 3);
        m.rect("line-bot", 0, MenuBuilder.H - BOT, MenuBuilder.W, 1, LINE, 3);
        m.rect("brand-bg", 20, 12, 28, 28, ACC, 2);
        m.text("brand-t", "&#0B1211镇", 34, 27, 14, 5, CENTER, null);
        m.text("title", "&#D8AE63" + title, 58, 27, 15, 5, LEFT, null);
        m.text("bal-label", "&#9AADA4钱包余额", 680, 16, 8, 5, CENTER, null);
        m.text("bal-value", "&#D8AE63{meta:balance}", 680, 34, 11, 5, CENTER, dyn());
        m.text("close-x", "&#9AADA4×", 774, 27, 16, 5, CENTER, null);
        m.region("close", 752, 6, 44, 40,
                MenuBuilder.spec(MenuBuilder.tooltip("&f关闭界面"), MenuBuilder.click("all", "close:")));
        m.text("hint", "&#9AADA4" + hint, 20, 430, 8, 5, LEFT, null);
        if (showBack) {
            m.rect("back-bg", 664, 416, 116, 26, RAISE, 2);
            m.text("back-t", "&#F0EBDD返回主页", 722, 429, 9, 5, CENTER, null);
            m.region("back", 664, 416, 116, 26,
                    MenuBuilder.spec(MenuBuilder.tooltip("&f返回小镇主页"), MenuBuilder.click("all", "command: town")));
        }
        return m;
    }

    private static void button(final MenuBuilder m, final String id, final double l, final double t,
                               final double w, final double h, final String color, final String label,
                               final JsonObject spec) {
        m.rect(id + "-bg", l, t, w, h, color, 2);
        m.text(id + "-t", label, l + w / 2, t + h / 2, 11, 5, CENTER, null);
        m.region(id, l, t, w, h, spec);
    }

    private static void button(final MenuBuilder m, final String id, final double l, final double t,
                               final double w, final double h, final String color, final String label,
                               final String... actions) {
        button(m, id, l, t, w, h, color, label,
                MenuBuilder.spec(MenuBuilder.tooltip("&f" + MenuBuilder.stripCodes(label)),
                        MenuBuilder.click("all", actions)));
    }

    private static void navCard(final MenuBuilder m, final String id, final double l, final double t,
                                final double w, final double h, final String material,
                                final String title, final String subtitle, final String... actions) {
        m.rect(id + "-bg", l, t, w, h, RAISE, 2);
        m.item(id + "-icon", material, l + 30, t + h / 2, 28, 4);
        m.text(id + "-title", "&#F0EBDD" + title, l + 58, t + h / 2 - 12, 12, 5, LEFT, null);
        m.text(id + "-sub", "&#9AADA4" + subtitle, l + 58, t + h / 2 + 13, 8, 5, LEFT, null);
        m.region(id, l, t, w, h,
                MenuBuilder.spec(MenuBuilder.tooltip("&f" + title, "&7" + subtitle),
                        MenuBuilder.click("all", actions)));
    }

    private static void infoRow(final MenuBuilder m, final String id, final String label,
                                final String value, final double cy, final double lx, final double vx) {
        m.text("i-" + id, "&#9AADA4" + label, lx, cy, 10, 5, LEFT, null);
        m.text("v-" + id, "&#F0EBDD" + value, vx, cy, 10, 5, CENTER, dyn());
    }

    private static void pagination(final MenuBuilder m, final String command) {
        button(m, "page-prev", 400, 416, 70, 26, RAISE, "&#F0EBDD上一页",
                MenuBuilder.conditionSpec("{meta:has_prev} == 1", null,
                        MenuBuilder.click("all", "command: " + command + " {meta:prev_page}")));
        button(m, "page-next", 478, 416, 70, 26, RAISE, "&#F0EBDD下一页",
                MenuBuilder.conditionSpec("{meta:has_next} == 1", null,
                        MenuBuilder.click("all", "command: " + command + " {meta:next_page}")));
        m.text("page-now", "&#9AADA4{meta:page_now} / {meta:page_total}", 586, 430, 9, 5, CENTER, dyn());
    }

    // ------------------------------------------------------------------ 主页

    public static MenuBuilder homeNone() {
        final MenuBuilder m = chrome("小镇系统", "创建后进入筹备期，邀请伙伴即可成立小镇", false);
        m.rect("hero", 20, 64, 370, 322, PANEL, 1);
        m.text("hero-kicker", "&#D8AE63TOWN HALL", 44, 98, 9, 5, LEFT, null);
        m.item("hero-icon", "bell", 336, 104, 32, 4);
        m.text("hero-title-1", "&#F0EBDD从一块领地，", 44, 148, 23, 5, LEFT, null);
        m.text("hero-title-2", "&#F0EBDD建立你的小镇。", 44, 182, 23, 5, LEFT, null);
        m.text("hero-sub-1", "&#9AADA4召集伙伴、划定领地，", 44, 222, 10, 5, LEFT, null);
        m.text("hero-sub-2", "&#9AADA4让每一次建设都有归属。", 44, 242, 10, 5, LEFT, null);
        button(m, "go-create", 44, 298, 310, 48, ACC, "&#0B1211创建小镇　→", "command: town create");
        final String[] titles = {"登记小镇名称", "邀请首批成员", "落成镇政厅"};
        final String[] subs = {"独一无二的名字，是小镇的起点", "至少 2 名成员，包含你自己", "支付成立费用，获得中心与初始领地"};
        for (int i = 0; i < 3; i++) {
            final double t = 64 + i * 112;
            m.rect("step-" + i, 406, t, 374, 98, PANEL, 1);
            m.text("step-no-" + i, "&#D8AE630" + (i + 1), 428, t + 49, 20, 5, LEFT, null);
            m.text("step-title-" + i, "&#F0EBDD" + titles[i], 480, t + 37, 12, 5, LEFT, null);
            m.text("step-sub-" + i, "&#9AADA4" + subs[i], 480, t + 62, 8, 5, LEFT, null);
        }
        return m;
    }

    public static MenuBuilder homeInvited() {
        final MenuBuilder m = chrome("小镇邀请", "接受前会再次检查邀请、成员上限和所属小镇", false);
        m.rect("invite-main", 20, 64, 500, 322, PANEL, 1);
        m.rect("invite-side", 536, 64, 244, 322, PANEL, 1);
        m.text("invite-kicker", "&#D8AE63INVITATION", 48, 102, 9, 5, LEFT, null);
        m.item("invite-icon", "writable_book", 458, 108, 34, 4);
        m.text("invite-town", "&#F0EBDD{meta:invited_from}", 270, 158, 23, 5, CENTER, dyn());
        m.text("invite-mayor", "&#9AADA4镇长 {meta:invited_mayor}", 270, 198, 10, 5, CENTER, dyn());
        m.text("invite-members", "&#9AADA4当前 {meta:invited_members} 名成员", 270, 224, 10, 5, CENTER, dyn());
        m.text("invite-expire", "&#D8AE63{meta:invited_expiry}", 270, 266, 11, 5, CENTER, dyn());
        m.text("invite-note", "&#9AADA4加入后即可参与筹备与建设", 270, 314, 9, 5, CENTER, null);
        m.text("invite-question", "&#F0EBDD要接受这份邀请吗？", 658, 116, 13, 5, CENTER, null);
        button(m, "invite-accept", 564, 180, 188, 48, GREEN, "&#0B1211接受邀请", "command: town accept");
        button(m, "invite-reject", 564, 244, 188, 44, RAISE, "&#D97676礼貌拒绝", "command: town reject");
        return m;
    }

    public static MenuBuilder create(final TownConfig cfg) {
        final MenuBuilder m = chrome("创建小镇", "名称 2~12 个字符 · 支持中文、字母、数字、下划线", true);
        m.rect("rules", 20, 64, 300, 322, PANEL, 1);
        m.text("rules-kicker", "&#D8AE63RULES", 44, 96, 9, 5, LEFT, null);
        m.text("rules-title", "&#F0EBDD登记须知", 44, 126, 14, 5, LEFT, null);
        infoRow(m, "name", "名称长度", "{meta:name_len}", 166, 44, 240);
        infoRow(m, "members", "首镇成员", "至少 {meta:min_members} 人", 202, 44, 240);
        infoRow(m, "cost", "成立费用", "{meta:found_cost}", 238, 44, 240);
        m.text("rules-free", "&#9AADA4初始领地", 44, 274, 10, 5, LEFT, null);
        m.text("rules-free-v", "&#F0EBDD镇政厅周围免费圈地", 230, 274, 10, 5, CENTER, null);
        m.rect("form", 336, 64, 444, 322, PANEL, 1);
        m.text("form-kicker", "&#D8AE63NEW TOWN", 368, 100, 9, 5, LEFT, null);
        m.text("form-title", "&#F0EBDD小镇叫什么？", 368, 136, 20, 5, LEFT, null);
        m.frame("in-frame", 368, 174, 380, 48, ACC, 3, 1);
        m.text("in-preview", "&#9AADA4点击这里输入小镇名称", 558, 198, 11, 5, CENTER, null);
        m.region("in-name", 368, 174, 380, 48,
                MenuBuilder.spec(MenuBuilder.tooltip("&f点击唤起输入框，输入后回车提交"),
                        MenuBuilder.click("all", "command: town ui-input name")));
        button(m, "go", 368, 248, 380, 46, ACC, "&#0B1211输入名称并开始筹备",
                "command: town ui-input name");
        m.text("form-note", "&#9AADA4输入后按回车提交；现在只登记名称，成立时才扣费", 558, 326, 9, 5, CENTER, null);
        return m;
    }

    public static MenuBuilder homePending() {
        final MenuBuilder m = chrome("筹备中的小镇", "成员达到要求后，确认费用即可正式成立", false);
        final String[] steps = {"创建档案", "邀请成员", "正式成立", "落成镇政厅"};
        for (int i = 0; i < 4; i++) {
            final double l = 20 + i * 192;
            final String color = i == 0 ? GREEN : i == 1 ? ACC : LINE;
            m.rect("progress-" + i, l, 64, 184, 44, PANEL, 1);
            m.rect("progress-line-" + i, l, 106, 184, 2, color, 3);
            m.text("progress-t-" + i, color + (i + 1) + " · " + steps[i], l + 92, 86, 10, 5, CENTER, null);
        }
        m.rect("info", 20, 122, 370, 264, PANEL, 1);
        m.text("town-name", "&#F0EBDD{meta:town_name}", 205, 152, 18, 5, CENTER, dyn());
        infoRow(m, "mayor", "镇长", "{meta:mayor_name}", 194, 44, 292);
        infoRow(m, "members", "成员进度", "{meta:member_count} / {meta:min_members} 人", 230, 44, 292);
        infoRow(m, "cost", "成立费用", "{meta:found_cost}", 266, 44, 292);
        infoRow(m, "ready", "筹备状态", "{meta:found_state}", 302, 44, 292);
        m.text("pending-note", "&#9AADA4筹备超时将自动解散", 205, 348, 9, 5, CENTER, null);
        button(m, "b-invite", 406, 122, 374, 48, ACC, "&#0B1211邀请在线玩家", "command: town invite");
        button(m, "b-members", 406, 180, 374, 44, RAISE, "&#F0EBDD管理现有成员", "command: town members");
        button(m, "b-found", 406, 234, 374, 48, GREEN, "&#0B1211正式成立 · {meta:found_cost}",
                MenuBuilder.conditionSpec("{meta:can_found} == 1",
                        MenuBuilder.tooltip("&f支付成立费用，正式成立小镇"),
                        MenuBuilder.click("all", "command: town confirm found"),
                        MenuBuilder.actions("tell: &e成员不足，达到 {meta:min_members} 人后才能成立。")));
        button(m, "b-disband", 406, 292, 374, 44, RAISE, "&#D97676放弃筹备",
                "command: town confirm disband");
        return m;
    }

    public static MenuBuilder homeActive() {
        final MenuBuilder m = chrome("小镇政务厅", "管理成员、领地与祝福 · 镇政厅是小镇中心", false);
        m.rect("town-head", 20, 64, 760, 64, PANEL, 1);
        m.item("town-icon", "bell", 50, 96, 30, 4);
        m.text("town-name", "&#F0EBDD{meta:town_name}", 278, 86, 18, 5, CENTER, dyn());
        m.text("town-mayor", "&#9AADA4镇长 {meta:mayor_name} · 成立于 {meta:created_date}",
                278, 108, 8, 5, CENTER, dyn());
        m.text("stat-members", "&#F0EBDD{meta:member_count}&#9AADA4 成员", 500, 96, 10, 5, CENTER, dyn());
        m.text("stat-claims", "&#F0EBDD{meta:claim_count}&#9AADA4 领地", 604, 96, 10, 5, CENTER, dyn());
        m.text("stat-buffs", "&#F0EBDD{meta:buff_count}&#9AADA4 祝福", 708, 96, 10, 5, CENTER, dyn());
        navCard(m, "b-members", 20, 140, 244, 112, "writable_book", "成员管理", "邀请、职位与成员详情", "command: town members");
        navCard(m, "b-claim", 276, 140, 244, 112, "map", "领地地图", "查看、购买与放弃区块", "command: town claimmap center");
        navCard(m, "b-buffs", 20, 264, 244, 112, "potion", "领地祝福", "急迫、再生与更多祝福", "command: town buffs");
        navCard(m, "b-spawn", 276, 264, 244, 112, "lodestone", "回到小镇", "传送至小镇传送点",
                "close:", "command: town spawn");
        m.rect("hall", 536, 140, 244, 164, PANEL, 1);
        m.item("hall-icon", "lodestone", 658, 172, 26, 4);
        m.text("hall-title", "&#F0EBDD镇政厅", 658, 206, 13, 5, CENTER, null);
        m.text("hall-state", "&#52C48B{meta:hall_state}", 658, 234, 10, 5, CENTER, dyn());
        m.text("hall-pos", "&#9AADA4{meta:hall_position}", 658, 260, 9, 5, CENTER, dyn());
        m.rect("danger-bg", 536, 316, 244, 60, RAISE, 2);
        m.text("danger-t", "&#D97676{meta:danger_label}", 658, 346, 11, 5, CENTER, dyn());
        m.region("danger", 536, 316, 244, 60,
                MenuBuilder.spec(MenuBuilder.tooltip("&f退出或解散前需要再次确认"),
                        MenuBuilder.click("all", "command: town confirm {meta:danger_action}")));
        return m;
    }

    // ------------------------------------------------------------------ 创建页与成员

    public static MenuBuilder members() {
        final MenuBuilder m = chrome("成员管理", "选择成员进入详情 · 原始玩家名仅用于指令参数", true);
        m.frame("inv-frame", 20, 64, 330, 44, LINE, 3, 1);
        m.text("inv-preview", "&#9AADA4点击输入玩家名邀请", 185, 86, 10, 5, CENTER, null);
        m.region("inv-input", 20, 64, 330, 44,
                MenuBuilder.spec(MenuBuilder.tooltip("&f点击唤起输入框，输入后回车发送邀请"),
                        MenuBuilder.click("all", "command: town ui-input invite")));
        button(m, "inv-go", 362, 64, 100, 44, GREEN, "&#0B1211邀请",
                "command: town ui-input invite");
        button(m, "inv-picker", 474, 64, 306, 44, RAISE, "&#F0EBDD在线玩家选择器",
                "command: town invitepicker 1");
        m.rect("member-head", 20, 118, 760, 24, RAISE, 2);
        memberHeader(m);
        for (int i = 0; i < 8; i++) {
            final double t = 144 + i * 30;
            m.rect("mem-bg-" + i, 20, t, 760, 28, PANEL, 1);
            m.text("mem-no-" + i, "&#9AADA4" + String.format("%02d", i + 1), 44, t + 14, 9, 5, CENTER, null);
            m.text("mem-name-" + i, "&#F0EBDD{meta:member_" + i + "_display}", 230, t + 14, 10, 5, CENTER, dyn());
            m.text("mem-role-" + i, "{meta:member_" + i + "_role_display}", 430, t + 14, 10, 5, CENTER, dyn());
            m.text("mem-online-" + i, "&#9AADA4{meta:member_" + i + "_online}", 566, t + 14, 9, 5, CENTER, dyn());
            m.rect("mem-manage-bg-" + i, 660, t + 3, 100, 22, RAISE, 2);
            m.text("mem-manage-t-" + i, "&#F0EBDD{meta:member_" + i + "_action}", 710, t + 14, 9, 5, CENTER, dyn());
            m.region("mem-manage-" + i, 660, t + 3, 100, 22,
                    MenuBuilder.conditionSpec("{meta:member_" + i + "_present} == 1", null,
                            MenuBuilder.click("all", "command: town member {meta:member_" + i + "_name}")));
        }
        pagination(m, "town members");
        return m;
    }

    private static void memberHeader(final MenuBuilder m) {
        m.text("member-head-no", "&#9AADA4#", 44, 130, 8, 5, CENTER, null);
        m.text("member-head-name", "&#9AADA4玩家", 230, 130, 8, 5, CENTER, null);
        m.text("member-head-role", "&#9AADA4职位", 430, 130, 8, 5, CENTER, null);
        m.text("member-head-online", "&#9AADA4状态", 566, 130, 8, 5, CENTER, null);
        m.text("member-head-action", "&#9AADA4操作", 710, 130, 8, 5, CENTER, null);
    }

    public static MenuBuilder memberDetail() {
        final MenuBuilder m = chrome("成员详情", "转让与踢出需要二次确认 · 离线成员也可转让", true);
        m.rect("profile", 20, 64, 360, 322, PANEL, 1);
        m.item("profile-icon", "player_head", 200, 116, 42, 4);
        m.text("profile-name", "&#F0EBDD{meta:selected_display}", 200, 166, 20, 5, CENTER, dyn());
        m.text("profile-uuid", "&#9AADA4{meta:selected_uuid}", 200, 194, 8, 5, CENTER, dyn());
        m.text("profile-role", "{meta:selected_role_display}", 200, 228, 12, 5, CENTER, dyn());
        m.text("profile-online", "&#9AADA4{meta:selected_online}", 200, 254, 10, 5, CENTER, dyn());
        m.text("profile-town", "&#9AADA4{meta:town_name}", 200, 306, 10, 5, CENTER, dyn());
        button(m, "promote", 396, 64, 184, 148, RAISE, "&#52C48B提升为官员",
                MenuBuilder.conditionSpec("{meta:selected_present} == 1", null,
                        MenuBuilder.click("all", "command: town promote {meta:selected_name}")));
        button(m, "demote", 596, 64, 184, 148, RAISE, "&#D8AE63降为成员",
                MenuBuilder.conditionSpec("{meta:selected_present} == 1", null,
                        MenuBuilder.click("all", "command: town demote {meta:selected_name}")));
        button(m, "transfer", 396, 228, 184, 158, ACC, "&#0B1211转让镇长",
                MenuBuilder.conditionSpec("{meta:selected_present} == 1", null,
                        MenuBuilder.click("all", "command: town confirm transfer {meta:selected_name}")));
        button(m, "kick", 596, 228, 184, 158, RAISE, "&#D97676踢出小镇",
                MenuBuilder.conditionSpec("{meta:selected_present} == 1", null,
                        MenuBuilder.click("all", "command: town confirm kick {meta:selected_name}")));
        return m;
    }

    public static MenuBuilder invitePicker() {
        final MenuBuilder m = chrome("邀请在线玩家", "只显示当前在线玩家 · 提交时再次校验状态", true);
        m.rect("picker-head", 20, 64, 760, 44, PANEL, 1);
        m.text("picker-tip", "&#9AADA4点选玩家发送邀请，已有小镇或邀请的玩家会标明状态", 40, 86, 9, 5, LEFT, null);
        m.rect("picker-columns", 20, 118, 760, 24, RAISE, 2);
        m.text("picker-head-no", "&#9AADA4#", 44, 130, 8, 5, CENTER, null);
        m.text("picker-head-name", "&#9AADA4在线玩家", 230, 130, 8, 5, CENTER, null);
        m.text("picker-head-state", "&#9AADA4当前状态", 500, 130, 8, 5, CENTER, null);
        m.text("picker-head-action", "&#9AADA4操作", 710, 130, 8, 5, CENTER, null);
        for (int i = 0; i < 8; i++) {
            final double t = 144 + i * 30;
            m.rect("candidate-bg-" + i, 20, t, 760, 28, PANEL, 1);
            m.text("candidate-no-" + i, "&#9AADA4" + String.format("%02d", i + 1), 44, t + 14, 9, 5, CENTER, null);
            m.text("candidate-name-" + i, "&#F0EBDD{meta:candidate_" + i + "_name}", 230, t + 14, 10, 5, CENTER, dyn());
            m.text("candidate-state-" + i, "{meta:candidate_" + i + "_state}", 500, t + 14, 9, 5, CENTER, dyn());
            m.rect("candidate-go-bg-" + i, 660, t + 3, 100, 22, RAISE, 2);
            m.text("candidate-go-t-" + i, "{meta:candidate_" + i + "_action}", 710, t + 14, 9, 5, CENTER, dyn());
            m.region("candidate-go-" + i, 660, t + 3, 100, 22,
                    MenuBuilder.conditionSpec("{meta:candidate_" + i + "_can} == 1", null,
                            MenuBuilder.click("all", "command: town invite {meta:candidate_" + i + "_name}")));
        }
        pagination(m, "town invitepicker");
        return m;
    }

    // ------------------------------------------------------------------ 圈地与祝福

    public static MenuBuilder claim() {
        final MenuBuilder m = chrome("领地地图", "左键购买 · 右键放弃 · 确认时会在世界中高亮目标区块", true);
        final double cell = 32;
        final double gap = 3;
        final double l0 = 20;
        final double t0 = 76;
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                final double l = l0 + c * (cell + gap);
                final double t = t0 + r * (cell + gap);
                m.rect("cell-bg-" + r + "-" + c, l, t, cell, cell, RAISE, 1);
                m.text("cell-" + r + "-" + c, "{meta:cell_" + r + "_" + c + "}",
                        l + cell / 2, t + cell / 2, 18, 5, CENTER, dyn());
                final JsonObject actions = new JsonObject();
                actions.add("left", MenuBuilder.actions("command: town confirm claimmap " + r + " " + c));
                actions.add("right", MenuBuilder.actions("command: town confirm unclaimat " + r + " " + c));
                m.region("cellgo-" + r + "-" + c, l, t, cell, cell,
                        MenuBuilder.spec(MenuBuilder.tooltip("&f左键：购买该区块", "&f右键：放弃本镇区块"), actions));
            }
        }
        m.frame("cell-mark", l0 + 4 * (cell + gap) - 1, t0 + 4 * (cell + gap) - 1,
                cell + 2, cell + 2, ACC, 3, 1);
        m.rect("side", 348, 64, 432, 324, PANEL, 1);
        m.text("side-town", "&#F0EBDD{meta:town_name}", 564, 92, 15, 5, CENTER, dyn());
        m.text("side-center", "&#9AADA4中心 {meta:claim_center}", 564, 118, 9, 5, CENTER, dyn());
        m.text("side-count", "&#9AADA4领地 &#F0EBDD{meta:claim_count} / {meta:claim_max}",
                564, 142, 10, 5, CENTER, dyn());
        legend(m, "own", 390, 172, "&#52C48B■", "本镇领地");
        legend(m, "open", 584, 172, "&#76B7C6□", "可购买");
        legend(m, "other", 390, 196, "&#D97676■", "他镇领地");
        legend(m, "far", 584, 196, "&#62736B·", "不相连");
        legend(m, "center", 390, 220, "&#D8AE63▣", "地图中心");
        legend(m, "denied", 584, 220, "&#C08A5A×", "禁圈世界");
        infoRow(m, "claim-price", "圈地单价", "{meta:claim_price}", 254, 384, 622);
        button(m, "move-left", 372, 278, 184, 30, RAISE, "&#F0EBDD◀ 左移 3 块",
                "command: town claimmap move -3 0");
        button(m, "move-right", 576, 278, 184, 30, RAISE, "&#F0EBDD右移 3 块 ▶",
                "command: town claimmap move 3 0");
        button(m, "move-up", 372, 314, 184, 30, RAISE, "&#F0EBDD▲ 上移 3 块",
                "command: town claimmap move 0 -3");
        button(m, "move-down", 576, 314, 184, 30, RAISE, "&#F0EBDD下移 3 块 ▼",
                "command: town claimmap move 0 3");
        button(m, "recenter", 372, 352, 388, 28, RAISE, "&#F0EBDD回到我的位置",
                "command: town claimmap center");
        return m;
    }

    private static void legend(final MenuBuilder m, final String id, final double x, final double cy,
                               final String mark, final String label) {
        m.text("lg-" + id + "-m", mark, x, cy, 12, 5, CENTER, null);
        m.text("lg-" + id, "&#9AADA4" + label, x + 16, cy, 9, 5, LEFT, null);
    }

    public static MenuBuilder buffs() {
        final MenuBuilder m = chrome("领地祝福", "祝福作用于身处本镇领地的成员 · 时长可叠加", true);
        m.rect("buff-intro", 20, 64, 760, 40, PANEL, 1);
        m.text("buff-intro-t", "&#9AADA4购买前确认费用与时长 · 永久祝福不可重复购买", 40, 84, 9, 5, LEFT, null);
        m.rect("buff-head", 20, 114, 760, 24, RAISE, 2);
        final String[] heads = {"图标", "祝福", "价格", "时长", "当前状态", "操作"};
        final double[] xs = {50, 205, 355, 480, 610, 725};
        for (int i = 0; i < heads.length; i++) {
            m.text("buff-head-" + i, "&#9AADA4" + heads[i], xs[i], 126, 8, 5, CENTER, null);
        }
        for (int i = 0; i < 6; i++) {
            final double t = 142 + i * 40;
            m.rect("bf-bg-" + i, 20, t, 760, 36, PANEL, 1);
            m.text("bf-icon-" + i, "&#D8AE63{meta:bf_" + i + "_icon}", 50, t + 18, 12, 5, CENTER, dyn());
            m.text("bf-name-" + i, "{meta:bf_" + i + "_name}", 205, t + 18, 11, 5, CENTER, dyn());
            m.text("bf-cost-" + i, "&#D8AE63{meta:bf_" + i + "_cost}", 355, t + 18, 10, 5, CENTER, dyn());
            m.text("bf-duration-" + i, "&#9AADA4{meta:bf_" + i + "_duration}", 480, t + 18, 9, 5, CENTER, dyn());
            m.text("bf-state-" + i, "{meta:bf_" + i + "_state}", 610, t + 18, 9, 5, CENTER, dyn());
            m.rect("bf-buy-bg-" + i, 690, t + 5, 70, 26, RAISE, 2);
            m.text("bf-buy-t-" + i, "{meta:bf_" + i + "_action}", 725, t + 18, 9, 5, CENTER, dyn());
            m.region("bf-buy-" + i, 690, t + 5, 70, 26,
                    MenuBuilder.conditionSpec("{meta:bf_" + i + "_can_buy} == 1", null,
                            MenuBuilder.click("all", "command: town confirm buff {meta:bf_" + i + "_id}")));
            m.region("bf-disabled-" + i, 690, t + 5, 70, 26,
                    MenuBuilder.conditionSpec("{meta:bf_" + i + "_present} == 1 && {meta:bf_"
                                    + i + "_can_buy} == 0", null,
                            MenuBuilder.click("all", "tell: &e该祝福正在生效中，无需重复购买。")));
        }
        pagination(m, "town buffs");
        return m;
    }

    // ------------------------------------------------------------------ 确认页

    public static MenuBuilder confirm() {
        final MenuBuilder m = chrome("确认操作", "确认对象、费用和后果 · 取消返回来源页面", false);
        m.rect("confirm-panel", 112, 76, 576, 298, PANEL, 1);
        m.rect("confirm-accent", 112, 76, 576, 2, ACC, 3);
        m.text("confirm-mark", "&#D8AE63!", 400, 118, 26, 5, CENTER, null);
        m.text("confirm-title", "&#F0EBDD{meta:confirm_title}", 400, 154, 20, 5, CENTER, dyn());
        m.text("confirm-body-1", "&#9AADA4{meta:confirm_body_1}", 400, 194, 10, 5, CENTER, dyn());
        m.text("confirm-body-2", "&#9AADA4{meta:confirm_body_2}", 400, 218, 10, 5, CENTER, dyn());
        m.text("confirm-cost", "&#D8AE63{meta:confirm_cost}", 400, 254, 20, 5, CENTER, dyn());
        m.text("confirm-note", "&#9AADA4{meta:confirm_note}", 400, 280, 9, 5, CENTER, dyn());
        button(m, "confirm-cancel", 156, 310, 220, 44, RAISE, "&#F0EBDD返回检查",
                "command: town confirm-cancel");
        button(m, "confirm-yes", 424, 310, 220, 44, GREEN, "&#0B1211确认执行",
                "command: town confirm {meta:confirm_action}");
        return m;
    }

    // ------------------------------------------------------------------ 输出

    public static Map<String, MenuBuilder> all(final TownConfig cfg) {
        final Map<String, MenuBuilder> all = new LinkedHashMap<>();
        all.put(HOME_NONE, homeNone());
        all.put(HOME_INVITED, homeInvited());
        all.put(CREATE, create(cfg));
        all.put(HOME_PENDING, homePending());
        all.put(HOME_ACTIVE, homeActive());
        all.put(MEMBERS, members());
        all.put(MEMBER_DETAIL, memberDetail());
        all.put(INVITE_PICKER, invitePicker());
        all.put(CLAIM, claim());
        all.put(BUFFS, buffs());
        all.put(CONFIRM, confirm());
        return all;
    }

    public static List<Written> writeAll(final TownConfig cfg, final Path dir) throws IOException {
        Files.createDirectories(dir);
        final List<Written> written = new ArrayList<>();
        for (final Map.Entry<String, MenuBuilder> entry : all(cfg).entrySet()) {
            final Path file = dir.resolve(entry.getKey() + ".yml");
            Files.writeString(file, entry.getValue().dump(entry.getKey()), StandardCharsets.UTF_8);
            written.add(new Written(entry.getKey(), file));
        }
        return written;
    }

    public static String miniToAmp(final String mini) {
        String s = mini
                .replace("<dark_red>", "&#AA0000").replace("<red>", "&#FF5555")
                .replace("<gold>", "&#FFAA00").replace("<yellow>", "&#FFFF55")
                .replace("<green>", "&#55FF55").replace("<dark_green>", "&#00AA00")
                .replace("<aqua>", "&#55FFFF").replace("<dark_aqua>", "&#00AAAA")
                .replace("<blue>", "&#5555FF").replace("<dark_blue>", "&#0000AA")
                .replace("<light_purple>", "&#FF55FF").replace("<dark_purple>", "&#AA00AA")
                .replace("<white>", "&#FFFFFF").replace("<gray>", "&#AAAAAA")
                .replace("<dark_gray>", "&#555555").replace("<black>", "&#000000");
        s = s.replaceAll("</?[A-Za-z_0-9:=-]+>", "");
        return s;
    }
}
