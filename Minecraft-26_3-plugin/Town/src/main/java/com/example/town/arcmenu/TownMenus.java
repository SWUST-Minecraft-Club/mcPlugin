package com.example.town.arcmenu;

import com.example.town.TownConfig;
import com.example.town.TownPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.example.town.arcmenu.MenuBuilder.Align.CENTER;
import static com.example.town.arcmenu.MenuBuilder.Align.LEFT;

/**
 * 小镇全部菜单的生成器：按 arcmenu-designing 技能的规范书写 800×450 设计坐标，
 * 由 {@link MenuBuilder} 输出实测补偿后的 YAML（JSON 形态），写到 plugins/ArcMenu/menus/ 供 ArcMenu 加载。
 *
 * <p>版式沿用技能自带商店示例的设计语言：深色底 + 顶栏 / 底栏 + 左信息右操作 + 金色强调。
 * 动态值一律走 {meta:…} 占位符（打开前由 {@link TownUi} 写入玩家 META），点击走 /town 指令等价通道。
 */
public final class TownMenus {

    // 配色（深色主题）
    public static final String BG = "#121826";
    public static final String PANEL = "#1C2540";
    public static final String RAISE = "#242F4F";
    public static final String LINE = "#2E3A5C";
    public static final String TEXT = "#E8ECF7";
    public static final String MUTE = "#8A93B2";
    public static final String ACC = "#F0B429";
    public static final String GREEN = "#2E7D5B";
    public static final String RED = "#8A3242";
    public static final String CYAN = "#2C6E8A";
    public static final String DIM = "#0B0F1A";

    public static final String HOME_NONE = "town-home-none";
    public static final String HOME_PENDING = "town-home-pending";
    public static final String HOME_ACTIVE = "town-home-active";
    public static final String MEMBERS = "town-members";
    public static final String CLAIM = "town-claim";
    public static final String BUFFS = "town-buffs";
    public static final String CREATE = "town-create";

    /** 一份生成结果（菜单 id ↔ 文件）。 */
    public record Written(String id, Path file) {
    }

    private TownMenus() {
    }

    // ------------------------------------------------------------------ 公共壳

    private static final int TOP = 48;
    private static final int BOT = 40;

    /** 底色 + 顶栏（标题 + 余额）+ 底栏（提示 + 关闭）。 */
    private static MenuBuilder chrome(final MenuBuilder m, final String title) {
        m.rect("bg", 0, 0, MenuBuilder.W, MenuBuilder.H, BG, 0);
        m.rect("bar-top", 0, 0, MenuBuilder.W, TOP, PANEL, 1);
        m.rect("bar-bot", 0, MenuBuilder.H - BOT, MenuBuilder.W, BOT, PANEL, 1);
        m.rect("line-top", 0, TOP, MenuBuilder.W, 1, LINE, 3);
        m.rect("line-bot", 0, MenuBuilder.H - BOT, MenuBuilder.W, 1, LINE, 3);
        m.text("title", "&#F0B429" + title, 24, TOP / 2 + 1, 15, 5, LEFT, null);
        // 余额（动态，居中）
        m.text("bal-label", "&#8A93B2余额", MenuBuilder.W - 200, 16, 8, 5, CENTER, null);
        m.text("bal-value", "&#F0B429{meta:balance}", MenuBuilder.W - 200, 33, 12, 5, CENTER,
                MenuBuilder.updateEvery(40));
        // 关闭
        m.text("close-x", "&#8A93B2×", MenuBuilder.W - 28, TOP / 2 + 1, 14, 5, CENTER, null);
        m.region("close", MenuBuilder.W - 56, 0, 56, TOP,
                MenuBuilder.spec(MenuBuilder.tooltip("&f关闭界面"),
                        MenuBuilder.click("all", "close")));
        return m;
    }

    /** 底部提示行。 */
    private static void hint(final MenuBuilder m, final String text) {
        m.text("hint", "&#5A6480" + text, MenuBuilder.W / 2, MenuBuilder.H - BOT / 2, 9, 5, CENTER, null);
    }

