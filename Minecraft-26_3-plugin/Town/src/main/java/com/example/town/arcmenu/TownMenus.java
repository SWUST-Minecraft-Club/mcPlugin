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

/** 小镇 ArcMenu 页面：暖象牙卡片、玉绿按钮、黄铜点缀。 */
public final class TownMenus {

    public static final String BG = "#D8D2C4";
    public static final String PANEL = "#F4F0E4";
    public static final String RAISE = "#E5DDCB";
    public static final String LINE = "#4B4940";
    public static final String TEXT = "#343D38";
    public static final String MUTE = "#716F62";
    public static final String ACC = "#7D6338";
    public static final String GREEN = "#387C64";
    public static final String RED = "#AE5D51";
    public static final String CYAN = "#4E7880";
    public static final String GOLD = "#A37D38";
    public static final String WHITE = "#FFFFFF";

    public static final String HOME_NONE = "town-home-none";
    public static final String HOME_INVITED = "town-home-invited";
    public static final String HOME_PENDING = "town-home-pending";
    public static final String HOME_ACTIVE = "town-home-active";
    public static final String MEMBERS = "town-members";
    public static final String MEMBER_DETAIL = "town-member-detail";
    public static final String INVITE_PICKER = "town-invite-picker";
    public static final String CLAIM = "town-claim";
    public static final String CLAIM_WAITING = "town-claim-waiting";
    public static final String BUFFS = "town-buffs";
    public static final String CONFIRM = "town-confirm";
    public static final String CREATE = "town-create";

    public record Written(String id, Path file) {
    }

    private TownMenus() {
    }

    private static final int TOP = 52;
    private static final int BOT = 40;

    public static String amp(final String color) {
        return "&#" + color.substring(1);
    }

    static String t(final String color, final String text) {
        return amp(color) + text;
    }

    static JsonObject dyn() {
        return MenuBuilder.updateEvery(40);
    }

    static MenuBuilder chrome(final String title, final String hint, final boolean showBack) {
        return chrome(title, hint, showBack, false);
    }

    static MenuBuilder chrome(final String title, final String hint, final boolean showBack,
                              final boolean hintAboveContent) {
        final MenuBuilder m = new MenuBuilder();
        m.rect("bg", 0, 0, MenuBuilder.W, MenuBuilder.H, BG, 0);
        m.rect("bar-top", 0, 0, MenuBuilder.W, TOP, PANEL, 1);
        m.rect("bar-bot", 0, MenuBuilder.H - BOT, MenuBuilder.W, BOT, PANEL, 1);
        m.rect("line-top", 0, TOP, MenuBuilder.W, 2, LINE, 3);
        m.rect("line-bot", 0, MenuBuilder.H - BOT, MenuBuilder.W, 2, LINE, 3);
        m.item("brand-icon", "amethyst_shard", 34, 27, 25, 4);
        m.text("title", t(TEXT, title), 58, 28, 20, 5, LEFT, null);
        card(m, "balance-card", 584, 8, 158, 36, RAISE);
        m.text("bal-label", t(MUTE, "余额"), 600, 19, 9, 5, LEFT, null);
        m.text("bal-value", t(ACC, "{meta:balance}"), 704, 29, 12, 5, CENTER, dyn());
        card(m, "close-card", 752, 8, 36, 36, RAISE);
        m.text("close-x", t(TEXT, "×"), 770, 27, 18, 5, CENTER, null);
        m.region("close", 752, 8, 36, 36,
                MenuBuilder.spec(MenuBuilder.tooltip("&f关闭界面"), MenuBuilder.click("all", "close:")));
        m.text("hint", t(MUTE, hint), 20, hintAboveContent ? 59 : 430,
                hintAboveContent ? 9 : 10, 5, LEFT, null);
        if (showBack) {
            smallButton(m, "back", 664, 416, 116, 26, RAISE, "返回主页",
                    MenuBuilder.spec(MenuBuilder.tooltip("&f返回小镇主页"),
                            MenuBuilder.click("all", "command: town")));
        }
        return m;
    }

