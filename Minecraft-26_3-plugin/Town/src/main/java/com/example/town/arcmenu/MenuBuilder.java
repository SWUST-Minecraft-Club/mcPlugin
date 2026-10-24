package com.example.town.arcmenu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * ArcMenu 精确布局库（Java 版）：按 800×450 左上原点书写设计坐标，输出已做实测校正的
 * frontend/backend（见 arcmenu-designing 技能 scripts/arcmenu_layout.py，2026.10 实测于 3.0/26.2）：
 * <ul>
 *   <li>ppb 固定 42.7（改了鼠标不可见）；内容整体乘 G=ppb/160.5，800×450 恰好铺满 16:9 视野</li>
 *   <li>透视：z 越大越近越大，scale(z)=1+ZK·z，位置与尺寸都除以 scale(z)</li>
 *   <li>rectangle 顶部少画 10% 高度、底边准确 → 设高 h/0.9 并锁定底边</li>
 *   <li>text 永远以 offset.x 为水平中心，offset.y≈文字底边 → 左/右对齐靠墨宽换算中心；
 *       含 %占位% / {meta} 的动态文本宽度未知，只允许居中</li>
 *   <li>frame 宽或高 ≤2 会导致整份菜单加载失败，细线一律用 rect</li>
 * </ul>
 * 图层约定（同 PS）：layer 0..6 → z=-3..3；互相重叠的元素绝不共用 z。
 */
public final class MenuBuilder {

    public static final int W = 800;
    public static final int H = 450;
    public static final double PPB = 42.7;
    public static final double G = PPB / 160.5;
    public static final double ZK = 0.00305 * 160.5 / PPB;

    /** 文字对齐方式。 */
    public enum Align { LEFT, CENTER, RIGHT }

    private static final Map<Character, Integer> ADV = new LinkedHashMap<>();

    static {
        "ABCDEFGHJKLMNOPQRSUVWXYZabcdeghjmnopqrsuvwxyz0123456789$/?#%&+-=_".chars()
                .forEach(c -> ADV.put((char) c, 6));
        "i!.,:;|'".chars().forEach(c -> ADV.put((char) c, 2));
        ADV.put('l', 3);
        ADV.put('I', 4);
        ADV.put('t', 4);
        ADV.put(' ', 4);
        ADV.put('f', 5);
        ADV.put('k', 5);
        ADV.put('<', 5);
        ADV.put('>', 5);
    }

    public static boolean isCjk(final char ch) {
        return ch > 0x2E80;
    }

    /** 去掉 &#RRGGBB 与 &色码 后的纯文本。 */
    public static String stripCodes(final String s) {
        return s.replaceAll("&#([0-9A-Fa-f]{6})", "").replaceAll("&[0-9a-fk-or]", "");
    }

    /** 文字墨迹宽度（画布单位），不含颜色码。 */
    public static double inkWidth(final String raw, final double size) {
        final String s = stripCodes(raw);
        if (s.isEmpty()) {
            return 0;
        }
        double px = 0;
        for (final char ch : s.toCharArray()) {
            px += isCjk(ch) ? 9 : ADV.getOrDefault(ch, 6);
        }
        px -= isCjk(s.charAt(s.length() - 1)) ? 2.5 : 1;
        return px * 0.1106 * size;
    }

    private static int z(final int layer) {
        return layer - 3;
    }

    private static double s(final int layer) {
        return 1 + ZK * z(layer);
    }

