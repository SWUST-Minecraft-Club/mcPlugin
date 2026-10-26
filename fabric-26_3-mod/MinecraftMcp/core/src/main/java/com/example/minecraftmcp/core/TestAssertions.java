package com.example.minecraftmcp.core;

import com.google.gson.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** 在 HTTP 工作线程之外轮询；每次查询仍由适配层调度到游戏线程。 */
public final class TestAssertions implements AutoCloseable {
    private final GameAdapter adapter;
    private final ToolCatalog catalog;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(
            r -> { Thread t = new Thread(r, "minecraft-mcp-assertions"); t.setDaemon(true); return t; });

    public TestAssertions(GameAdapter adapter, ToolCatalog catalog) { this.adapter = adapter; this.catalog = catalog; }

    public CompletableFuture<ToolResult> execute(boolean wait, JsonObject arguments) {
        String query = arguments.get("query").getAsString();
        JsonObject queryArguments = arguments.has("arguments") ? arguments.getAsJsonObject("arguments") : new JsonObject();
        catalog.validate(query, queryArguments);
        String path = arguments.get("path").getAsString();
        if (!path.isEmpty() && !path.startsWith("/")) throw new IllegalArgumentException("path 必须为空或以 / 开头的 JSON Pointer");
        String operator = arguments.get("operator").getAsString();
        if (!operator.equals("exists") && !arguments.has("expected")) throw new IllegalArgumentException("该 operator 需要 expected");
        JsonElement expected = arguments.get("expected");
        if ((operator.equals("gte") || operator.equals("lte")) && (expected == null || !expected.isJsonPrimitive()
                || !expected.getAsJsonPrimitive().isNumber())) throw new IllegalArgumentException("数值比较需要数字 expected");
        CompletableFuture<ToolResult> result = new CompletableFuture<>();
        int timeout = wait ? integer(arguments, "timeoutMs", 10000) : 0;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
        AtomicReference<ToolResult> latest = new AtomicReference<>();
        if (wait) {
            ScheduledFuture<?> expiry = timer.schedule(() -> result.complete(latest.get() != null ? latest.get()
                    : ToolResult.error("assertion_timeout", "等待条件超时，游戏线程尚未返回查询结果")), timeout, TimeUnit.MILLISECONDS);
            result.whenComplete((ignored, error) -> expiry.cancel(false));
        }
        poll(result, query, queryArguments, path, operator, expected, wait, deadline, integer(arguments, "intervalMs", 100), latest);
        return result;
    }

    private void poll(CompletableFuture<ToolResult> result, String query, JsonObject arguments, String path,
                      String operator, JsonElement expected, boolean wait, long deadline, int interval, AtomicReference<ToolResult> latest) {
        if (result.isDone()) return;
        CompletableFuture<ToolResult> pending = adapter.execute(query, arguments);
        result.whenComplete((ignored, error) -> { if (!pending.isDone()) pending.cancel(false); });
        pending.whenComplete((value, error) -> {
            if (result.isDone()) return;
            if (error != null) { result.completeExceptionally(error); return; }
            JsonElement actual = value.data() == null ? null : pointer(value.data(), path);
            boolean matched = !value.isError() && matches(actual, operator, expected);
            JsonObject data = new JsonObject();
            data.addProperty("passed", matched);
            data.addProperty("query", query);
            data.addProperty("path", path);
            data.addProperty("operator", operator);
            data.add("actual", actual == null ? JsonNull.INSTANCE : actual);
            if (expected != null) data.add("expected", expected);
            if (value.isError()) data.add("queryError", value.data());
            ToolResult report = ToolResult.data(data);
            report.json().addProperty("isError", !matched);
            latest.set(report);
            if (matched || !wait || System.nanoTime() >= deadline) {
                result.complete(report);
            } else {
                try { timer.schedule(() -> poll(result, query, arguments, path, operator, expected, true, deadline, interval, latest), interval, TimeUnit.MILLISECONDS); }
                catch (RejectedExecutionException e) { result.complete(ToolResult.error("stopped", "测试服务已停止")); }
            }
        });
    }

    static JsonElement pointer(JsonElement root, String path) {
        if (path.isEmpty()) return root;
        JsonElement value = root;
        for (String raw : path.substring(1).split("/", -1)) {
            String key = raw.replace("~1", "/").replace("~0", "~");
            if (value == null) return null;
            if (value.isJsonObject()) value = value.getAsJsonObject().get(key);
            else if (value.isJsonArray()) {
                try {
                    if (!key.matches("0|[1-9][0-9]*")) return null;
                    int index = Integer.parseInt(key);
                    value = index < value.getAsJsonArray().size() ? value.getAsJsonArray().get(index) : null;
                } catch (NumberFormatException e) { return null; }
            } else return null;
        }
        return value;
    }

    static boolean matches(JsonElement actual, String operator, JsonElement expected) {
        if (operator.equals("exists")) return actual != null;
        if (actual == null) return false;
        return switch (operator) {
            case "equals" -> actual.equals(expected);
            case "contains" -> actual.isJsonArray() ? actual.getAsJsonArray().contains(expected)
                    : actual.isJsonPrimitive() && actual.getAsJsonPrimitive().isString()
                    && expected != null && expected.isJsonPrimitive() && expected.getAsJsonPrimitive().isString()
                    && actual.getAsString().contains(expected.getAsString());
            case "gte", "lte" -> actual.isJsonPrimitive() && actual.getAsJsonPrimitive().isNumber()
                    && (operator.equals("gte") ? actual.getAsBigDecimal().compareTo(expected.getAsBigDecimal()) >= 0
                    : actual.getAsBigDecimal().compareTo(expected.getAsBigDecimal()) <= 0);
            default -> false;
        };
    }

    private static int integer(JsonObject a, String name, int fallback) { return a.has(name) ? a.get(name).getAsInt() : fallback; }
    @Override public void close() { timer.shutdownNow(); }
}
