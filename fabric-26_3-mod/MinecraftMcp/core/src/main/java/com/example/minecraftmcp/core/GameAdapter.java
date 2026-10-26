package com.example.minecraftmcp.core;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;

/** 唯一的版本边界：协议、校验与测试编排不依赖 Minecraft/Fabric 类。 */
public interface GameAdapter extends AutoCloseable {
    CompletableFuture<ToolResult> execute(String tool, JsonObject arguments);
    @Override void close();
}
