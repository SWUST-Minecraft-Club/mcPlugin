package com.example.town.arcmenu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ArcMenu 精确布局库：按 800×450 左上原点书写设计坐标，输出补偿后的 frontend/backend。
 * ppb 固定 42.7；补偿参数需要用当前 ArcMenu / MC 版本的实机截图复核。
 * 图层约定为 layer 0..6 → z=-3..3，互相重叠的元素使用不同图层。
 */
public final class MenuBuilder {

    public static final int W = 800;
    public static final int H = 450;
    public static final double PPB = 42.7;
    /** 菜单整体占 16:9 视野的比例；视觉与 backend 命中区必须一起缩放。 */
    public static final double WINDOW_SCALE = 0.70;
    public static final double G = PPB / 160.5 * WINDOW_SCALE;
    public static final double ZK = 0.00305 * 160.5 / PPB;

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

    private final Map<String, JsonObject> frontend = new LinkedHashMap<>();
    private final Map<String, JsonObject> backend = new LinkedHashMap<>();

    public static boolean isCjk(final char ch) {
        return ch > 0x2E80;
    }

    public static String stripCodes(final String s) {
        return s.replaceAll("&#([0-9A-Fa-f]{6})", "").replaceAll("&[0-9a-fk-or]", "");
    }

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
        if (layer < 0 || layer > 6) {
            throw new IllegalArgumentException("layer 必须在 0..6: " + layer);
        }
        return layer - 3;
    }

    private static double s(final int layer) {
        return 1 + ZK * z(layer);
    }

    private static double r3(final double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static void bounds(final String id, final double l, final double t,
                               final double w, final double h) {
        if (w <= 0 || h <= 0 || l < 0 || t < 0 || l + w > W || t + h > H) {
            throw new IllegalArgumentException(id + " 超出 800×450 设计画布或尺寸非法: "
                    + l + "," + t + " " + w + "×" + h);
        }
    }

    private JsonObject offset(final double cx, final double cy, final int layer) {
        final double scale = s(layer);
        final JsonObject off = new JsonObject();
        off.addProperty("x", r3(G * cx / scale));
        off.addProperty("y", r3(G * cy / scale));
        off.addProperty("z", z(layer));
        return off;
    }

    private void putFrontend(final String id, final JsonObject value) {
        if (frontend.containsKey(id) || backend.containsKey(id)) {
            throw new IllegalArgumentException("菜单元素 ID 重复: " + id);
        }
        frontend.put(id, value);
    }

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

    public void rect(final String id, final double l, final double t, final double w, final double h,
                     final String color, final int layer) {
        rect(id, l, t, w, h, color, 255, layer);
    }

    public void rect(final String id, final double l, final double t, final double w, final double h,
                     final String color, final int opacity, final int layer) {
        bounds(id, l, t, w, h);
        if (opacity < 0 || opacity > 255) {
            throw new IllegalArgumentException(id + " opacity 必须在 0..255");
        }
        final double scale = s(layer);
        final double hs = h / 0.9;
        final double bottom = H / 2.0 - (t + h);
        final JsonObject o = new JsonObject();
        o.addProperty("type", "rectangle");
        o.addProperty("width", r3(G * w / scale));
        o.addProperty("height", r3(G * hs / scale));
        o.addProperty("color", color);
        o.addProperty("opacity", opacity);
        o.add("offset", offset(l + w / 2 - W / 2.0, bottom + hs / 2, layer));
        putFrontend(id, o);
    }

    public void frame(final String id, final double l, final double t, final double w, final double h,
                      final String color, final int layer, final double thickness) {
        bounds(id, l, t, w, h);
        if (w <= 2 || h <= 2 || thickness <= 0 || 2 * thickness >= Math.min(w, h)) {
            throw new IllegalArgumentException("frame " + id + " 尺寸或厚度非法");
        }
        final double scale = s(layer);
        final JsonObject o = new JsonObject();
        o.addProperty("type", "frame");
        o.addProperty("width", r3(G * w / scale));
        o.addProperty("height", r3(G * h / scale));
        o.addProperty("thickness", r3(G * thickness / scale));
        o.addProperty("color", color);
        o.add("offset", offset(l + w / 2 - W / 2.0, H / 2.0 - (t + h / 2), layer));
        putFrontend(id, o);
    }

    /** 动态文本宽度未知，必须按中心坐标居中，避免把左边缘误当中心。 */
    public void text(final String id, final String content, final double x, final double cy,
                     final double size, final int layer, final Align align, final JsonObject extra) {
        if (size <= 0) {
            throw new IllegalArgumentException(id + " 字号必须大于 0");
        }
        final String plain = stripCodes(content);
        final boolean dynamic = plain.contains("%") || plain.contains("{");
        if (dynamic && align != Align.CENTER) {
            throw new IllegalArgumentException(id + " 含动态占位符，必须使用 CENTER");
        }
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
        putFrontend(id, o);
    }

    public void item(final String id, final String material, final double cx, final double cy,
                     final double size, final int layer) {
        if (size <= 0) {
            throw new IllegalArgumentException(id + " 物品尺寸必须大于 0");
        }
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
        putFrontend(id, o);
    }

    /** 图片元素（source 为含占位符的虚拟路径；update 为重解析周期 tick）。 */
    public void image(final String id, final String source, final double l, final double t,
                      final double w, final double h, final int layer, final int updateTicks) {
        bounds(id, l, t, w, h);
        final double scale = s(layer);
        final JsonObject o = new JsonObject();
        o.addProperty("type", "image");
        o.addProperty("source", source);
        o.addProperty("width", r3(G * w / scale));
        o.addProperty("height", r3(G * h / scale));
        o.addProperty("opacity", 255);
        o.addProperty("update", updateTicks);
        o.add("offset", offset(l + w / 2 - W / 2.0, H / 2.0 - (t + h / 2), layer));
        putFrontend(id, o);
    }

    public void region(final String id, final double l, final double t, final double w, final double h,
                       final JsonObject spec) {
        bounds(id, l, t, w, h);
        if (frontend.containsKey(id) || backend.containsKey(id)) {
            throw new IllegalArgumentException("菜单元素 ID 重复: " + id);
        }
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

    public static JsonArray actions(final String... list) {
        final JsonArray arr = new JsonArray();
        for (final String action : list) {
            arr.add(action);
        }
        return arr;
    }

    public static JsonObject spec(final JsonObject tooltip, final JsonObject actions) {
        final JsonObject o = new JsonObject();
        if (tooltip != null) {
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

    public static JsonObject updateEvery(final int ticks) {
        if (ticks <= 0) {
            throw new IllegalArgumentException("update 必须是正整数");
        }
        final JsonObject o = new JsonObject();
        o.addProperty("update", ticks);
        return o;
    }

    public static JsonObject conditionSpec(final String condition, final JsonObject tooltip,
                                           final JsonObject actions) {
        return conditionSpec(condition, tooltip, actions, null);
    }

    public static JsonObject conditionSpec(final String condition, final JsonObject tooltip,
                                           final JsonObject actions, final JsonArray deny) {
        final JsonObject o = spec(tooltip, actions);
        o.addProperty("condition", condition);
        if (deny != null) {
            o.add("deny", deny);
        }
        return o;
    }

    /** JSON 是合法 YAML；每页由 TownUi 的关闭指令区分“真实关闭”和“切页”。 */
    public String dump(final String menuId) {
        final JsonObject doc = new JsonObject();
        doc.addProperty("schema-version", 1);
        doc.addProperty("id", menuId);
        doc.addProperty("permission", "town.use");
        doc.add("open-commands", new JsonArray());
        doc.addProperty("main-menu", false);
        final JsonObject canvas = new JsonObject();
        canvas.addProperty("width", (double) W);
        canvas.addProperty("height", (double) H);
        canvas.addProperty("pixels-per-block", PPB);
        canvas.addProperty("distance", 2.0);
        doc.add("canvas", canvas);
        doc.add("frontend", frontend());
        doc.add("backend", backend());
        final JsonObject events = new JsonObject();
        events.add("close", actions("command: town ui-closed " + menuId));
        doc.add("events", events);
        return doc.toString();
    }
}