    static void card(final MenuBuilder m, final String id, final double l, final double t,
                             final double w, final double h, final String color) {
        m.rect(id + "-bg", l, t, w, h, color, 1);
        m.frame(id + "-frame", l, t, w, h, LINE, 3, 2);
    }

    static void card(final MenuBuilder m, final String id, final double l, final double t,
                             final double w, final double h) {
        card(m, id, l, t, w, h, PANEL);
    }

    private static String buttonText(final String color, final String label) {
        if (label.startsWith("&")) {
            return label;
        }
        return t(color.equals(GREEN) || color.equals(ACC) || color.equals(RED) || color.equals(CYAN)
                ? WHITE : TEXT, label);
    }

    static void button(final MenuBuilder m, final String id, final double l, final double t,
                               final double w, final double h, final String color, final String label,
                               final JsonObject spec) {
        m.rect(id + "-bg", l, t, w, h, color, 2);
        m.frame(id + "-frame", l, t, w, h, LINE, 3, 2);
        m.text(id + "-t", buttonText(color, label), l + w / 2, t + h / 2, 13, 5, CENTER, null);
        m.region(id, l, t, w, h, spec);
    }

    static void button(final MenuBuilder m, final String id, final double l, final double t,
                               final double w, final double h, final String color, final String label,
                               final String... actions) {
        button(m, id, l, t, w, h, color, label,
                MenuBuilder.spec(MenuBuilder.tooltip("&f" + MenuBuilder.stripCodes(label)),
                        MenuBuilder.click("all", actions)));
    }

    static void smallButton(final MenuBuilder m, final String id, final double l, final double t,
                                    final double w, final double h, final String color, final String label,
                                    final JsonObject spec) {
        m.rect(id + "-bg", l, t, w, h, color, 2);
        m.frame(id + "-frame", l, t, w, h, LINE, 3, 1);
        m.text(id + "-t", buttonText(color, label), l + w / 2, t + h / 2, 10, 5, CENTER, null);
        m.region(id, l, t, w, h, spec);
    }

    static void navCard(final MenuBuilder m, final String id, final double l, final double t,
                                final double w, final double h, final String material,
                                final String title, final String subtitle, final JsonObject spec) {
        card(m, id, l, t, w, h);
        m.item(id + "-icon", material, l + 30, t + h / 2, 34, 4);
        m.text(id + "-title", t(TEXT, title), l + 58, t + h / 2 - 13, 15, 5, LEFT, null);
        m.text(id + "-sub", t(MUTE, subtitle), l + 58, t + h / 2 + 15, 10, 5, LEFT, null);
        m.region(id, l, t, w, h, spec);
    }

    static void navCard(final MenuBuilder m, final String id, final double l, final double t,
                                final double w, final double h, final String material,
                                final String title, final String subtitle, final String... actions) {
        navCard(m, id, l, t, w, h, material, title, subtitle,
                MenuBuilder.spec(MenuBuilder.tooltip("&f" + title, "&7" + subtitle),
                        MenuBuilder.click("all", actions)));
    }

    static void infoRow(final MenuBuilder m, final String id, final String label,
                                final String value, final double cy, final double lx, final double vx) {
        m.text("i-" + id, t(MUTE, label), lx, cy, 12, 5, LEFT, null);
        m.text("v-" + id, t(TEXT, value), vx, cy, 12, 5, CENTER, dyn());
    }

    private static void pagination(final MenuBuilder m, final String command) {
        smallButton(m, "page-prev", 400, 416, 70, 26, RAISE, "上一页",
                MenuBuilder.conditionSpec("{meta:has_prev} == 1", null,
                        MenuBuilder.click("all", "command: " + command + " {meta:prev_page}")));
        smallButton(m, "page-next", 478, 416, 70, 26, RAISE, "下一页",
                MenuBuilder.conditionSpec("{meta:has_next} == 1", null,
                        MenuBuilder.click("all", "command: " + command + " {meta:next_page}")));
        m.text("page-now", t(MUTE, "{meta:page_now} / {meta:page_total}"), 586, 430, 10, 5, CENTER, dyn());
    }

    // ------------------------------------------------------------------ 主页

