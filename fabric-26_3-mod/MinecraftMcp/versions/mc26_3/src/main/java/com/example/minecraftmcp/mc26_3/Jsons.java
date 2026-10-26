package com.example.minecraftmcp.mc26_3;

import com.google.gson.*;

final class Jsons {
    private Jsons() {}
    static JsonObject object(Object... fields) {
        JsonObject object = new JsonObject();
        for (int i = 0; i < fields.length; i += 2) object.add((String) fields[i], value(fields[i + 1]));
        return object;
    }
    private static JsonElement value(Object v) {
        if (v == null) return JsonNull.INSTANCE;
        if (v instanceof JsonElement element) return element;
        if (v instanceof Number number) return new JsonPrimitive(number);
        if (v instanceof Boolean bool) return new JsonPrimitive(bool);
        return new JsonPrimitive(v.toString());
    }
    static String text(JsonObject a, String key, String fallback) { return a.has(key) ? a.get(key).getAsString() : fallback; }
    static int integer(JsonObject a, String key, int fallback) { return a.has(key) ? a.get(key).getAsInt() : fallback; }
    static double number(JsonObject a, String key, double fallback) { return a.has(key) ? a.get(key).getAsDouble() : fallback; }
    static boolean bool(JsonObject a, String key) { return a.has(key) && a.get(key).getAsBoolean(); }
}
