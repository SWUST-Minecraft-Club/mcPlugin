package com.example.minecraftmcp.core;

import com.google.gson.*;
import java.util.*;

/** 工具名、JSON Schema 与参数校验共同维护；适配层只接收已校验的请求。 */
public final class ToolCatalog {
    public record Tool(String name, String description, boolean readOnly, JsonObject schema) {
        public JsonObject descriptor() {
            JsonObject object = new JsonObject();
            object.addProperty("name", name);
            object.addProperty("description", description);
            object.add("inputSchema", schema.deepCopy());
            JsonObject annotations = new JsonObject();
            annotations.addProperty("readOnlyHint", readOnly);
            annotations.addProperty("destructiveHint", !readOnly);
            annotations.addProperty("idempotentHint", readOnly);
            annotations.addProperty("openWorldHint", !readOnly);
            object.add("annotations", annotations);
            return object;
        }
    }

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolCatalog() {
        add("client.state", "获取游戏版本、窗口/GUI/截图尺寸、连接与屏幕状态、测试适配器信息。", true);
        add("capture.screenshot", "截取游戏 framebuffer，直接返回 MCP PNG 图片；坐标对应 guiWidth/guiHeight。", true,
                "maxWidth", integer(64, 3840));
        add("ui.inspect", "获取当前屏幕标题、控件树、稳定到本次屏幕的控件编号、坐标、可用/焦点状态和容器槽位。", true);
        add("ui.open", "打开 inventory/pause/chat；需要进入世界。", false,
                "screen!", choices("inventory", "pause", "chat"));
        add("ui.close", "关闭当前界面；容器使用玩家关闭流程通知服务端。", false);
        add("ui.click", "点击 GUI 逻辑坐标；支持 left/right/middle 与 shift/ctrl/alt。界面必须已打开。", false,
                "x!", number(0, 32768), "y!", number(0, 32768),
                "button", choices("left", "right", "middle"), "shift", bool(), "ctrl", bool(), "alt", bool());
        add("ui.widget_click", "使用 ui.inspect 返回的 screenToken 和控件 id 点击；拒绝已切换屏幕的旧编号。", false,
                "screenToken!", string(128), "id!", integer(0, 4095));
        add("ui.scroll", "在指定 GUI 逻辑坐标滚动。", false,
                "x!", number(0, 32768), "y!", number(0, 32768), "horizontal", number(-100, 100), "vertical!", number(-100, 100));
        add("ui.type", "向当前获得焦点的文本输入控件输入 Unicode 文本；不读取系统剪贴板。", false,
                "text!", string(4096));
        add("input.key", "按 Minecraft 键名（如 w、space、enter、escape、left.shift）在 GUI 发送按下/释放，或在世界中按住映射键 ticks 个 tick 后自动释放。", false,
                "key!", string(64), "ticks", integer(1, 200), "shift", bool(), "ctrl", bool(), "alt", bool());
        add("input.release_all", "立即释放本 Mod 持有的全部按键。", false);
        add("player.look", "设置玩家 yaw/pitch 视角（角度）。", false,
                "yaw!", number(-360000, 360000), "pitch!", number(-90, 90));
        add("player.inspect", "读取位置、朝向、生命、饥饿、经验、游戏模式、速度和手持物品。", true);
        add("inventory.list", "读取玩家背包或当前容器及其槽位、物品摘要、菜单 id 和 carried 光标物品。", true,
                "scope", choices("player", "container"), "includeEmpty", bool());
        add("item.inspect", "读取背包或容器指定槽位的注册 id、数量、耐久、组件与完整 tooltip。", true,
                "scope", choices("player", "container"), "slot!", integer(0, 4095));
        add("inventory.click", "使用服务端菜单槽位编号进行 PICKUP/QUICK_MOVE/SWAP/THROW/CLONE/PICKUP_ALL；menuId 防止误点旧容器。", false,
                "menuId!", integer(0, 2147483647), "slot!", integer(-999, 4095),
                "button", integer(0, 8), "action", choices("PICKUP", "QUICK_MOVE", "SWAP", "THROW", "CLONE", "PICKUP_ALL"));
        add("inventory.select_hotbar", "选择快捷栏槽位（0..8）。", false, "slot!", integer(0, 8));
        add("world.inspect", "读取维度、时间、天气、玩家指向的方块/实体与方块状态；只读客户端已加载数据。", true);
        add("world.block", "读取坐标处已加载方块的注册 id 与状态，不请求加载区块。", true,
                "x!", integer(-30000000, 30000000), "y!", integer(-2048, 2048), "z!", integer(-30000000, 30000000));
        add("world.entities", "读取玩家附近客户端可见实体，按距离排序并限制数量。", true,
                "radius", number(1, 128), "limit", integer(1, 256));
        add("player.interact", "对当前准星目标执行 attack/use；发送普通客户端交互，不绕过服务端权限。", false,
                "action!", choices("attack", "use"), "ticks", integer(1, 200));
        add("chat.send", "发送聊天或 / 开头的服务端指令；遵守 allowChat/allowCommands 配置和原有权限。", false,
                "message!", string(256));
        add("chat.history", "读取客户端聊天历史文本，最多 100 条。", true, "limit", integer(1, 100));
        add("test.wait", "等待实际游戏 tick，不阻塞游戏线程；GUI 暂停时客户端 tick 仍可推进。", true,
                "ticks!", integer(1, 200));
        for (String name : List.of("test.assert", "test.wait_for")) {
            add(name, "调用只读 query，按 RFC 6901 JSON Pointer 检查 path；支持 equals/contains/exists/gte/lte。wait_for 轮询至满足或超时。", true,
                    "query!", choices("client.state", "ui.inspect", "player.inspect", "inventory.list", "item.inspect", "world.inspect", "world.block", "world.entities", "chat.history"),
                    "arguments", JsonParser.parseString("{\"type\":\"object\"}").getAsJsonObject(),
                    "path!", string(512), "operator!", choices("equals", "contains", "exists", "gte", "lte"),
                    "expected", new JsonObject(), "timeoutMs", integer(1, 30000), "intervalMs", integer(50, 5000));
        }
    }

