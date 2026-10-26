package com.example.minecraftmcp.core;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class McpServerTest {
    private static final String TOKEN = "a".repeat(43);
    private McpServer server;
    private FakeAdapter adapter;
    private final HttpClient client = HttpClient.newHttpClient();
    private URI endpoint;

    @BeforeEach void start() throws Exception {
        adapter = new FakeAdapter();
        server = new McpServer(new McpConfig(true, 0, TOKEN, 1000, false, false), adapter, "test");
        server.start();
        endpoint = URI.create("http://127.0.0.1:" + server.port() + "/mcp");
    }
    @AfterEach void close() { server.close(); }

    private HttpRequest.Builder request() {
        return HttpRequest.newBuilder(endpoint).header("Authorization", "Bearer " + TOKEN)
                .header("Accept", "application/json, text/event-stream").header("Content-Type", "application/json");
    }
    private HttpResponse<String> post(String body) throws Exception {
        return client.send(request().POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonObject rpc(String method, JsonObject params) throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0"); request.addProperty("id", 7); request.addProperty("method", method); request.add("params", params);
        var response = post(request.toString());
        assertEquals(200, response.statusCode(), response.body());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    private JsonObject call(String name, String args) throws Exception {
        JsonObject params = new JsonObject(); params.addProperty("name", name); params.add("arguments", JsonParser.parseString(args));
        return rpc("tools/call", params);
    }

    @Test void initializesAndNegotiatesLegacyVersions() throws Exception {
        for (String version : List.of("2025-11-25", "2025-06-18", "2025-03-26", "future")) {
            JsonObject params = JsonParser.parseString("{\"protocolVersion\":\"" + version + "\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}").getAsJsonObject();
            JsonObject result = rpc("initialize", params).getAsJsonObject("result");
            assertEquals(version.equals("future") ? McpServer.PROTOCOL : version, result.get("protocolVersion").getAsString());
            assertTrue(result.getAsJsonObject("capabilities").has("tools"));
        }
    }
    @Test void advertisesAllToolsWithSchemas() throws Exception {
        JsonArray tools = rpc("tools/list", new JsonObject()).getAsJsonObject("result").getAsJsonArray("tools");
        assertEquals(26, tools.size());
        Set<String> names = new HashSet<>();
        for (JsonElement tool : tools) {
            JsonObject object = tool.getAsJsonObject();
            assertTrue(names.add(object.get("name").getAsString()));
            assertFalse(object.getAsJsonObject("inputSchema").get("additionalProperties").getAsBoolean());
        }
    }
    @Test void requiresBearerAuthentication() throws Exception {
        var response = client.send(HttpRequest.newBuilder(endpoint).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
        assertFalse(response.body().contains(TOKEN));
    }
    @Test void rejectsBrowserOriginsAndPathPrefixes() throws Exception {
        var response = client.send(request().header("Origin", "https://evil.example")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode());
        endpoint = URI.create(endpoint + "/other");
        assertEquals(404, post("{}").statusCode());
    }
    @Test void rejectsWrongProtocolMediaAndMethod() throws Exception {
        assertEquals(405, client.send(request().GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(400, client.send(request().header("MCP-Protocol-Version", "future")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(415, client.send(HttpRequest.newBuilder(endpoint).header("Authorization", "Bearer " + TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    @Test void doesNotExecuteNotificationsOrBatches() throws Exception {
        assertEquals(202, post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}").statusCode());
        assertEquals(202, post("{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"ui.close\"}}").statusCode());
        assertEquals(400, post("[]").statusCode());
        assertEquals(0, adapter.calls.get());
    }
    @Test void rejectsMalformedAndOversizedBodies() throws Exception {
        assertEquals(-32700, JsonParser.parseString(post("{").body()).getAsJsonObject().getAsJsonObject("error").get("code").getAsInt());
        assertEquals(400, post("{unquoted: 'invalid'}").statusCode());
        assertEquals(413, post(" ".repeat(1_048_577)).statusCode());
    }
    @Test void validatesArgumentsBeforeSchedulingGameWork() throws Exception {
        for (String args : List.of("{}", "{\"slot\":1.2}", "{\"slot\":9}", "{\"slot\":1e1000000}", "{\"slot\":\"1\"}", "{\"slot\":1,\"extra\":true}")) {
            assertEquals(-32602, call("inventory.select_hotbar", args).getAsJsonObject("error").get("code").getAsInt());
        }
        assertEquals(-32602, call("unknown", "{}").getAsJsonObject("error").get("code").getAsInt());
        assertEquals(0, adapter.calls.get());
    }
    @Test void respectsCommandAndChatConfiguration() throws Exception {
        for (String message : List.of("/op user", "hello")) {
            assertTrue(call("chat.send", "{\"message\":\"" + message + "\"}").getAsJsonObject("result").get("isError").getAsBoolean());
        }
        assertEquals(0, adapter.calls.get());
    }
    @Test void reportsToolErrorsAndEmbedsScreenshotImage() throws Exception {
        JsonObject result = call("capture.screenshot", "{}").getAsJsonObject("result");
        assertEquals("image", result.getAsJsonArray("content").get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("image/png", result.getAsJsonArray("content").get(1).getAsJsonObject().get("mimeType").getAsString());
        adapter.fail = true;
        assertTrue(call("ui.close", "{}").getAsJsonObject("result").get("isError").getAsBoolean());
    }
    @Test void cancelsWorkThatTimesOutBeforeItCanRun() throws Exception {
        adapter.pending = new CompletableFuture<>();
        JsonObject result = call("client.state", "{}").getAsJsonObject("result");
        assertTrue(result.get("isError").getAsBoolean());
        assertEquals("timeout", result.getAsJsonObject("structuredContent").get("code").getAsString());
        assertTrue(adapter.pending.isCancelled());
    }
    @Test void assertionsDistinguishMissingNullFalseAndNumericValues() throws Exception {
        for (String condition : List.of(
                "\"path\":\"/ready\",\"operator\":\"equals\",\"expected\":false",
                "\"path\":\"/nullable\",\"operator\":\"exists\"",
                "\"path\":\"/counter\",\"operator\":\"gte\",\"expected\":1")) {
            assertFalse(call("test.assert", "{\"query\":\"client.state\"," + condition + "}").getAsJsonObject("result").get("isError").getAsBoolean());
        }
        assertTrue(call("test.assert", "{\"query\":\"client.state\",\"path\":\"/missing\",\"operator\":\"exists\"}")
                .getAsJsonObject("result").get("isError").getAsBoolean());
    }
    @Test void waitForPollsAndReportsTimeoutWithoutBlockingGameThread() throws Exception {
        JsonObject success = call("test.wait_for", "{\"query\":\"client.state\",\"path\":\"/counter\",\"operator\":\"gte\",\"expected\":3,\"timeoutMs\":2000,\"intervalMs\":50}").getAsJsonObject("result");
        assertFalse(success.get("isError").getAsBoolean());
        assertTrue(adapter.calls.get() >= 3);
        JsonObject failure = call("test.wait_for", "{\"query\":\"client.state\",\"path\":\"/missing\",\"operator\":\"exists\",\"timeoutMs\":75,\"intervalMs\":50}").getAsJsonObject("result");
        assertTrue(failure.get("isError").getAsBoolean());
    }
    @Test void jsonPointerEscapesAndArrayBoundsWork() {
        JsonElement value = JsonParser.parseString("{\"a/b\":{\"~\":[false,null]}}");
        assertEquals(JsonNull.INSTANCE, TestAssertions.pointer(value, "/a~1b/~0/1"));
        assertNull(TestAssertions.pointer(value, "/a~1b/~0/2"));
        assertNull(TestAssertions.pointer(value, "/a~1b/~0/-1"));
    }
    @Test void waitForDeadlineCancelsAnUnresponsiveGameQuery() throws Exception {
        adapter.pending = new CompletableFuture<>();
        JsonObject result = call("test.wait_for", "{\"query\":\"client.state\",\"path\":\"/ready\",\"operator\":\"equals\",\"expected\":true,\"timeoutMs\":50}").getAsJsonObject("result");
        assertTrue(result.get("isError").getAsBoolean());
        assertEquals("assertion_timeout", result.getAsJsonObject("structuredContent").get("code").getAsString());
        assertTrue(adapter.pending.isCancelled());
    }
    @Test void persistsTokenAndRejectsInvalidExistingConfig(@TempDir Path directory) throws Exception {
        Path config = directory.resolve("config/minecraft-mcp.json");
        McpConfig first = McpConfig.load(config);
        assertEquals(first, McpConfig.load(config));
        assertTrue(first.token().length() >= 32);
        Files.writeString(config, "{\"token\":\"short\"}");
        assertThrows(IllegalArgumentException.class, () -> McpConfig.load(config));
    }

    private static final class FakeAdapter implements GameAdapter {
        final AtomicInteger calls = new AtomicInteger();
        boolean fail;
        CompletableFuture<ToolResult> pending;
        @Override public CompletableFuture<ToolResult> execute(String tool, JsonObject arguments) {
            int count = calls.incrementAndGet();
            if (pending != null) return pending;
            if (fail) return CompletableFuture.completedFuture(ToolResult.error("no_world", "未进入世界"));
            JsonObject data = new JsonObject();
            data.addProperty("counter", count); data.addProperty("ready", false); data.add("nullable", JsonNull.INSTANCE);
            return CompletableFuture.completedFuture(tool.equals("capture.screenshot")
                    ? ToolResult.image(new byte[]{1, 2, 3}, data) : ToolResult.data(data));
        }
        @Override public void close() {}
    }
}