    public static MenuBuilder homeNone() {
        final MenuBuilder m = chrome("小镇系统", "创建后进入筹备期，邀请伙伴即可成立小镇", false);
        card(m, "hero", 20, 64, 370, 322);
        m.text("hero-kicker", t(ACC, "TOWN HALL"), 44, 98, 10, 5, LEFT, null);
        m.item("hero-icon", "bell", 336, 104, 38, 4);
        m.text("hero-title-1", t(TEXT, "从一块领地，"), 44, 150, 24, 5, LEFT, null);
        m.text("hero-title-2", t(TEXT, "建立你的小镇。"), 44, 186, 24, 5, LEFT, null);
        m.text("hero-sub-1", t(MUTE, "召集伙伴、划定领地，"), 44, 228, 12, 5, LEFT, null);
        m.text("hero-sub-2", t(MUTE, "让每一次建设都有归属。"), 44, 250, 12, 5, LEFT, null);
        button(m, "go-create", 44, 300, 310, 48, GREEN, "创建小镇　→", "command: town create");
        final String[] titles = {"登记小镇名称", "邀请首批成员", "落成镇政厅"};
        final String[] subs = {"独一无二的名字，是小镇的起点", "至少 {meta:min_members} 名成员，包含你自己", "支付成立费用，获得中心与初始领地"};
        for (int i = 0; i < 3; i++) {
            final double top = 64 + i * 112;
            card(m, "step-" + i, 406, top, 374, 98);
            m.text("step-no-" + i, t(ACC, "0" + (i + 1)), 430, top + 49, 22, 5, LEFT, null);
            m.text("step-title-" + i, t(TEXT, titles[i]), 482, top + 37, 15, 5, LEFT, null);
            m.text("step-sub-" + i, t(MUTE, subs[i]), 625, top + 64, 10, 5, CENTER, i==1?dyn():null);
        }
        return m;
    }

    public static MenuBuilder homeInvited() {
        final MenuBuilder m = chrome("小镇邀请", "接受前会再次检查邀请、成员上限和所属小镇", false);
        card(m, "invite-main", 20, 64, 500, 322);
        card(m, "invite-side", 536, 64, 244, 322);
        m.text("invite-kicker", t(ACC, "INVITATION"), 48, 102, 10, 5, LEFT, null);
        m.item("invite-icon", "writable_book", 458, 108, 40, 4);
        m.text("invite-town", t(TEXT, "{meta:invited_from}"), 270, 160, 24, 5, CENTER, dyn());
        m.text("invite-mayor", t(MUTE, "镇长 {meta:invited_mayor}"), 270, 200, 12, 5, CENTER, dyn());
        m.text("invite-members", t(MUTE, "当前 {meta:invited_members} 名成员"), 270, 228, 12, 5, CENTER, dyn());
        m.text("invite-expire", t(ACC, "{meta:invited_expiry}"), 270, 270, 13, 5, CENTER, dyn());
        m.text("invite-note", t(MUTE, "加入后即可参与筹备与建设"), 270, 316, 11, 5, CENTER, null);
        m.text("invite-question", t(TEXT, "要接受这份邀请吗？"), 658, 118, 15, 5, CENTER, null);
        button(m, "invite-accept", 564, 180, 188, 48, GREEN, "接受邀请", "command: town accept");
        button(m, "invite-reject", 564, 244, 188, 44, RAISE, t(RED, "礼貌拒绝"), "command: town reject");
        return m;
    }

