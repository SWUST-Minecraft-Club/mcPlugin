package com.example.minecraftmcp.core;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** MCP 2025-11-25 无会话 Streamable HTTP；返回 JSON，GET 不提供 SSE。 */
public final class McpServer implements AutoCloseable {
    public static final String PROTOCOL = "2025-11-25";
    private static final Set<String> SUPPORTED = Set.of(PROTOCOL, "2025-06-18", "2025-03-26");
    private static final int MAX_BODY = 1_048_576;
    private final McpConfig config;
    private final GameAdapter adapter;
    private final ToolCatalog catalog = new ToolCatalog();
    private final TestAssertions assertions;
    private final HttpServer http;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore capacity = new Semaphore(16);
    private final Set<CompletableFuture<?>> active = ConcurrentHashMap.newKeySet();
    private final String version;

    public McpServer(McpConfig config, GameAdapter adapter, String version) throws IOException {
        this.config = config;
        this.adapter = adapter;
        this.version = version;
        this.assertions = new TestAssertions(adapter, catalog);
        this.http = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), config.port()), 16);
        http.createContext("/mcp", this::handle);
        http.setExecutor(executor);
    }

    public void start() { if (config.enabled()) http.start(); }
    public int port() { return http.getAddress().getPort(); }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!exchange.getRequestURI().getPath().equals("/mcp")) { send(exchange, 404, null); return; }
            String host = exchange.getRequestHeaders().getFirst("Host");
            if (!Set.of("127.0.0.1:" + port(), "localhost:" + port()).contains(host == null ? "" : host.toLowerCase(Locale.ROOT))) {
                send(exchange, 403, null); return;
            }
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin != null && !Set.of("http://127.0.0.1:" + port(), "http://localhost:" + port()).contains(origin)) {
                send(exchange, 403, null); return;
            }
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (authorization == null || !MessageDigest.isEqual(authorization.getBytes(StandardCharsets.UTF_8),
                    ("Bearer " + config.token()).getBytes(StandardCharsets.UTF_8))) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                send(exchange, 401, null); return;
            }
            if (!exchange.getRequestMethod().equals("POST")) {
                exchange.getResponseHeaders().set("Allow", "POST"); send(exchange, 405, null); return;
            }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim().equals("application/json")) {
                send(exchange, 415, null); return;
            }
            String accept = exchange.getRequestHeaders().getFirst("Accept");
            if (accept == null || !accept.contains("application/json") || !accept.contains("text/event-stream")) {
                send(exchange, 406, null); return;
            }
            String protocol = exchange.getRequestHeaders().getFirst("MCP-Protocol-Version");
            if (protocol != null && !SUPPORTED.contains(protocol)) {
                send(exchange, 400, rpcError(JsonNull.INSTANCE, -32600, "不支持的 MCP-Protocol-Version")); return;
            }
            if (!capacity.tryAcquire()) { send(exchange, 429, null); return; }
            try {
                byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
                if (body.length > MAX_BODY) { send(exchange, 413, null); return; }
                JsonElement parsed;
                try { parsed = new GsonBuilder().setStrictness(Strictness.STRICT).create()
                        .fromJson(new String(body, StandardCharsets.UTF_8), JsonElement.class); }
                catch (JsonParseException e) { send(exchange, 400, rpcError(JsonNull.INSTANCE, -32700, "JSON 解析失败")); return; }
                if (parsed == null || !parsed.isJsonObject()) { send(exchange, 400, rpcError(JsonNull.INSTANCE, -32600, "消息必须为单个 JSON-RPC 对象")); return; }
                dispatch(exchange, parsed.getAsJsonObject());
            } finally { capacity.release(); }
        } catch (IOException ignored) {
            // 客户端断开时不把请求内容或 token 写到日志。
        }
    }

    private void dispatch(HttpExchange exchange, JsonObject request) throws IOException {
        JsonElement id = request.has("id") ? request.get("id") : JsonNull.INSTANCE;
        boolean hasId = request.has("id");
        if (!isString(request.get("jsonrpc")) || !request.get("jsonrpc").getAsString().equals("2.0")
                || !isString(request.get("method")) || (hasId && (!id.isJsonPrimitive()
                || (!id.getAsJsonPrimitive().isNumber() && !id.getAsJsonPrimitive().isString())))
                || (request.has("params") && !request.get("params").isJsonObject())) {
            send(exchange, 400, rpcError(JsonNull.INSTANCE, -32600, "无效 JSON-RPC 请求")); return;
        }
        String method = request.get("method").getAsString();
        if (!hasId) {
            // 接受通知但绝不执行没有 id 的 tools/call。
            send(exchange, 202, null); return;
        }
        JsonObject params = request.has("params") ? request.getAsJsonObject("params") : new JsonObject();
        JsonObject result;
        try {
            result = switch (method) {
                case "initialize" -> initialize(params);
                case "ping" -> new JsonObject();
                case "tools/list" -> {
                    if (params.has("cursor")) throw new IllegalArgumentException("本工具列表无分页，不接受 cursor");
                    JsonObject list = new JsonObject(); list.add("tools", catalog.list()); yield list;
                }
                case "tools/call" -> call(params).json();
                default -> null;
            };
        } catch (IllegalArgumentException | IllegalStateException e) {
            send(exchange, 200, rpcError(id, -32602, e.getMessage())); return;
        } catch (Exception e) {
            send(exchange, 200, rpcError(id, -32603, "内部处理失败")); return;
        }
        if (result == null) { send(exchange, 200, rpcError(id, -32601, "未知方法：" + method)); return; }
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0"); response.add("id", id); response.add("result", result);
        send(exchange, 200, response);
    }

    private JsonObject initialize(JsonObject params) {
        if (!isString(params.get("protocolVersion")) || !params.has("capabilities") || !params.get("capabilities").isJsonObject()
                || !params.has("clientInfo") || !params.get("clientInfo").isJsonObject())
            throw new IllegalArgumentException("initialize 需要 protocolVersion、capabilities 和 clientInfo");
        JsonObject result = new JsonObject();
        String requested = params.get("protocolVersion").getAsString();
        result.addProperty("protocolVersion", SUPPORTED.contains(requested) ? requested : PROTOCOL);
        JsonObject capabilities = new JsonObject();
        JsonObject tools = new JsonObject(); tools.addProperty("listChanged", false);
        capabilities.add("tools", tools); result.add("capabilities", capabilities);
        JsonObject info = new JsonObject(); info.addProperty("name", "MinecraftMcp"); info.addProperty("version", version);
        result.add("serverInfo", info);
        result.addProperty("instructions", "先 client.state，再 ui.inspect/inventory.list 取得 GUI 坐标、screenToken、menuId；截图尺寸可能已缩放。操作后用 test.wait_for 验证服务端同步结果。所有游戏状态只能通过版本适配层访问。");
        return result;
    }

    private ToolResult call(JsonObject params) {
        if (!isString(params.get("name"))) throw new IllegalArgumentException("tools/call 需要 name");
        if (params.has("arguments") && !params.get("arguments").isJsonObject()) throw new IllegalArgumentException("arguments 必须为对象");
        String name = params.get("name").getAsString();
        JsonObject arguments = params.has("arguments") ? params.getAsJsonObject("arguments") : new JsonObject();
        catalog.validate(name, arguments);
        if (name.equals("chat.send")) {
            String message = arguments.get("message").getAsString();
            if (message.startsWith("/") ? !config.allowCommands() : !config.allowChat())
                return ToolResult.error("disabled", "该能力已被配置禁用");
        }
        CompletableFuture<ToolResult> pending = name.equals("test.assert") || name.equals("test.wait_for")
                ? assertions.execute(name.equals("test.wait_for"), arguments) : adapter.execute(name, arguments);
        active.add(pending);
        int timeout = name.equals("test.wait_for") ? Math.max(config.requestTimeoutMs(),
                (arguments.has("timeoutMs") ? arguments.get("timeoutMs").getAsInt() : 10000) + config.requestTimeoutMs()) : config.requestTimeoutMs();
        try { return pending.get(timeout, TimeUnit.MILLISECONDS); }
        catch (TimeoutException e) { pending.cancel(false); return ToolResult.error("timeout", "等待游戏线程超时；未执行的请求已取消"); }
        catch (InterruptedException e) { pending.cancel(false); Thread.currentThread().interrupt(); return ToolResult.error("stopped", "测试服务已停止"); }
        catch (ExecutionException | CancellationException e) { return ToolResult.error("operation_failed", "游戏操作失败或已取消"); }
        finally { active.remove(pending); }
    }

    private static boolean isString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }
    private static JsonObject rpcError(JsonElement id, int code, String message) {
        JsonObject response = new JsonObject(), error = new JsonObject();
        response.addProperty("jsonrpc", "2.0"); response.add("id", id);
        error.addProperty("code", code); error.addProperty("message", message);
        response.add("error", error); return response;
    }
    private static void send(HttpExchange exchange, int status, JsonObject body) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        if (body == null) { exchange.sendResponseHeaders(status, -1); return; }
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override public void close() {
        http.stop(0);
        active.forEach(future -> future.cancel(false));
        assertions.close();
        executor.shutdownNow();
        adapter.close();
    }
}
