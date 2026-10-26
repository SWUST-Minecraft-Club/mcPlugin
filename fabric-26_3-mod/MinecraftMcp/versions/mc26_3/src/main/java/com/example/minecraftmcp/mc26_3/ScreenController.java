package com.example.minecraftmcp.mc26_3;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.input.*;
import java.lang.reflect.Field;
import java.util.*;
import static com.example.minecraftmcp.mc26_3.Jsons.*;

final class ScreenController {
    private final Minecraft client;
    private final String instance = UUID.randomUUID().toString();
    private Screen observed;
    private int width, height;
    private long revision;
    private List<GuiEventListener> widgets = List.of();
    private final Field left = field(AbstractContainerScreen.class, "leftPos");
    private final Field top = field(AbstractContainerScreen.class, "topPos");

    ScreenController(Minecraft client) { this.client = client; }

    void observe() {
        Screen current = client.gui.screen();
        List<GuiEventListener> currentWidgets = new ArrayList<>();
        if (current != null) collect(current, currentWidgets, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        if (current != observed || (current != null && (width != current.width || height != current.height)) || !currentWidgets.equals(widgets)) {
            revision++;
            observed = current;
            width = current == null ? 0 : current.width;
            height = current == null ? 0 : current.height;
            widgets = List.copyOf(currentWidgets);
        }
    }

    JsonObject inspect() {
        observe();
        JsonArray result = new JsonArray();
        for (int id = 0; id < widgets.size(); id++) {
            GuiEventListener listener = widgets.get(id);
            var rectangle = listener.getRectangle();
            JsonObject widget = object("id", id, "class", listener.getClass().getName(), "focused", listener.isFocused(),
                    "x", rectangle.left(), "y", rectangle.top(), "width", rectangle.width(), "height", rectangle.height());
            if (listener instanceof AbstractWidget w) {
                widget.addProperty("label", w.getMessage().getString());
                widget.addProperty("active", w.active);
                widget.addProperty("visible", w.visible);
            }
            if (listener instanceof EditBox input) widget.addProperty("value", input.getValue());
            if (listener instanceof MultiLineEditBox input) widget.addProperty("value", input.getValue());
            result.add(widget);
        }
        JsonObject info = object("screen", screenInfo(), "screenToken", token(), "widgets", result,
                "guiWidth", client.getWindow().getGuiScaledWidth(), "guiHeight", client.getWindow().getGuiScaledHeight());
        if (observed instanceof AbstractContainerScreen<?> container) {
            try {
                int offsetX = left.getInt(container), offsetY = top.getInt(container);
                JsonArray slots = new JsonArray();
                for (var slot : container.getMenu().slots) {
                    slots.add(object("slot", slot.index, "inventorySlot", slot.getContainerSlot(),
                            "x", offsetX + slot.x, "y", offsetY + slot.y, "width", 16, "height", 16,
                            "active", slot.isActive(), "item", StateReader.item(slot.getItem(), false, client)));
                }
                info.add("slots", slots);
                info.addProperty("menuId", container.getMenu().containerId);
                info.addProperty("creative", container instanceof CreativeModeInventoryScreen);
            } catch (IllegalAccessException e) { throw new IllegalStateException("容器坐标访问失败", e); }
        }
        return info;
    }

    JsonElement screenInfo() {
        Screen screen = client.gui.screen();
        return screen == null ? JsonNull.INSTANCE : object("class", screen.getClass().getName(),
                "title", screen.getTitle().getString(), "width", screen.width, "height", screen.height,
                "pausesGame", screen.isPauseScreen());
    }

    JsonObject open(String name) {
        if (client.player == null || client.level == null) throw new IllegalStateException("尚未进入世界");
        if (client.gui.screen() instanceof AbstractContainerScreen<?>) client.player.closeContainer();
        switch (name) {
            case "inventory" -> {
                if (client.player.isSpectator()) throw new IllegalStateException("旁观模式不能打开背包");
                client.getTutorial().onOpenInventory();
                client.gui.setScreen(new InventoryScreen(client.player));
            }
            case "pause" -> { client.gui.setScreen(null); client.gui.setPauseScreen(false, false); }
            case "chat" -> client.gui.openChatScreen(ChatComponent.ChatMethod.MESSAGE);
            default -> throw new IllegalArgumentException("不支持的屏幕");
        }
        return inspect();
    }

    JsonObject close() {
        Screen screen = client.gui.screen();
        if (screen instanceof AbstractContainerScreen<?> && client.player != null) client.player.closeContainer();
        else if (screen != null) screen.onClose();
        return inspect();
    }

    JsonObject click(JsonObject arguments) {
        Screen screen = requireScreen();
        double x = number(arguments, "x", 0), y = number(arguments, "y", 0);
        checkCoordinates(screen, x, y);
        int button = switch (text(arguments, "button", "left")) {
            case "right" -> InputConstants.MOUSE_BUTTON_RIGHT;
            case "middle" -> InputConstants.MOUSE_BUTTON_MIDDLE;
            default -> InputConstants.MOUSE_BUTTON_LEFT;
        };
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(button, modifiers(arguments)));
        screen.mouseMoved(x, y);
        boolean handled;
        try { handled = screen.mouseClicked(event, false); }
        finally {
            if (client.gui.screen() == screen) { screen.mouseReleased(event); screen.setDragging(false); }
        }
        return object("handled", handled, "screen", screenInfo());
    }