    public static MenuBuilder create(final TownConfig cfg) {
        final MenuBuilder m = chrome("创建小镇", "名称 2~12 个字符 · 支持中文、字母、数字、下划线", true);
        card(m, "rules", 20, 64, 300, 322);
        m.text("rules-kicker", t(ACC, "RULES"), 44, 96, 10, 5, LEFT, null);
        m.text("rules-title", t(TEXT, "登记须知"), 44, 128, 17, 5, LEFT, null);
        infoRow(m, "name", "名称长度", "{meta:name_len}", 170, 44, 240);
        infoRow(m, "members", "首镇成员", "至少 {meta:min_members} 人", 208, 44, 240);
        infoRow(m, "cost", "成立费用", "{meta:found_cost}", 246, 44, 240);
        m.text("rules-free", t(MUTE, "初始领地"), 44, 284, 12, 5, LEFT, null);
        m.text("rules-free-v", t(TEXT, "镇政厅周围免费圈地"), 230, 284, 12, 5, CENTER, null);
        card(m, "form", 336, 64, 444, 322);
        m.text("form-kicker", t(ACC, "NEW TOWN"), 368, 100, 10, 5, LEFT, null);
        m.text("form-title", t(TEXT, "小镇叫什么？"), 368, 138, 22, 5, LEFT, null);
        m.frame("in-frame", 368, 174, 380, 48, ACC, 3, 2);
        m.text("in-preview", t(MUTE, "点击这里输入小镇名称"), 558, 198, 12, 5, CENTER, null);
        m.region("in-name", 368, 174, 380, 48,
                MenuBuilder.spec(MenuBuilder.tooltip("&f点击唤起输入框，输入后回车提交"),
                        MenuBuilder.click("all", "command: town ui-input name")));
        button(m, "go", 368, 248, 380, 46, GREEN, "输入名称并开始筹备",
                "command: town ui-input name");
        m.text("form-note", t(MUTE, "输入后按回车提交；现在只登记名称，成立时才扣费"), 558, 326, 10, 5, CENTER, null);
        return m;
    }

    public static MenuBuilder homePending() {
        final MenuBuilder m = chrome("筹备中的小镇", "成员达到要求后，确认费用即可正式成立", false);
        final String[] steps = {"创建档案", "邀请成员", "正式成立", "落成镇政厅"};
        for (int i = 0; i < 4; i++) {
            final double l = 20 + i * 192;
            final String color = i == 0 ? GREEN : i == 1 ? ACC : MUTE;
            card(m, "progress-" + i, l, 64, 184, 44);
            m.rect("progress-line-" + i, l, 106, 184, 3, color, 4);
            m.text("progress-t-" + i, t(color, (i + 1) + " · " + steps[i]), l + 92, 86, 12, 5, CENTER, null);
        }
        card(m, "info", 20, 122, 370, 264);
        m.text("town-name", t(TEXT, "{meta:town_name}"), 205, 154, 20, 5, CENTER, dyn());
        infoRow(m, "mayor", "镇长", "{meta:mayor_name}", 198, 44, 292);
        infoRow(m, "members", "成员进度", "{meta:member_count} / {meta:min_members} 人", 236, 44, 292);
        infoRow(m, "cost", "成立费用", "{meta:found_cost}", 274, 44, 292);
        infoRow(m, "ready", "筹备状态", "{meta:found_state}", 312, 44, 292);
        m.text("pending-note", t(MUTE, "筹备超时将自动解散"), 205, 352, 10, 5, CENTER, null);
        button(m, "b-invite", 406, 122, 374, 48, GREEN, "邀请在线玩家", "command: town invite");
        button(m, "b-members", 406, 180, 374, 44, RAISE, "管理现有成员", "command: town members");
        button(m, "b-found", 406, 234, 374, 48, GREEN, "正式成立 · {meta:found_cost}",
                MenuBuilder.conditionSpec("{meta:can_found} == 1",
                        MenuBuilder.tooltip("&f支付成立费用，正式成立小镇"),
                        MenuBuilder.click("all", "command: town confirm found"),
                        MenuBuilder.actions("tell: &e成员不足，达到 {meta:min_members} 人后才能成立。")));
        button(m, "b-disband", 406, 292, 374, 44, RAISE, t(RED, "放弃筹备"),
                "command: town confirm disband");
        return m;
    }