    public Tool get(String name) { return tools.get(name); }
    public JsonArray list() {
        JsonArray array = new JsonArray();
        tools.values().forEach(tool -> array.add(tool.descriptor()));
        return array;
    }

    public void validate(String name, JsonObject arguments) {
        Tool tool = tools.get(name);
        if (tool == null) throw new IllegalArgumentException("未知工具：" + name);
        JsonObject properties = tool.schema.getAsJsonObject("properties");
        for (JsonElement required : tool.schema.getAsJsonArray("required")) {
            if (!arguments.has(required.getAsString())) throw new IllegalArgumentException("缺少参数：" + required.getAsString());
        }
        for (var entry : arguments.entrySet()) {
            if (!properties.has(entry.getKey())) throw new IllegalArgumentException("未知参数：" + entry.getKey());
            JsonObject rule = properties.getAsJsonObject(entry.getKey());
            JsonElement value = entry.getValue();
            if (!rule.has("type")) continue;
            String type = rule.get("type").getAsString();
            boolean valid = switch (type) {
                case "object" -> value.isJsonObject();
                case "string" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
                case "boolean" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
                case "integer", "number" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
                default -> false;
            };
            if (!valid) throw new IllegalArgumentException("参数类型错误：" + entry.getKey());
            if (type.equals("integer")) {
                try {
                    if (value.getAsBigDecimal().stripTrailingZeros().scale() > 0) throw new ArithmeticException();
                }
                catch (ArithmeticException ex) { throw new IllegalArgumentException("必须为整数：" + entry.getKey()); }
            }
            if (rule.has("minimum") && (value.getAsBigDecimal().compareTo(rule.get("minimum").getAsBigDecimal()) < 0
                    || value.getAsBigDecimal().compareTo(rule.get("maximum").getAsBigDecimal()) > 0))
                throw new IllegalArgumentException("参数越界：" + entry.getKey());
            if (rule.has("maxLength") && value.getAsString().codePointCount(0, value.getAsString().length()) > rule.get("maxLength").getAsInt())
                throw new IllegalArgumentException("文本过长：" + entry.getKey());
            if (rule.has("enum") && !rule.getAsJsonArray("enum").contains(value))
                throw new IllegalArgumentException("参数取值无效：" + entry.getKey());
        }
    }

    private void add(String name, String description, boolean readOnly, Object... fields) {
        JsonObject schema = new JsonObject(), properties = new JsonObject();
        JsonArray required = new JsonArray();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);
        for (int i = 0; i < fields.length; i += 2) {
            String key = (String) fields[i];
            if (key.endsWith("!")) { key = key.substring(0, key.length() - 1); required.add(key); }
            properties.add(key, (JsonObject) fields[i + 1]);
        }
        schema.add("properties", properties);
        schema.add("required", required);
        tools.put(name, new Tool(name, description, readOnly, schema));
    }
    private static JsonObject type(String type) { JsonObject o = new JsonObject(); o.addProperty("type", type); return o; }
    private static JsonObject integer(int min, int max) { return range("integer", min, max); }
    private static JsonObject number(double min, double max) { return range("number", min, max); }
    private static JsonObject range(String type, double min, double max) {
        JsonObject o = type(type); o.addProperty("minimum", min); o.addProperty("maximum", max); return o;
    }
    private static JsonObject bool() { return type("boolean"); }
    private static JsonObject string(int max) { JsonObject o = type("string"); o.addProperty("maxLength", max); return o; }
    private static JsonObject choices(String... values) {
        JsonObject o = type("string"); JsonArray a = new JsonArray();
        for (String value : values) a.add(value);
        o.add("enum", a); return o;
    }
}
