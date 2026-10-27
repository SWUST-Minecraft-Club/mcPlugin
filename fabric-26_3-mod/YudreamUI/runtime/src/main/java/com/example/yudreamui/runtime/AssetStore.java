package com.example.yudreamui.runtime;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;

/** Content-addressed UI resources, completely separate from Minecraft's resource-pack manager. */
public final class AssetStore {
    public record Prepared(Map<String, NativeEngine.Image> images, Map<String, byte[]> fonts, Map<String, String> names) {}
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private final Path cache;
    private final boolean localHttp;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
    public final AtomicLong requests = new AtomicLong(), downloads = new AtomicLong(), cacheHits = new AtomicLong();
    public AssetStore(Path cache, boolean localHttp) { this.cache = cache; this.localHttp = localHttp; }
    public CompletableFuture<Prepared> prepare(JsonArray manifest, Executor executor) {
        // The built-in path performs no HTTP request, cache scan, or resource update.
        if (manifest.isEmpty()) return CompletableFuture.completedFuture(new Prepared(Map.of(), Map.of(), Map.of()));
        if (manifest.size() > 16) throw new IllegalArgumentException("Too many UI assets");
        return CompletableFuture.supplyAsync(() -> {
            var images = new HashMap<String, NativeEngine.Image>();
            var fonts = new HashMap<String, byte[]>();
            var names = new HashMap<String, String>();
            long totalDecoded=0, totalBytes=0;
            for (var entry : manifest) {
                var asset = entry.getAsJsonObject();
                String id = asset.get("id").getAsString(), hash = asset.get("hash").getAsString();
                if (!id.matches("[a-zA-Z0-9_-]{1,64}") || !hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid UI asset identity");
                if (names.putIfAbsent(id, hash) != null) throw new IllegalArgumentException("Duplicate UI asset");
                byte[] bytes = load(asset.get("url").getAsString(), hash);
                totalBytes+=bytes.length;
                if(totalBytes>32L*1024*1024)throw new IllegalArgumentException("UI assets exceed aggregate size limit");
                String kind=asset.get("kind").getAsString();
                if (kind.equals("font")) fonts.put(id + "-" + hash, bytes);
                else if(kind.equals("image")) {
                    var image=decode(bytes);totalDecoded+=image.rgba().length;
                    if(totalDecoded>32L*1024*1024)throw new IllegalArgumentException("UI images exceed decoded memory budget");
                    images.put("asset:" + hash, image);
                }else throw new IllegalArgumentException("Unsupported UI asset kind");
            }
            return new Prepared(Map.copyOf(images), Map.copyOf(fonts), Map.copyOf(names));
        }, executor);
    }
    private byte[] load(String url, String hash) {
        try {
            Path file = cache.resolve(hash);
            if (Files.isRegularFile(file) && Files.size(file) <= MAX_BYTES) {
                byte[] bytes = Files.readAllBytes(file);
                if (sha256(bytes).equals(hash)) { cacheHits.incrementAndGet(); return bytes; }
            }
            var uri = URI.create(url);
            boolean loopback = localHttp && uri.getScheme().equals("http") && Set.of("127.0.0.1", "localhost", "[::1]").contains(uri.getHost());
            if (!(uri.getScheme().equals("https") || loopback) || uri.getUserInfo() != null) throw new IllegalArgumentException("UI assets require HTTPS");
            requests.incrementAndGet();
            var response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (var body = response.body()) {
                if (response.statusCode() != 200) throw new IOException("Asset HTTP " + response.statusCode());
                bytes = body.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw new IOException("Asset exceeds size limit");
            }
            if (!sha256(bytes).equals(hash)) throw new IOException("Asset hash mismatch");
            Files.createDirectories(cache);
            Path temporary = Files.createTempFile(cache, "pending-", ".asset");
            try { Files.write(temporary, bytes); Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            finally { Files.deleteIfExists(temporary); }
            downloads.incrementAndGet();
            return bytes;
        } catch (IOException e) { throw new UncheckedIOException(e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CompletionException(e); }
    }
    public static NativeEngine.Image decode(byte[] bytes) {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported UI image");
            var reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 2048 || height > 2048) throw new IOException("Invalid UI image dimensions");
                var image = reader.read(0);
                byte[] rgba = new byte[width * height * 4];
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    int pixel = image.getRGB(x, y), alpha = pixel >>> 24, i = (y * width + x) * 4;
                    rgba[i] = (byte) (((pixel >> 16) & 255) * alpha / 255);
                    rgba[i+1] = (byte) (((pixel >> 8) & 255) * alpha / 255);
                    rgba[i+2] = (byte) ((pixel & 255) * alpha / 255);
                    rgba[i+3] = (byte) alpha;
                }
                return new NativeEngine.Image(rgba, width, height);
            } finally { reader.dispose(); }
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public static String sha256(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