    public static MenuBuilder homeActive() {
        final MenuBuilder m = chrome("小镇政务厅", "管理成员、领地与祝福 · 镇政厅是小镇中心", false);
        card(m, "town-head", 20, 64, 760, 64);
        m.item("town-icon", "bell", 50, 96, 34, 4);
        m.text("town-name", t(TEXT, "{meta:town_name}"), 278, 86, 20, 5, CENTER, dyn());
        m.text("town-mayor", t(MUTE, "镇长 {meta:mayor_name} · 成立于 {meta:created_date}"),
                278, 110, 10, 5, CENTER, dyn());
        m.text("stat-members", t(TEXT, "{meta:member_count}") + t(MUTE, " 成员"), 500, 96, 12, 5, CENTER, dyn());
        m.text("stat-claims", t(TEXT, "{meta:claim_count}") + t(MUTE, " 领地"), 604, 96, 12, 5, CENTER, dyn());
        m.text("stat-buffs", t(TEXT, "{meta:buff_count}") + t(MUTE, " 祝福"), 708, 96, 12, 5, CENTER, dyn());
        navCard(m,"b-members",20,140,244,92,"writable_book","成员管理","邀请伙伴与职务","command: town members");
        navCard(m,"b-claim",278,140,244,92,"map","领土地图","自主选择相连区块","command: town claimmap center");
        navCard(m,"b-plots",536,140,244,92,"oak_door","地块与租赁","住宅、商铺与授权","command: town manage plots");
        navCard(m,"b-civic",20,244,244,92,"emerald","镇库与发展","科技、政策与贡献","command: town manage civic");
        navCard(m,"b-market",278,244,244,92,"chest","商业街","现货、订单与收件箱","command: town manage market");
        navCard(m,"b-planning",536,244,244,92,"brush","城镇规划","建筑迁移与工程验收","command: town manage planning");
        button(m,"b-spawn",20,348,176,38,GREEN,"回到小镇","close:","command: town spawn");
        button(m,"b-buffs",210,348,176,38,RAISE,"领地祝福","command: town buffs");
        button(m,"b-approvals",400,348,176,38,RAISE,"待办审批","command: town manage approvals");
        button(m,"danger",590,348,190,38,RAISE,t(RED,"{meta:danger_label}"),"command: town confirm {meta:danger_action}");
        return m;
    }

    // ------------------------------------------------------------------ 成员与邀请

    public static MenuBuilder members() {
        final MenuBuilder m = chrome("成员管理", "选择成员进入详情 · 原始玩家名仅用于指令参数", true);
        m.frame("inv-frame", 20, 64, 330, 44, LINE, 3, 2);
        m.text("inv-preview", t(MUTE, "点击输入玩家名邀请"), 185, 86, 12, 5, CENTER, null);
        m.region("inv-input", 20, 64, 330, 44,
                MenuBuilder.spec(MenuBuilder.tooltip("&f点击唤起输入框，输入后回车发送邀请"),
                        MenuBuilder.click("all", "command: town ui-input invite")));
        button(m, "inv-go", 362, 64, 100, 44, GREEN, "邀请", "command: town ui-input invite");
        button(m, "inv-picker", 474, 64, 306, 44, RAISE, "在线玩家选择器",
                "command: town invitepicker 1");
        card(m, "member-head", 20, 118, 760, 24, RAISE);
        memberHeader(m);
        for (int i = 0; i < 8; i++) {
            final double top = 144 + i * 30;
            card(m, "mem-" + i, 20, top, 760, 28);
            m.text("mem-no-" + i, t(MUTE, String.format("%02d", i + 1)), 44, top + 14, 10, 5, CENTER, null);
            m.text("mem-name-" + i, t(TEXT, "{meta:member_" + i + "_display}"), 230, top + 14, 12, 5, CENTER, dyn());
            m.text("mem-role-" + i, "{meta:member_" + i + "_role_display}", 430, top + 14, 12, 5, CENTER, dyn());
            m.text("mem-online-" + i, t(MUTE, "{meta:member_" + i + "_online}"), 566, top + 14, 11, 5, CENTER, dyn());
            smallButton(m, "mem-manage-" + i, 660, top + 3, 100, 22, RAISE, "{meta:member_" + i + "_action}",
                    MenuBuilder.conditionSpec("{meta:member_" + i + "_present} == 1", null,
                            MenuBuilder.click("all", "command: town member {meta:member_" + i + "_name}")));
        }
        pagination(m, "town members");
        return m;
    }

    private static void memberHeader(final MenuBuilder m) {
        m.text("member-head-no", t(MUTE, "#"), 44, 130, 10, 5, CENTER, null);
        m.text("member-head-name", t(MUTE, "玩家"), 230, 130, 10, 5, CENTER, null);
        m.text("member-head-role", t(MUTE, "职位"), 430, 130, 10, 5, CENTER, null);
        m.text("member-head-online", t(MUTE, "状态"), 566, 130, 10, 5, CENTER, null);
        m.text("member-head-action", t(MUTE, "操作"), 710, 130, 10, 5, CENTER, null);
    }

