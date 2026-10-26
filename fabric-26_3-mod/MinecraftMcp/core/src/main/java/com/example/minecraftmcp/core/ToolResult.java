package com.example.minecraftmcp.core;

import com.google.gson.*;
import java.util.Base64;

public record ToolResult(JsonObject json) {
    public static ToolResult data(JsonElement value) {
        JsonObject result = new JsonObject();
        JsonArray content = new JsonArray();
        JsonObject text = new JsonObject();
        text.addProperty("type", "text");
        text.addProperty("text", value.toString());
        content.add(text);
        result.add("content", content);
        if (value.isJsonObject()) result.add("structuredContent", value);
        result.addProperty("isError", false);
        return new ToolResult(result);
    }

    public static ToolResult error(String code, String message) {
        JsonObject data = new JsonObject();
        data.addProperty("code", code);
        data.addProperty("message", message);
        ToolResult result = data(data);
        result.json.addProperty("isError", true);
        return result;
    }

    public static ToolResult image(byte[] png, JsonObject metadata) {
        ToolResult result = data(metadata);
        JsonObject image = new JsonObject();
        image.addProperty("type", "image");
        image.addProperty("mimeType", "image/png");
        image.addProperty("data", Base64.getEncoder().encodeToString(png));
        result.json.getAsJsonArray("content").add(image);
        return result;
    }

    public JsonObject data() { return json.getAsJsonObject("structuredContent"); }
    public boolean isError() { return json.get("isError").getAsBoolean(); }
}