    /** 标准按钮：底色 + 文字 + 命中区。 */
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
        button(m, id, l, t, w, h, color, label, MenuBuilder.spec(null, MenuBuilder.click("left", actions)));
    }

    // ------------------------------------------------------------------ 各界面

    /** 无镇主页。 */
    public static MenuBuilder homeNone(final TownPlugin plugin) {
        final MenuBuilder m = chrome(new MenuBuilder(), "小镇系统");
        m.text("hero", "&#E8ECF7创 立 小 镇", MenuBuilder.W / 2, 140, 30, 5, CENTER, null);
        m.text("sub", "&#8A93B2召集伙伴 · 圈地建国 · 领地祝福", MenuBuilder.W / 2, 178, 12, 5, CENTER, null);
        m.text("rule-1", "&#8A93B21. 起一个响亮的名字（{meta:name_len}）", MenuBuilder.W / 2, 214, 10, 5, CENTER, null);
        m.text("rule-2", "&#8A93B22. 邀请至少 {meta:min_members} 名成员加入", MenuBuilder.W / 2, 234, 10, 5, CENTER, null);
        m.text("rule-3", "&#8A93B23. 成立支付 {meta:found_cost}，领取镇政厅 · 免费圈地", MenuBuilder.W / 2, 254, 10, 5, CENTER, null);
        button(m, "go-create", MenuBuilder.W / 2 - 110, 288, 220, 46, ACC,
                "&#121826创建小镇", "open: " + CREATE);
        // 有邀请时点亮「查看邀请」
        m.text("inv-line", "&#38BDF8你有一份小镇邀请待处理", MenuBuilder.W / 2, 356, 11, 5, CENTER,
                MenuBuilder.updateEvery(40));
        m.region("go-invite", MenuBuilder.W / 2 - 110, 372, 220, 40,
                MenuBuilder.conditionSpec("{meta:invited_from} != null",
                        MenuBuilder.tooltip("&f查看邀请"),
                        MenuBuilder.click("all", "open: " + MEMBERS)));
        hint(m, "加入小镇：让有小镇的朋友邀请你");
        return m;
    }

    /** 筹备中主页。 */
    public static MenuBuilder homePending() {
        final MenuBuilder m = chrome(new MenuBuilder(), "小镇 · 筹备中");
        // 左信息面板
        m.rect("info", 60, 84, 300, 280, PANEL, 1);
        m.text("t-name", "&#E8ECF7{meta:town_name}", 210, 124, 20, 5, CENTER, MenuBuilder.updateEvery(40));
        m.rect("t-line", 90, 148, 240, 1, LINE, 3);
        infoRow(m, "成员", "{meta:member_count} / {meta:min_members}", 180);
        infoRow(m, "成立费", "{meta:found_cost}", 212);
        infoRow(m, "状态", "&#F0B429筹备中", 244);
        infoRow(m, "镇长", "{meta:mayor_name}", 276);
        // 右按钮列
        button(m, "b-invite", 420, 84, 320, 46, CYAN, "&#E8ECF7邀请成员", "open: " + MEMBERS);
        m.text("found-hint-no", "&#F87171成员不足，达到 {meta:min_members} 人后可成立",
                580, 160, 10, 5, CENTER, MenuBuilder.updateEvery(40));
        // 「成立」按钮：条件不满足时静默无效
        m.rect("b-found-bg", 420, 178, 320, 46, GREEN, 2);
        m.text("b-found-t", "&#E8ECF7成立小镇（支付 {meta:found_cost}）", 580, 201, 11, 5, CENTER, null);
        m.region("b-found", 420, 178, 320, 46,
                MenuBuilder.conditionSpec("{meta:can_found} == 1",
                        MenuBuilder.tooltip("&f确认支付后正式成立"),
                        MenuBuilder.click("all", "command: town found")));
        button(m, "b-disband", 420, 240, 320, 46, RED,
                "&#E8ECF7放弃筹备（再次点击确认）", "command: town confirm disband");
        hint(m, "邀请需要的成员数后即可成立");
        return m;
    }

    /** 已成立主页。 */
    public static MenuBuilder homeActive() {
        final MenuBuilder m = chrome(new MenuBuilder(), "小镇 · {meta:town_name}");
        m.rect("info", 60, 84, 300, 300, PANEL, 1);
        m.text("t-name", "&#E8ECF7{meta:town_name}", 210, 118, 20, 5, CENTER, MenuBuilder.updateEvery(40));
        m.rect("t-line", 90, 142, 240, 1, LINE, 3);
        infoRow(m, "镇长", "{meta:mayor_name}", 168);
        infoRow(m, "成员", "{meta:member_count} 人", 202);
        infoRow(m, "领地", "{meta:claim_count} 块", 236);
        infoRow(m, "祝福", "{meta:buff_count} 个生效", 270);
        infoRow(m, "镇政厅", "{meta:hall_state}", 304);
        infoRow(m, "成立", "{meta:created_date}", 338);
        // 右按钮列
        final double x = 420;
        final double w = 320;
        button(m, "b-members", x, 84, w, 46, CYAN, "&#E8ECF7成员管理", "open: " + MEMBERS);
        button(m, "b-claim", x, 142, w, 46, CYAN, "&#E8ECF7领地地图（点选圈地）", "open: " + CLAIM);
        button(m, "b-buffs", x, 200, w, 46, CYAN, "&#E8ECF7领地祝福", "open: " + BUFFS);
        // 回小镇（未设传送点时无效）
        m.rect("b-spawn-bg", x, 258, w, 46, RAISE, 2);
        m.text("b-spawn-t", "&#E8ECF7回小镇", x + w / 2, 281, 11, 5, CENTER, null);
        m.region("b-spawn", x, 258, w, 46,
                MenuBuilder.conditionSpec("{meta:has_spawn} == 1",
                        MenuBuilder.tooltip("&f传送到小镇传送点"),
                        MenuBuilder.click("all", "command: town spawn")));
        // 退出 / 解散（按身份二选一）
        m.rect("b-leave-bg", x, 316, w, 46, RED, 2);
        // frontend 文本不支持 condition，用中性文案；动作仍按身份二选一
        m.text("b-leave-label", "&#E8ECF7解散 / 退出小镇（再次点击确认）", x + w / 2, 339, 11, 5,
                CENTER, null);
        m.region("b-leave-mayor", x, 316, w, 46,
                MenuBuilder.conditionSpec("{meta:is_mayor} == 1",
                        MenuBuilder.tooltip("&f解散后领地全部释放"),
                        MenuBuilder.click("all", "command: town confirm disband")));
        m.region("b-leave-member", x, 316, w, 46,
                MenuBuilder.conditionSpec("{meta:is_mayor} == 0",
                        MenuBuilder.tooltip("&f退出当前小镇"),
                        MenuBuilder.click("all", "command: town confirm leave")));
        hint(m, "圈地请进「领地地图」点选相邻区块");
        return m;
    }

    private static void infoRow(final MenuBuilder m, final String label, final String value, final double cy) {
        m.text("i-" + label, "&#8A93B2" + label, 90, cy, 10, 5, LEFT, null);
        m.text("v-" + label, "&#E8ECF7" + value, 270, cy, 11, 5, CENTER, MenuBuilder.updateEvery(40));
    }

    // ------------------------------------------------------------------ 成员管理

    /** 成员管理：顶部邀请输入 + 10 行成员 + 翻页。 */
    public static MenuBuilder members() {
        final MenuBuilder m = chrome(new MenuBuilder(), "成员管理");
        // 邀请输入行
        m.frame("inv-frame", 40, 64, 320, 40, LINE, 3, 1);
        m.text("inv-preview", "&#8A93B2点击输入玩家名", 200, 84, 10, 5, CENTER, null);
        final JsonObject arcitx = new JsonObject();
        arcitx.addProperty("type", "Arcitx");
        arcitx.addProperty("text", "inv-preview");
        arcitx.addProperty("submit", "inv-go");
        arcitx.addProperty("title", "输入要邀请的玩家名");
        arcitx.addProperty("initial", "");
        arcitx.addProperty("max-length", 16);
        final JsonObject display = new JsonObject();
        display.addProperty("before", "&#8A93B2点击输入玩家名");
        display.addProperty("editing", "&#E8ECF7{input}&#F0B429{caret}");
        display.addProperty("draft", "&#E8ECF7{input}");
        display.addProperty("complete", "&#E8ECF7{input}");
        arcitx.add("display", display);
        m.region("inv-input", 40, 64, 320, 40, arcitx);
        button(m, "inv-go", 380, 64, 90, 40, GREEN, "&#E8ECF7邀 请",
                "command: town invite {meta:input-inv}");
        m.text("inv-tip", "&#5A6480邀请后对方界面会出现邀请卡片", 500, 84, 9, 5, LEFT, null);

        // 10 行成员
        final double rowH = 30;
        for (int i = 0; i < 10; i++) {
            final double t = 120 + i * (rowH + 2);
            final String cond = "{meta:member_" + i + "} != —";
            m.text("mem-" + i, "&#E8ECF7{meta:member_" + i + "}", 130, t + rowH / 2, 11, 5, CENTER,
                    MenuBuilder.updateEvery(40));
            m.text("mem-role-" + i, "&#8A93B2{meta:member_" + i + "_role}", 320, t + rowH / 2, 10, 5,
                    CENTER, MenuBuilder.updateEvery(40));
            final double bx = 500;
            smallButton(m, "mem-up-" + i, bx, t + 3, "升", "&#34D399",
                    "command: town promote {meta:member_" + i + "}", cond);
            smallButton(m, "mem-down-" + i, bx + 52, t + 3, "降", "&#F0B429",
                    "command: town demote {meta:member_" + i + "}", cond);
            smallButton(m, "mem-kick-" + i, bx + 104, t + 3, "踢", "&#F87171",
                    "command: town confirm kick {meta:member_" + i + "}", cond);
            m.region("mem-tr-" + i, bx + 156, t + 3, 44, rowH - 6,
                    MenuBuilder.conditionSpec(
                            "{meta:is_mayor} == 1 && " + cond,
                            MenuBuilder.tooltip("&f转让镇长之位"),
                            MenuBuilder.click("all",
                                    "command: town confirm transfer {meta:member_" + i + "}")));
            m.text("mem-tr-t-" + i, "&#F0B429转让", bx + 178, t + rowH / 2, 9, 5, CENTER, null);
        }
        // 翻页
        m.text("page-now", "&#8A93B2第 {meta:page_now} / {meta:page_total} 页", MenuBuilder.W / 2,
                MenuBuilder.H - BOT / 2, 10, 5, CENTER, MenuBuilder.updateEvery(40));
        m.region("page-prev", 40, MenuBuilder.H - BOT - 24, 90, 28,
                MenuBuilder.conditionSpec("{meta:has_prev} == 1", MenuBuilder.tooltip("&f上一页"),
                        MenuBuilder.click("all", "command: town members {meta:prev_page}")));
        m.text("page-prev-t", "&#E8ECF7◀ 上一页", 85, MenuBuilder.H - BOT / 2, 10, 5, CENTER, null);
        m.region("page-next", MenuBuilder.W - 130, MenuBuilder.H - BOT - 24, 90, 28,
                MenuBuilder.conditionSpec("{meta:has_next} == 1", MenuBuilder.tooltip("&f下一页"),
                        MenuBuilder.click("all", "command: town members {meta:next_page}")));
        m.text("page-next-t", "&#E8ECF7下一页 ▶", MenuBuilder.W - 85, MenuBuilder.H - BOT / 2, 10, 5,
                CENTER, null);
        hint(m, "升 / 降仅官员与成员之间；踢人与转让需二次确认");
        return m;
    }

    private static void smallButton(final MenuBuilder m, final String id, final double l,
                                    final double t, final String label, final String color,
                                    final String action, final String condition) {
        m.rect(id + "-bg", l, t, 44, 24, RAISE, 2);
        m.text(id + "-t", color + label, l + 22, t + 12, 9, 5, CENTER, null);
        m.region(id, l, t, 44, 24, MenuBuilder.conditionSpec(condition, null,
                MenuBuilder.click("all", action)));
    }

    // ------------------------------------------------------------------ 圈地地图

    /** 9×9 圈地地图：以打开时的玩家区块为中心（meta 由 TownUi 写入）。 */
    public static MenuBuilder claim() {
        final MenuBuilder m = chrome(new MenuBuilder(), "领地地图");
        final double cell = 36;
        final double gap = 2;
        final double l0 = 24;
        final double t0 = 84;
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                final double l = l0 + c * (cell + gap);
                final double t = t0 + r * (cell + gap);
                m.text("cell-" + r + "-" + c, "&#8A93B2{meta:cell_" + r + "_" + c + "}",
                        l + cell / 2, t + cell / 2, 18, 5, CENTER, MenuBuilder.updateEvery(40));
                // 鼠标模式：左键购买，右键放弃（输入策略 force-mouse，见 README）
                final JsonObject actions = new JsonObject();
                final JsonArray left = new JsonArray();
                left.add("command: town claimmap " + r + " " + c);
                actions.add("left", left);
                final JsonArray right = new JsonArray();
                right.add("command: town unclaimat " + r + " " + c);
                actions.add("right", right);
                m.region("cellgo-" + r + "-" + c, l, t, cell, cell,
                        MenuBuilder.spec(MenuBuilder.tooltip(
                                "&f左键：圈下该区块（需与领地相连）",
                                "&f右键：放弃本镇区块"), actions));
            }
        }
        // 中心格金框（玩家所在区块）
        m.frame("cell-mark", l0 + 4 * (cell + gap) - 1, t0 + 4 * (cell + gap) - 1,
                cell + 2, cell + 2, ACC, 3, 1);
        // 右栏
        m.rect("side", 400, 84, 376, 260, PANEL, 1);
        m.text("side-town", "&#E8ECF7{meta:town_name}", 588, 106, 14, 5, CENTER, MenuBuilder.updateEvery(40));
        m.text("side-price", "&#8A93B2单价 &#F0B429{meta:claim_price}", 588, 136, 10, 5, CENTER,
                MenuBuilder.updateEvery(40));
        m.text("side-count", "&#8A93B2领地 &#E8ECF7{meta:claim_count}&#8A93B2 / {meta:claim_max}",
                588, 158, 10, 5, CENTER, MenuBuilder.updateEvery(40));
        legend(m, 420, 190, "&#34D399■", "本镇");
        legend(m, 560, 190, "&#38BDF8□", "可购买");
        legend(m, 420, 216, "&#F87171■", "他镇");
        legend(m, 560, 216, "&#5A6480·", "不相连");
        legend(m, 420, 242, "&#F0B429▣", "你所在区块");
        legend(m, 560, 242, "&#C08A5A×", "禁圈世界");
        m.text("side-balance", "&#8A93B2余额 &#F0B429{meta:balance}", 588, 276, 11, 5, CENTER,
                MenuBuilder.updateEvery(40));
        m.text("side-center", "&#8A93B2中心：{meta:claim_center}", 588, 308, 9, 5, CENTER,
                MenuBuilder.updateEvery(40));
        button(m, "recenter", 400, 356, 376, 40, RAISE, "&#E8ECF7回到我的位置",
                "command: town claimmap center");
        hint(m, "光标左键圈地 · 右键放弃 · 必须与已有领地相连");
        return m;
    }

    private static void legend(final MenuBuilder m, final double x, final double cy,
                               final String mark, final String label) {
        m.text("lg-" + label + "-m", mark, x, cy, 12, 5, CENTER, null);
        m.text("lg-" + label, "&#8A93B2" + label, x + 14, cy, 10, 5, LEFT, null);
    }

    // ------------------------------------------------------------------ 领地祝福

    /** 祝福商店：按配置生成，最多 6 行。 */
    public static MenuBuilder buffs(final TownPlugin plugin) {
        final MenuBuilder m = chrome(new MenuBuilder(), "领地祝福");
        final List<TownConfig.BuffDef> defs = plugin.config().buffs();
        final int rows = Math.min(defs.size(), 6);
        if (rows == 0) {
            m.text("no-buff", "&#8A93B2服务器没有配置任何祝福。", MenuBuilder.W / 2, 200, 12, 5, CENTER, null);
            hint(m, "管理员可在 config.yml 的 buffs.entries 添加");
            return m;
        }
        for (int i = 0; i < rows; i++) {
            final TownConfig.BuffDef def = defs.get(i);
            final double t = 84 + i * 56;
            m.rect("bf-bg-" + i, 40, t, 720, 48, PANEL, 1);
            m.text("bf-name-" + i, miniToAmp(def.displayMini()), 70, t + 24, 13, 5, LEFT, null);
            m.text("bf-cost-" + i, "&#F0B429{meta:bf_" + def.id() + "_cost}", 420, t + 24, 11, 5,
                    CENTER, MenuBuilder.updateEvery(40));
            m.text("bf-dur-" + i, "&#8A93B2" + (def.permanent() ? "永久" : def.durationMillis() / 60000 + " 分钟"),
                    530, t + 24, 10, 5, CENTER, null);
            m.text("bf-state-" + i, "&#8A93B2{meta:bf_" + def.id() + "}", 640, t + 24, 10, 5, CENTER,
                    MenuBuilder.updateEvery(40));
            button(m, "bf-buy-" + i, 700, t + 8, 52, 32, GREEN, "&#E8ECF7购买",
                    "command: town buff buy " + def.id());
        }
        hint(m, "祝福对身处本镇领地的成员持续生效，可叠加时长");
        return m;
    }

    // ------------------------------------------------------------------ 创建小镇

    /** 创建小镇：规则说明 + Arcitx 名称输入 + 提交。 */
    public static MenuBuilder create(final TownPlugin plugin) {
        final MenuBuilder m = chrome(new MenuBuilder(), "创建小镇");
        m.text("r0", "&#8A93B21. 名称 " + plugin.config().nameMinLength() + "~"
                + plugin.config().nameMaxLength() + " 个字符（中文 / 字母 / 数字 / 下划线）",
                MenuBuilder.W / 2, 108, 10, 5, CENTER, null);
        m.text("r1", "&#8A93B22. 邀请至少 {meta:min_members} 名成员加入", MenuBuilder.W / 2, 132, 10, 5,
                CENTER, null);
        m.text("r2", "&#8A93B23. 成立支付 &#F0B429{meta:found_cost}&#8A93B2，获得镇政厅并免费圈地",
                MenuBuilder.W / 2, 156, 10, 5, CENTER, null);
        // 输入框
        m.frame("in-frame", 240, 196, 320, 44, LINE, 3, 1);
        m.text("in-preview", "&#8A93B2点击这里输入小镇名称", 400, 218, 11, 5, CENTER, null);
        final JsonObject arcitx = new JsonObject();
        arcitx.addProperty("type", "Arcitx");
        arcitx.addProperty("text", "in-preview");
        arcitx.addProperty("submit", "go");  // 必须指向已存在的 backend 命中区 id
        arcitx.addProperty("title", "输入小镇名称");
        arcitx.addProperty("initial", "");
        arcitx.addProperty("max-length", plugin.config().nameMaxLength());
        final JsonObject display = new JsonObject();
        display.addProperty("before", "&#8A93B2点击这里输入小镇名称");
        display.addProperty("editing", "&#E8ECF7{input}&#F0B429{caret}");
        display.addProperty("draft", "&#E8ECF7{input}");
        display.addProperty("complete", "&#E8ECF7{input}");
        arcitx.add("display", display);
        m.region("in-name", 240, 196, 320, 44, arcitx);
        // 提交（与输入区不相交）
        button(m, "go", 240, 262, 320, 46, ACC, "&#121826确认名称并筹备",
                "command: town create {meta:input-in-name}");
        m.text("tip", "&#5A6480提交后进入筹备期；名称非法会被拒绝并说明原因", MenuBuilder.W / 2, 330, 9, 5,
                CENTER, null);
        hint(m, "输入框支持中文；点击提交后如名称被占用会给出提示");
        return m;
    }

    // ------------------------------------------------------------------ 工具

    /** MiniMessage 常用颜色 → & 色码（ArcMenu 文本用传统色码）；其余标签剥除。 */
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

    /** 生成全部菜单并写入目录；返回写入清单。 */
    public static List<Written> writeAll(final TownPlugin plugin, final Path dir) throws IOException {
        Files.createDirectories(dir);
        final List<Written> written = new ArrayList<>();
        put(written, dir, HOME_NONE, homeNone(plugin));
        put(written, dir, HOME_PENDING, homePending());
        put(written, dir, HOME_ACTIVE, homeActive());
        put(written, dir, MEMBERS, members());
        put(written, dir, CLAIM, claim());
        put(written, dir, BUFFS, buffs(plugin));
        put(written, dir, CREATE, create(plugin));
        return written;
    }

    private static void put(final List<Written> out, final Path dir, final String id,
                            final MenuBuilder m) throws IOException {
        final Path file = dir.resolve(id + ".yml");
        Files.writeString(file, m.dump(id), StandardCharsets.UTF_8);
        out.add(new Written(id, file));
    }
}
