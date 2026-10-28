package com.example.yudreamui.runtime;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;

/** RmlUi owner. Every operation runs on the host's UI thread; the core has no game imports. */
public final class NativeEngine implements AutoCloseable {
    public record Geometry(float[] xyuv, int[] colors, int[] indices) {}
    public sealed interface Command permits Draw, Item, Terrain {}
    public record Terrain(com.example.yudreamui.protocol.TerrainSpec spec, float x, float y, float width, float height, int clipX, int clipY, int clipWidth, int clipHeight) implements Command {}
    public record Item(String identifier, float x, float y, float width, float height, int clipX, int clipY, int clipWidth, int clipHeight) implements Command {}
    public record Draw(Geometry geometry, long texture, float x, float y, int clipX, int clipY, int clipWidth, int clipHeight, float[] transform) implements Command {}
    public record Image(byte[] rgba, int width, int height) {}
    public record Event(String action, String id, String value) {}
    public interface Textures {
        void create(long id, Image image);
        void retire(long id);
    }
    private static boolean loaded;
    private long handle;
    private final Map<Long, Geometry> geometries = new HashMap<>();
    private final List<Command> draws = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();
    private final Textures textures;
    private final Consumer<String> logger;
    private final Map<String,Image> builtins = new HashMap<>();
    public long frames, lastFrameNanos, maxFrameNanos;

    public NativeEngine(Textures textures, Consumer<String> logger) {
        load();
        this.textures = textures; this.logger = logger;
        handle = create();
        if (handle == 0) throw new IllegalStateException("RmlUi initialization failed");
        try { font(readResource("/builtin/ui-font.otf"), "mc-ui"); }
        catch (RuntimeException e) { close(); throw e; }
    }
    private static synchronized void load() {
        if (loaded) return;
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        os = os.contains("win") ? "windows" : os.contains("mac") ? "macos" : "linux";
        String arch = System.getProperty("os.arch");
        arch = arch.equals("amd64") ? "x86_64" : arch.equals("arm64") ? "aarch64" : arch;
        String name = os.equals("windows") ? "yudreamui.dll" : os.equals("macos") ? "libyudreamui.dylib" : "libyudreamui.so";
        byte[] bytes = readResource("/natives/" + os + "-" + arch + "/" + name);
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            Path dir = Files.createTempDirectory("yudream-ui-" + hash.substring(0, 12));
            Path file = dir.resolve(name);
            Files.write(file, bytes);
            dir.toFile().deleteOnExit(); file.toFile().deleteOnExit();
            System.load(file.toAbsolutePath().toString());
            loaded = true;
        } catch (Exception e) { throw new IllegalStateException("Could not load UI native runtime", e); }
    }
    public static byte[] readResource(String path) {
        try (var input = NativeEngine.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing bundled UI resource: " + path);
            return input.readAllBytes();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public void open(String rml, int width, int height) {
        for (String name : List.of("village", "guide")) {
            String id="builtin:atlas-"+name;
            if (rml.contains(id)) image(id,builtins.computeIfAbsent(name,k->AssetStore.decode(readResource("/builtin/art/"+k+".png"))));
        }
        open(handle, rml, width, height); pruneImages(handle);
    }
    public void hide() { hide(handle); pruneImages(handle); }
    public List<Command> frame(int width, int height, int mouseX, int mouseY) {
        long start = System.nanoTime(); draws.clear();
        frame(handle, width, height, mouseX, mouseY);
        lastFrameNanos = System.nanoTime() - start; maxFrameNanos = Math.max(maxFrameNanos, lastFrameNanos); frames++;
        return draws;
    }
    public void image(String name, Image value) { image(handle, name, value.rgba, value.width, value.height); }
    public void font(byte[] bytes, String family) { if (!font(handle, bytes, family)) throw new IllegalArgumentException("Invalid UI font"); }
    public void patch(JsonObject state) { state.entrySet().forEach(e -> text(handle, e.getKey(), e.getValue().getAsString())); }
    public void mouse(int type, float x, float y, int button, int modifiers) { mouse(handle, type, x, y, button, modifiers); }
    public void scroll(float x, float y, float delta) { mouse(handle, 0, x, y, 0, 0); scroll(handle, delta); }
    public void key(int key, int modifiers, boolean down) { key(handle, key, modifiers, down); }
    public void clipboard(String text) { clipboard(handle, text); }
    public void type(String text) { type(handle, text); }
    public JsonArray inspect() { return JsonParser.parseString(inspect(handle)).getAsJsonArray(); }
    public List<Event> drainEvents() { var result = List.copyOf(events); events.clear(); return result; }
    @Override public void close() { if (handle != 0) { destroy(handle); handle = 0; } geometries.clear(); draws.clear(); }

    // Native callbacks own detached immutable data, safe for deferred host rendering.
    private void geometry(long id, float[] xyuv, int[] colors, int[] indices) { geometries.put(id, new Geometry(xyuv, colors, indices)); }
    private void draw(long geometry, long texture, float x, float y, int cx, int cy, int cw, int ch, float[] matrix) {
        var value = geometries.get(geometry);
        if (value != null) draws.add(new Draw(value, texture, x, y, cx, cy, cw, ch, matrix));
    }
    private void item(String identifier,float x,float y,float width,float height,int cx,int cy,int cw,int ch) { draws.add(new Item(identifier,x,y,width,height,cx,cy,cw,ch)); }
    private void terrain(String dimension,int chunkX,int chunkZ,int chunks,float x,float y,float width,float height,int cx,int cy,int cw,int ch) {
        draws.add(new Terrain(new com.example.yudreamui.protocol.TerrainSpec(dimension,chunkX,chunkZ,chunks),x,y,width,height,cx,cy,cw,ch));
    }
    private void texture(long id, byte[] rgba, int width, int height) { textures.create(id, new Image(rgba, width, height)); }
    private void release(long id, boolean texture) { if (texture) textures.retire(id); else geometries.remove(id); }
    private void event(String action, String id, String value) { events.add(new Event(action, id, value)); }
    private void log(String text) { logger.accept(text); }
    private native long create();
    private native void destroy(long handle);
    private native void open(long handle, String rml, int width, int height);
    private native void hide(long handle);
    private native void frame(long handle, int width, int height, int mouseX, int mouseY);
    private native void image(long handle, String name, byte[] rgba, int width, int height);
    private native boolean font(long handle, byte[] bytes, String family);
    private native void text(long handle, String binding, String value);
    private native void mouse(long handle, int type, float x, float y, int button, int modifiers);
    private native void scroll(long handle, float delta);
    private native void key(long handle, int key, int modifiers, boolean down);
    private native void type(long handle, String text);
    private native void clipboard(long handle, String text);
    private native void pruneImages(long handle);
    private native String inspect(long handle);
}