    public static MenuBuilder memberDetail() {
        final MenuBuilder m = chrome("成员详情", "转让与踢出需要二次确认 · 离线成员也可转让", true);
        card(m, "profile", 20, 64, 360, 322);
        m.item("profile-icon", "player_head", 200, 116, 46, 4);
        m.text("profile-name", t(TEXT, "{meta:selected_display}"), 200, 168, 22, 5, CENTER, dyn());
        m.text("profile-uuid", t(MUTE, "{meta:selected_uuid}"), 200, 198, 10, 5, CENTER, dyn());
        m.text("profile-role", "{meta:selected_role_display}", 200, 232, 14, 5, CENTER, dyn());
        m.text("profile-online", t(MUTE, "{meta:selected_online}"), 200, 260, 12, 5, CENTER, dyn());
        m.text("profile-town", t(MUTE, "{meta:town_name}"), 200, 310, 12, 5, CENTER, dyn());
        navCard(m, "promote", 396, 64, 184, 148, "emerald", "提升为官员", "任命管理成员",
                MenuBuilder.conditionSpec("{meta:selected_present} == 1", null,
                        MenuBuilder.click("all", "command: town promote {meta:selected_name}")));
        navCard(m, "demote", 596, 64, 184, 148, "iron_ingot", "降为成员", "保留小镇身份",
                MenuBuilder.conditionSpec("{meta:selected_present} == 1", null,
                        MenuBuilder.click("all", "command: town demote {meta:selected_name}")));
        navCard(m, "transfer", 396, 228, 184, 158, "golden_helmet", "转让镇长", "移交小镇所有权",
                MenuBuilder.conditionSpec("{meta:selected_present} == 1", null,
                        MenuBuilder.click("all", "command: town confirm transfer {meta:selected_name}")));
        navCard(m, "kick", 596, 228, 184, 158, "barrier", "踢出小镇", "需要再次确认",
                MenuBuilder.conditionSpec("{meta:selected_present} == 1", null,
                        MenuBuilder.click("all", "command: town confirm kick {meta:selected_name}")));
        return m;
    }

    public static MenuBuilder invitePicker() {
        final MenuBuilder m = chrome("邀请在线玩家", "只显示当前在线玩家 · 提交时再次校验状态", true);
        card(m, "picker-head", 20, 64, 760, 44);
        m.text("picker-tip", t(MUTE, "点选玩家发送邀请，已有小镇或邀请的玩家会标明状态"), 40, 86, 11, 5, LEFT, null);
        card(m, "picker-columns", 20, 118, 760, 24, RAISE);
        m.text("picker-head-no", t(MUTE, "#"), 44, 130, 10, 5, CENTER, null);
        m.text("picker-head-name", t(MUTE, "在线玩家"), 230, 130, 10, 5, CENTER, null);
        m.text("picker-head-state", t(MUTE, "当前状态"), 500, 130, 10, 5, CENTER, null);
        m.text("picker-head-action", t(MUTE, "操作"), 710, 130, 10, 5, CENTER, null);
        for (int i = 0; i < 8; i++) {
            final double top = 144 + i * 30;
            card(m, "candidate-" + i, 20, top, 760, 28);
            m.text("candidate-no-" + i, t(MUTE, String.format("%02d", i + 1)), 44, top + 14, 10, 5, CENTER, null);
            m.text("candidate-name-" + i, t(TEXT, "{meta:candidate_" + i + "_name}"), 230, top + 14, 12, 5, CENTER, dyn());
            m.text("candidate-state-" + i, "{meta:candidate_" + i + "_state}", 500, top + 14, 11, 5, CENTER, dyn());
            smallButton(m, "candidate-go-" + i, 660, top + 3, 100, 22, RAISE, "{meta:candidate_" + i + "_action}",
                    MenuBuilder.conditionSpec("{meta:candidate_" + i + "_can} == 1", null,
                            MenuBuilder.click("all", "command: town invite {meta:candidate_" + i + "_name}")));
        }
        pagination(m, "town invitepicker");
        return m;
    }