    JsonObject widgetClick(JsonObject arguments) {
        observe();
        if (!token().equals(text(arguments, "screenToken", ""))) throw new IllegalStateException("屏幕或控件已变化，请重新 ui.inspect");
        int id = integer(arguments, "id", -1);
        if (id < 0 || id >= widgets.size()) throw new IllegalArgumentException("控件 id 不存在");
        GuiEventListener widget = widgets.get(id);
        if (widget instanceof AbstractWidget w && (!w.active || !w.visible)) throw new IllegalStateException("控件不可点击");
        var rectangle = widget.getRectangle();
        return click(object("x", rectangle.left() + rectangle.width() / 2.0,
                "y", rectangle.top() + rectangle.height() / 2.0));
    }

    JsonObject scroll(JsonObject arguments) {
        Screen screen = requireScreen();
        double x = number(arguments, "x", 0), y = number(arguments, "y", 0);
        checkCoordinates(screen, x, y);
        return object("handled", screen.mouseScrolled(x, y, number(arguments, "horizontal", 0), number(arguments, "vertical", 0)));
    }

    JsonObject type(String text) {
        Screen screen = requireScreen();
        if (screen.getFocused() == null) throw new IllegalStateException("没有获得焦点的输入控件，请先点击文本框");
        int accepted = 0;
        for (int point : text.codePoints().toArray()) {
            if (Character.isISOControl(point)) throw new IllegalArgumentException("文本不能包含控制字符；换行请用 input.key enter");
        }
        for (int point : text.codePoints().toArray()) if (screen.charTyped(new CharacterEvent(point))) accepted++;
        return object("acceptedCodepoints", accepted, "requestedCodepoints", text.codePointCount(0, text.length()));
    }

    Screen requireScreen() {
        Screen screen = client.gui.screen();
        if (screen == null) throw new IllegalStateException("当前没有打开界面");
        return screen;
    }
    private String token() { return instance + ":" + revision; }
    static int modifiers(JsonObject arguments) {
        return (bool(arguments, "shift") ? InputConstants.MOD_SHIFT : 0)
                | (bool(arguments, "ctrl") ? InputConstants.MOD_CONTROL : 0)
                | (bool(arguments, "alt") ? InputConstants.MOD_ALT : 0);
    }
    private static void checkCoordinates(Screen screen, double x, double y) {
        if (x >= screen.width || y >= screen.height) throw new IllegalArgumentException("坐标超出 GUI；请使用 guiWidth/guiHeight");
    }
    private static void collect(ContainerEventHandler container, List<GuiEventListener> result, Set<GuiEventListener> seen, int depth) {
        if (depth >= 16) return;
        for (GuiEventListener child : container.children()) {
            if (result.size() >= 2048) return;
            if (!seen.add(child)) continue;
            result.add(child);
            if (child instanceof ContainerEventHandler nested) collect(nested, result, seen, depth + 1);
        }
    }
    private static Field field(Class<?> owner, String name) {
        try { Field result = owner.getDeclaredField(name); result.setAccessible(true); return result; }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("26.3 适配器字段不匹配：" + name, e); }
    }
}
