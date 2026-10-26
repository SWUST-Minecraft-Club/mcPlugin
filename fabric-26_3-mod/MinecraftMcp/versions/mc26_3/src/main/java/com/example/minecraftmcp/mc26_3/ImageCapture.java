package com.example.minecraftmcp.mc26_3;

import com.example.minecraftmcp.core.ToolResult;
import com.google.gson.JsonObject;
import net.minecraft.client.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import static com.example.minecraftmcp.mc26_3.Jsons.*;

final class ImageCapture implements AutoCloseable {
    private final ExecutorService encoder = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "minecraft-mcp-png"); thread.setDaemon(true); return thread;
    });
    private final AtomicBoolean busy = new AtomicBoolean();

    void capture(Minecraft client, int maxWidth, CompletableFuture<ToolResult> result) {
        if (!busy.compareAndSet(false, true)) throw new IllegalStateException("上一张截图尚未完成");
        JsonObject metadata = object("guiWidth", client.getWindow().getGuiScaledWidth(),
                "guiHeight", client.getWindow().getGuiScaledHeight(), "capturedAt", Instant.now());
        try {
            Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> {
                if (result.isDone()) { image.close(); busy.set(false); return; }
                try {
                    encoder.execute(() -> {
                        try (image) {
                            int sourceWidth = image.getWidth(), sourceHeight = image.getHeight();
                            int width = Math.min(sourceWidth, maxWidth);
                            int height = Math.max(1, (int) Math.round((double) sourceHeight * width / sourceWidth));
                            BufferedImage source = new BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB);
                            source.setRGB(0, 0, sourceWidth, sourceHeight, image.getPixels(), 0, sourceWidth);
                            BufferedImage output = source;
                            if (width != sourceWidth) {
                                output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                                Graphics2D graphics = output.createGraphics();
                                try {
                                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                                    graphics.drawImage(source, 0, 0, width, height, null);
                                } finally { graphics.dispose(); }
                            }
                            ByteArrayOutputStream png = new ByteArrayOutputStream();
                            if (!ImageIO.write(output, "png", png)) throw new IllegalStateException("PNG 编码器不可用");
                            if (png.size() > 12 * 1024 * 1024) throw new IllegalStateException("截图超过 12 MiB，请调小 maxWidth");
                            metadata.addProperty("sourceWidth", sourceWidth); metadata.addProperty("sourceHeight", sourceHeight);
                            metadata.addProperty("width", width); metadata.addProperty("height", height);
                            metadata.addProperty("guiToImageX", (double) width / metadata.get("guiWidth").getAsInt());
                            metadata.addProperty("guiToImageY", (double) height / metadata.get("guiHeight").getAsInt());
                            result.complete(ToolResult.image(png.toByteArray(), metadata));
                        } catch (Exception e) { result.complete(ToolResult.error("capture_failed", "截图读取或 PNG 编码失败")); }
                        finally { busy.set(false); }
                    });
                } catch (RejectedExecutionException e) { image.close(); busy.set(false); result.complete(ToolResult.error("stopped", "截图服务已停止")); }
            });
        } catch (Exception e) { busy.set(false); throw e; }
    }
    @Override public void close() { encoder.shutdown(); }
}
