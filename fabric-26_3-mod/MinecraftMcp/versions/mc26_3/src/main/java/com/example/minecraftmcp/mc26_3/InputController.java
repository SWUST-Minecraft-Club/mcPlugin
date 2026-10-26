package com.example.minecraftmcp.mc26_3;

import com.example.minecraftmcp.core.ToolResult;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.sdl.SDLKeyboard;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static com.example.minecraftmcp.mc26_3.Jsons.*;

final class InputController {
    private record Hold(List<KeyMapping> mappings, InputConstants.Key physical, long until,
                        Screen screen, Object level, CompletableFuture<ToolResult> result) {}
    private final Minecraft client;
    private final List<Hold> held = new ArrayList<>();

    InputController(Minecraft client) { this.client = client; }

    void key(JsonObject arguments, long tick, CompletableFuture<ToolResult> result) {
        String name = text(arguments, "key", "").toLowerCase(Locale.ROOT);
        if (name.startsWith("key.keyboard.")) name = name.substring("key.keyboard.".length());
        if (!name.matches("[a-z0-9.]+")) throw new IllegalArgumentException("无效键名");
        InputConstants.Key key = InputConstants.getKey("key.keyboard." + name);
        if (key.equals(InputConstants.UNKNOWN)) throw new IllegalArgumentException("未知键名");
        Screen screen = client.gui.screen();
        if (screen != null) {
            int modifiers = ScreenController.modifiers(arguments);
            KeyEvent event = new KeyEvent(key.getValue(), SDLKeyboard.SDL_GetKeyFromScancode(key.getValue(), (short) modifiers, true), modifiers);
            boolean handled;
            try { handled = screen.keyPressed(event); }
            finally { if (client.gui.screen() == screen) screen.keyReleased(event); }
            result.complete(ToolResult.data(object("handled", handled, "key", name)));
            return;
        }
        if (client.player == null) throw new IllegalStateException("尚未进入世界");
        if (bool(arguments, "shift") || bool(arguments, "ctrl") || bool(arguments, "alt"))
            throw new IllegalArgumentException("世界长按请分别发送 left.shift/left.control 等键；修饰参数仅用于 GUI");
        List<KeyMapping> mappings = Arrays.stream(client.options.keyMappings).filter(mapping -> mapping.matches(key)).toList();
        if (mappings.isEmpty()) throw new IllegalArgumentException("该键未绑定游戏操作；可用 ui.open 打开界面");
        hold(mappings, key, integer(arguments, "ticks", 1), tick, result);
        KeyMapping.click(key);
    }

    void interact(String action, int ticks, long tick, CompletableFuture<ToolResult> result) {
        if (client.player == null || client.level == null) throw new IllegalStateException("尚未进入世界");
        if (client.gui.screen() != null) throw new IllegalStateException("请先关闭当前界面再进行世界交互");
        KeyMapping mapping = action.equals("attack") ? client.options.keyAttack : client.options.keyUse;
        hold(List.of(mapping), null, ticks, tick, result);
        // consumeClick 需要一次点击计数；绑定可以被用户修改，因此从真实映射读取。
        KeyMapping.click(InputConstants.getKey(mapping.saveString()));
    }

    private void hold(List<KeyMapping> mappings, InputConstants.Key key, int ticks, long tick, CompletableFuture<ToolResult> result) {
        if (held.stream().anyMatch(hold -> hold.mappings.stream().anyMatch(mappings::contains)))
            throw new IllegalStateException("该按键正在被其他请求持有");
        mappings.forEach(mapping -> mapping.setDown(true));
        held.add(new Hold(mappings, key, tick + ticks, client.gui.screen(), client.level, result));
    }

    void tick(long tick) {
        Iterator<Hold> iterator = held.iterator();
        while (iterator.hasNext()) {
            Hold hold = iterator.next();
            boolean changed = client.gui.screen() != hold.screen || client.level != hold.level;
            if (tick >= hold.until || hold.result.isDone() || changed) {
                release(hold);
                hold.result.complete(ToolResult.data(object("released", true, "releasedEarly", changed, "tick", tick)));
                iterator.remove();
            }
        }
    }

    JsonObject releaseAll() {
        for (Hold hold : held) {
            release(hold);
            hold.result.complete(ToolResult.data(object("released", true, "releasedEarly", true)));
        }
        int count = held.size(); held.clear();
        return object("releasedInputs", count);
    }
    private static void release(Hold hold) {
        boolean physicalDown = hold.physical != null && InputConstants.isKeyDown(hold.physical.getValue());
        hold.mappings.forEach(mapping -> mapping.setDown(physicalDown));
    }
}
