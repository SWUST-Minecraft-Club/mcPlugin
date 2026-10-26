package com.example.minecraftmcp.mc26_3;

import com.example.minecraftmcp.core.*;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringUtil;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.example.minecraftmcp.mc26_3.Jsons.*;

/** 所有 Minecraft 对象的读写集中在 END_CLIENT_TICK；HTTP 线程只入队。 */
final class Minecraft26Adapter implements GameAdapter {
    private record Job(String tool, JsonObject arguments, CompletableFuture<ToolResult> result, long deadline) {}
    private record Delay(long until, int ticks, CompletableFuture<ToolResult> result) {}
    private final Minecraft client;
    private final ScreenController screens;
    private final StateReader state;
    private final InputController input;
    private final ImageCapture images = new ImageCapture();
    private final Queue<Job> jobs = new ConcurrentLinkedQueue<>();
    private final Semaphore queueCapacity = new Semaphore(128);
    private final List<Delay> delays = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final int timeout;
    private long tick;

    Minecraft26Adapter(Minecraft client, int timeout) {
        this.client = client; this.timeout = timeout;
        this.screens = new ScreenController(client);
        this.state = new StateReader(client);
        this.input = new InputController(client);
    }

    @Override public CompletableFuture<ToolResult> execute(String tool, JsonObject arguments) {
        CompletableFuture<ToolResult> result = new CompletableFuture<>();
        if (closed.get()) { result.complete(ToolResult.error("stopped", "客户端测试服务已停止")); return result; }
        if (!queueCapacity.tryAcquire()) { result.complete(ToolResult.error("busy", "游戏操作队列已满")); return result; }
        jobs.add(new Job(tool, arguments.deepCopy(), result, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout)));
        if (closed.get()) cancelQueue();
        return result;
    }

    void onTick() {
        if (closed.get()) return;
        tick++;
        screens.observe();
        input.tick(tick);
        Iterator<Delay> iterator = delays.iterator();
        while (iterator.hasNext()) {
            Delay delay = iterator.next();
            if (delay.result.isDone() || tick >= delay.until) {
                delay.result.complete(ToolResult.data(object("waitedTicks", delay.ticks, "tick", tick)));
                iterator.remove();
            }
        }
        for (int count = 0; count < 32; count++) {
            Job job = jobs.poll();
            if (job == null) break;
            queueCapacity.release();
            if (job.result.isDone()) continue;
            if (System.nanoTime() >= job.deadline) { job.result.complete(ToolResult.error("timeout", "排队超时，操作未执行")); continue; }
            try { dispatch(job); }
            catch (IllegalArgumentException | IllegalStateException e) { job.result.complete(ToolResult.error("invalid_state", e.getMessage())); }
            catch (Exception e) { job.result.complete(ToolResult.error("adapter_error", "26.3 适配器执行失败：" + e.getClass().getSimpleName())); }
        }
    }

    private void dispatch(Job job) {
        JsonObject a = job.arguments;
        JsonObject data = switch (job.tool) {
            case "client.state" -> object("minecraftVersion", client.getLaunchedVersion(), "adapter", "mc26_3", "tick", tick,
                    "inWorld", client.level != null && client.player != null, "connected", client.getConnection() != null,
                    "fps", client.getFps(), "paused", client.isPaused(), "windowActive", client.isWindowActive(),
                    "windowWidth", client.getWindow().getScreenWidth(), "windowHeight", client.getWindow().getScreenHeight(),
                    "framebufferWidth", client.getWindow().getWidth(), "framebufferHeight", client.getWindow().getHeight(),
                    "guiWidth", client.getWindow().getGuiScaledWidth(), "guiHeight", client.getWindow().getGuiScaledHeight(),
                    "guiScale", client.getWindow().getGuiScale(), "screen", screens.screenInfo());
            case "ui.inspect" -> screens.inspect();
            case "ui.open" -> { input.releaseAll(); yield screens.open(text(a, "screen", "")); }
            case "ui.close" -> { input.releaseAll(); yield screens.close(); }
            case "ui.click" -> screens.click(a);
            case "ui.widget_click" -> screens.widgetClick(a);
            case "ui.scroll" -> screens.scroll(a);
            case "ui.type" -> screens.type(text(a, "text", ""));
            case "input.release_all" -> input.releaseAll();
            case "player.inspect" -> state.player();
            case "inventory.list" -> state.inventory(a);
            case "item.inspect" -> state.inspectItem(a);
            case "inventory.click" -> state.clickSlot(a);
            case "inventory.select_hotbar" -> {
                state.requirePlayer(); client.player.getInventory().setSelectedSlot(integer(a, "slot", 0));
                yield object("selectedHotbar", client.player.getInventory().getSelectedSlot());
            }
            case "world.inspect" -> state.world();
            case "world.block" -> state.block(new BlockPos(integer(a, "x", 0), integer(a, "y", 0), integer(a, "z", 0)));
            case "world.entities" -> state.entities(a);
            case "player.look" -> {
                state.requirePlayer(); client.player.setYRot((float) number(a, "yaw", 0)); client.player.setXRot((float) number(a, "pitch", 0));
                yield object("yaw", client.player.getYRot(), "pitch", client.player.getXRot());
            }
            case "chat.send" -> {
                state.requirePlayer(); String message = text(a, "message", "");
                if (message.isBlank() || message.length() > 256 || !message.codePoints().allMatch(StringUtil::isAllowedChatCharacter))
                    throw new IllegalArgumentException("消息必须为 1..256 个 UTF-16 单位，且不包含游戏禁止的聊天字符");
                if (message.startsWith("/")) {
                    if (message.substring(1).isBlank()) throw new IllegalArgumentException("命令不能为空");
                    client.player.connection.sendCommand(message.substring(1));
                } else client.player.connection.sendChat(message);
                yield object("sent", true, "command", message.startsWith("/"));
            }
            case "chat.history" -> state.chat(integer(a, "limit", 50));
            case "capture.screenshot" -> { images.capture(client, integer(a, "maxWidth", 1280), job.result); yield null; }
            case "input.key" -> { input.key(a, tick, job.result); yield null; }
            case "player.interact" -> { input.interact(text(a, "action", "use"), integer(a, "ticks", 1), tick, job.result); yield null; }
            case "test.wait" -> { int ticks = integer(a, "ticks", 1); delays.add(new Delay(tick + ticks, ticks, job.result)); yield null; }
            default -> throw new IllegalArgumentException("适配器不支持该工具：" + job.tool);
        };
        if (data != null) job.result.complete(ToolResult.data(data));
    }

    private void cancelQueue() {
        Job job;
        while ((job = jobs.poll()) != null) { queueCapacity.release(); job.result.complete(ToolResult.error("stopped", "测试服务已停止")); }
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        cancelQueue();
        Runnable release = () -> {
            input.releaseAll();
            delays.forEach(delay -> delay.result.complete(ToolResult.error("stopped", "测试服务已停止")));
            delays.clear();
        };
        if (Thread.currentThread() == client.getRunningThread()) release.run(); else client.execute(release);
        images.close();
    }
}