    // ------------------------------------------------------------------ 圈地与祝福

    public static MenuBuilder claim() {
        return claim(true);
    }

    private static MenuBuilder claim(boolean terrain) {
        final MenuBuilder m = chrome("领地地图", "真实渲染地形 · 角标=领地状态 · 确认时目标区块会标出", true);
        final int n = TownUi.CLAIM_GRID;
        final double cell = 44;
        final double gap = 3;
        final double l0 = 20;
        final double t0 = 68;
        final double grid = n * cell + (n - 1) * gap;
        if (terrain) {
            m.image("map-image", "/town/map/%arcmenu_meta_map_key%.png", l0, t0, grid, grid, 1, 20);
        } else {
            for (int r = 0; r < n; r++) for (int c = 0; c < n; c++)
                m.rect("cell-bg-" + r + "-" + c, l0 + c * (cell + gap), t0 + r * (cell + gap),
                        cell, cell, PANEL, 1);
        }
        m.frame("map-frame", l0 - 2, t0 - 2, grid + 4, grid + 4, LINE, 3, 2);
        m.frame("cell-mark", l0 + (n / 2) * (cell + gap) - 1, t0 + (n / 2) * (cell + gap) - 1,
                cell + 2, cell + 2, GOLD, 3, 2);
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                final double l = l0 + c * (cell + gap);
                final double t = t0 + r * (cell + gap);
                m.text("cellmark-" + r + "-" + c, "{meta:cellmark_" + r + "_" + c + "}",
                        l + cell - 11, t + cell - 12, 16, 5, CENTER, dyn());
                final JsonObject actions = new JsonObject();
                actions.add("left", MenuBuilder.actions("command: town confirm claimmap " + r + " " + c));
                actions.add("right", MenuBuilder.actions("command: town confirm unclaimat " + r + " " + c));
                m.region("cellgo-" + r + "-" + c, l, t, cell, cell,
                        MenuBuilder.spec(MenuBuilder.tooltip("&f左键：购买该区块", "&f右键：放弃本镇区块"), actions));
            }
        }
        final double sideX = l0 + grid + 10;
        final double sideW = 780 - sideX;
        card(m, "side", sideX, 64, sideW, 330);
        final double sx = sideX + 18;
        final double sw = sideW - 36;
        m.text("side-town", t(TEXT, "{meta:town_name}"), sx + sw / 2, 92, 17, 5, CENTER, dyn());
        m.text("side-center", t(MUTE, "中心 {meta:claim_center}"), sx + sw / 2, 118, 11, 5, CENTER, dyn());
        m.text("side-count", t(MUTE, "领地 ") + t(TEXT, "{meta:claim_count} / {meta:claim_max}"),
                sx + sw / 2, 142, 12, 5, CENTER, dyn());
        m.text("legend-terrain-title", t(ACC, "{meta:map_status}"), sx + sw / 2, 174, 11, 5, CENTER, dyn());
        m.text("legend-terrain-1", t(MUTE, "逐方块渲染，颜色随群系 / 水深 / 高度变化"),
                sx, 196, 10, 5, LEFT, null);
        m.text("legend-terrain-2", t(MUTE, "深绿=林地　蓝=水　黄=沙　灰=岩　白=雪"),
                sx, 216, 10, 5, LEFT, null);
        m.text("legend-state-title", t(ACC, "状态角标"), sx, 244, 11, 5, LEFT, null);
        m.text("legend-state", t(GREEN, "●本镇　") + t(CYAN, "○可买　") + t(RED, "●他镇　") + t(GOLD, "★镇政厅"),
                sx, 266, 11, 5, LEFT, null);
        infoRow(m, "claim-price", "圈地单价", "{meta:claim_price}", 298, sx, sx + sw - 40);
        smallButton(m, "move-left", sx, 322, 88, 26, RAISE, "◀ 左移",
                MenuBuilder.spec(null, MenuBuilder.click("all", "command: town claimmap move -3 0")));
        smallButton(m, "move-right", sx + 94, 322, 88, 26, RAISE, "右移 ▶",
                MenuBuilder.spec(null, MenuBuilder.click("all", "command: town claimmap move 3 0")));
        smallButton(m, "move-up", sx, 354, 88, 26, RAISE, "▲ 上移",
                MenuBuilder.spec(null, MenuBuilder.click("all", "command: town claimmap move 0 -3")));
        smallButton(m, "move-down", sx + 94, 354, 88, 26, RAISE, "下移 ▼",
                MenuBuilder.spec(null, MenuBuilder.click("all", "command: town claimmap move 0 3")));
        button(m, "recenter", sx + 188, 322, sw - 188, 58, RAISE, "回到我的位置",
                "command: town claimmap center");
        return m;
    }

    public static MenuBuilder buffs() {
        final MenuBuilder m = chrome("领地祝福", "祝福作用于身处本镇领地的成员 · 时长可叠加", true);
        card(m, "buff-intro", 20, 64, 760, 40);
        m.text("buff-intro-t", t(MUTE, "购买前确认费用与时长 · 永久祝福不可重复购买"), 40, 84, 11, 5, LEFT, null);
        card(m, "buff-head", 20, 114, 760, 24, RAISE);
        final String[] heads = {"图标", "祝福", "价格", "时长", "当前状态", "操作"};
        final double[] xs = {50, 205, 355, 480, 610, 725};
        for (int i = 0; i < heads.length; i++) {
            m.text("buff-head-" + i, t(MUTE, heads[i]), xs[i], 126, 10, 5, CENTER, null);
        }
        for (int i = 0; i < 6; i++) {
            final double top = 142 + i * 40;
            card(m, "bf-" + i, 20, top, 760, 36);
            m.text("bf-icon-" + i, t(ACC, "{meta:bf_" + i + "_icon}"), 50, top + 18, 14, 5, CENTER, dyn());
            m.text("bf-name-" + i, "{meta:bf_" + i + "_name}", 205, top + 18, 12, 5, CENTER, dyn());
            m.text("bf-cost-" + i, t(ACC, "{meta:bf_" + i + "_cost}"), 355, top + 18, 12, 5, CENTER, dyn());
            m.text("bf-duration-" + i, t(MUTE, "{meta:bf_" + i + "_duration}"), 480, top + 18, 11, 5, CENTER, dyn());
            m.text("bf-state-" + i, "{meta:bf_" + i + "_state}", 610, top + 18, 11, 5, CENTER, dyn());
            smallButton(m, "bf-buy-" + i, 690, top + 5, 70, 26, RAISE, "{meta:bf_" + i + "_action}",
                    MenuBuilder.conditionSpec("{meta:bf_" + i + "_can_buy} == 1", null,
                            MenuBuilder.click("all", "command: town confirm buff {meta:bf_" + i + "_id}")));
            m.region("bf-disabled-" + i, 690, top + 5, 70, 26,
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
        card(m, "confirm-panel", 112, 76, 576, 298);
        m.rect("confirm-accent", 112, 76, 576, 3, ACC, 4);
        m.text("confirm-mark", t(ACC, "!"), 400, 120, 28, 5, CENTER, null);
        m.text("confirm-title", t(TEXT, "{meta:confirm_title}"), 400, 156, 22, 5, CENTER, dyn());
        m.text("confirm-body-1", t(MUTE, "{meta:confirm_body_1}"), 400, 196, 12, 5, CENTER, dyn());
        m.text("confirm-body-2", t(MUTE, "{meta:confirm_body_2}"), 400, 220, 12, 5, CENTER, dyn());
        m.text("confirm-cost", t(ACC, "{meta:confirm_cost}"), 400, 256, 22, 5, CENTER, dyn());
        m.text("confirm-note", t(MUTE, "{meta:confirm_note}"), 400, 282, 11, 5, CENTER, dyn());
        button(m, "confirm-cancel", 156, 310, 220, 44, RAISE, "返回检查",
                "command: town confirm-cancel");
        button(m, "confirm-yes", 424, 310, 220, 44, GREEN, "确认执行",
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
        all.put(CLAIM_WAITING, claim(false));
        all.put(BUFFS, buffs());
        all.put(CONFIRM, confirm());
        all.putAll(TownOperationMenus.all());
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