    private static double r3(final double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    // ------------------------------------------------------------------

    private final Map<String, JsonObject> frontend = new LinkedHashMap<>();
    private final Map<String, JsonObject> backend = new LinkedHashMap<>();

    public JsonObject frontend() {
        final JsonObject obj = new JsonObject();
        frontend.forEach(obj::add);
        return obj;
    }

    public JsonObject backend() {
        final JsonObject obj = new JsonObject();
        backend.forEach(obj::add);
        return obj;
    }

    private JsonObject offset(final double cx, final double cy, final int layer) {
        final double scale = s(layer);
        final JsonObject off = new JsonObject();
        off.addProperty("x", r3(G * cx / scale));
        off.addProperty("y", r3(G * cy / scale));
        off.addProperty("z", z(layer));
        return off;
    }

    /** 实心矩形。l/t 为设计坐标左上角（y 向下），锁定底边补偿高度损失。 */
    public void rect(final String id, final double l, final double t, final double w, final double h,
                     final String color, final int layer) {
        final double scale = s(layer);
        final double hs = h / 0.9;
        final double bottom = H / 2.0 - (t + h);
        final JsonObject o = new JsonObject();
        o.addProperty("type", "rectangle");
        o.addProperty("width", r3(G * w / scale));
        o.addProperty("height", r3(G * hs / scale));
        o.addProperty("color", color);
        o.add("offset", offset(l + w / 2 - W / 2.0, bottom + hs / 2, layer));
        frontend.put(id, o);
    }

    /** 描边框（宽或高 ≤2 会加载失败，细线用 rect）。 */
    public void frame(final String id, final double l, final double t, final double w, final double h,
                      final String color, final int layer, final double thickness) {
        if (w <= 2 || h <= 2) {
            throw new IllegalArgumentException("frame " + id + " 宽高 ≤2 会加载失败，用 rect 画细线");
        }
        final double scale = s(layer);
        final JsonObject o = new JsonObject();
        o.addProperty("type", "frame");
        o.addProperty("width", r3(G * w / scale));
        o.addProperty("height", r3(G * h / scale));
        o.addProperty("thickness", r3(G * thickness / scale));
        o.addProperty("color", color);
        o.add("offset", offset(l + w / 2 - W / 2.0, H / 2.0 - (t + h / 2), layer));
        frontend.put(id, o);
    }

    /**
     * 文字。x：左对齐=左边缘 / 居中=中心 / 右对齐=右边缘；cy：视觉中心。
     * 含 %占位% 或 {meta} 的动态文本强制居中；kw 里的 update 等原样透传。
     */
    public void text(final String id, final String content, final double x, final double cy,
                     final double size, final int layer, final Align align, final JsonObject extra) {
        final String plain = stripCodes(content);
        final boolean dynamic = plain.contains("%") || plain.contains("{");
        final double w = dynamic ? 0 : inkWidth(plain, size);
        final double cx = switch (align) {
            case LEFT -> x + w / 2;
            case CENTER -> x;
            case RIGHT -> x - w / 2;
        };
        final double k = plain.chars().anyMatch(c -> isCjk((char) c)) ? 0.5125 : 0.59;
        final double scale = s(layer);
        final JsonObject o = new JsonObject();
        o.addProperty("type", "text");
        o.addProperty("content", content);
        o.addProperty("size", r3(G * size / scale));
        o.addProperty("alignment", "center");
        o.addProperty("line-width", 400);
        o.add("offset", offset(cx - W / 2.0, H / 2.0 - (cy + k * size), layer));
        if (extra != null) {
            extra.entrySet().forEach(e -> o.add(e.getKey(), e.getValue()));
        }
        frontend.put(id, o);
    }

    /** 物品模型（原版材质，GUI 上下文）。 */
    public void item(final String id, final String material, final double cx, final double cy,
                     final double size, final int layer) {
        final double scale = s(layer);
        final JsonObject off = offset(cx - W / 2.0, H / 2.0 - cy, layer);
        final JsonObject sc = new JsonObject();
        sc.addProperty("x", r3(G * size / scale));
        sc.addProperty("y", r3(G * size / scale));
        sc.addProperty("z", 1);
        final JsonObject o = new JsonObject();
        o.addProperty("type", "item");
        o.addProperty("material", material.contains(":") ? material : "minecraft:" + material);
        o.addProperty("context", "GUI");
        o.add("offset", off);
        o.add("scale", sc);
        frontend.put(id, o);
    }

    /** 后端命中区（不受 z 透视影响，只乘 G）。actions/tooltip/condition 等由调用方组装。 */
    public void region(final String id, final double l, final double t, final double w, final double h,
                       final JsonObject spec) {
        final JsonObject o = new JsonObject();
        o.addProperty("x", r3(G * (l + w / 2 - W / 2.0)));
        o.addProperty("y", r3(G * (H / 2.0 - (t + h / 2))));
        o.addProperty("width", r3(G * w));
        o.addProperty("height", r3(G * h));
        if (spec != null) {
            spec.entrySet().forEach(e -> o.add(e.getKey(), e.getValue()));
        }
        backend.put(id, o);
    }

    // ------------------------------------------------------------------ 便捷构造

    public static JsonArray actions(final String... list) {
        final JsonArray arr = new JsonArray();
        for (final String action : list) {
            arr.add(action);
        }
        return arr;
    }

    /** 组装 region 的 spec：actions（all/left/right/shift-right 之一或多键）。 */
    public static JsonObject spec(final JsonObject tooltip, final JsonObject actions) {
        final JsonObject o = new JsonObject();
        if (tooltip != null) {
            // tooltip(...) 返回 {tooltip:[...]}；region 的 tooltip 字段要求字符串/字符串数组，此处展平
            if (tooltip.entrySet().size() == 1 && tooltip.get("tooltip") instanceof JsonArray arr) {
                o.add("tooltip", arr);
            } else {
                o.add("tooltip", tooltip);
            }
        }
        if (actions != null) {
            o.add("actions", actions);
        }
        return o;
    }

    public static JsonObject tooltip(final String... lines) {
        final JsonArray arr = new JsonArray();
        for (final String line : lines) {
            arr.add(line);
        }
        final JsonObject o = new JsonObject();
        o.add("tooltip", arr);
        return o;
    }

    public static JsonObject click(final String key, final String... actionList) {
        final JsonObject actions = new JsonObject();
        actions.add(key, actions(actionList));
        return actions;
    }

    /** 文本 extra：update 周期。 */
    public static JsonObject updateEvery(final int ticks) {
        final JsonObject o = new JsonObject();
        o.addProperty("update", ticks);
        return o;
    }

    /** 条件 region spec：condition 不成立时无动作（无 deny 即静默）。 */
    public static JsonObject conditionSpec(final String condition, final JsonObject tooltip,
                                           final JsonObject actions) {
        final JsonObject o = spec(tooltip, actions);
        o.addProperty("condition", condition);
        return o;
    }

    /** 输出整份菜单（JSON 即合法 YAML）。 */
    public String dump(final String menuId) {
        final JsonObject doc = new JsonObject();
        doc.addProperty("schema-version", 1);
        doc.addProperty("id", menuId);
        doc.addProperty("permission", "town.use");
        final JsonObject canvas = new JsonObject();
        canvas.addProperty("width", (double) W);
        canvas.addProperty("height", (double) H);
        canvas.addProperty("pixels-per-block", PPB);
        canvas.addProperty("distance", 2.0);
        doc.add("canvas", canvas);
        doc.add("frontend", frontend());
        doc.add("backend", backend());
        return doc.toString();
    }
}
