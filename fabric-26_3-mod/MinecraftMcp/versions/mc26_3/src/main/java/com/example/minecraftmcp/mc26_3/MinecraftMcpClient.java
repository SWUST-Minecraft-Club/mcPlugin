package com.example.minecraftmcp.mc26_3;

import com.example.minecraftmcp.core.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

public final class MinecraftMcpClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftMcp");
    private McpServer server;

    @Override public void onInitializeClient() {
        Minecraft26Adapter adapter = null;
        try {
            Path configPath = FabricLoader.getInstance().getConfigDir().resolve("minecraft-mcp.json");
            McpConfig config = McpConfig.load(configPath);
            if (!config.enabled()) { LOGGER.info("MinecraftMcp 已在配置中停用"); return; }
            adapter = new Minecraft26Adapter(Minecraft.getInstance(), config.requestTimeoutMs());
            String version = MinecraftMcpClient.class.getPackage().getImplementationVersion();
            server = new McpServer(config, adapter, version == null ? "development" : version);
            Minecraft26Adapter runningAdapter = adapter;
            ClientTickEvents.END_CLIENT_TICK.register(client -> runningAdapter.onTick());
            ClientLifecycleEvents.CLIENT_STOPPING.register(client -> stop());
            server.start();
            LOGGER.info("MinecraftMcp 已启动：http://127.0.0.1:{}/mcp；Bearer token 位于实例 config/minecraft-mcp.json", server.port());
        } catch (Exception | LinkageError e) {
            if (server != null) stop(); else if (adapter != null) adapter.close();
            // 不输出配置内容，启动失败也不影响普通游戏。
            LOGGER.error("MinecraftMcp 启动失败，请检查 Fabric API、端口和 config/minecraft-mcp.json（{}）", e.getClass().getSimpleName());
        }
    }
    private void stop() {
        if (server != null) { server.close(); server = null; }
    }
}
