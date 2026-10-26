package com.example.mainmenu.arcmenu;

import com.example.mainmenu.MainMenuPlugin;
import com.example.mainmenu.api.MenuSidecard;
import com.example.mainmenu.sidecard.SidecardLayout;
import com.example.mainmenu.sidecard.SidecardService.Rendered;
import com.example.mainmenu.sidecard.SidecardSettings;
import com.fentai.arcmenu.api.ArcMenuSurface;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.joml.Matrix4f;

/** Private Bukkit displays on the native menu's authoritative plane; the phone stays native YAML. */
final class SidecardScene implements AutoCloseable {
    static final double G = 42.7 / 160.5 * .86;
    private static final double ZK = .00305 * 160.5 / 42.7;
    private static final double RECT_WIDTH = 1.01;
    private static final double RECT_HEIGHT = .915;
    record Hit(String id, String action, double x, double y, double width, double height, int delta) {
        boolean contains(final double px, final double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
    private record Visual(Display entity, Matrix4f local) { }
    private record Text(TextDisplay entity, double width, double size, int color, double x, double y, boolean centered) { }
    private record Meter(List<TextDisplay> cells) { }
    private final MainMenuPlugin plugin;
    private final Player player;
    private final SidecardSettings.Palette palette;
    private final Location anchor;
    private final List<Visual> visuals = new ArrayList<>();
    private final Map<String, Text> texts = new HashMap<>();
    private final Map<String, Meter> meters = new HashMap<>();
    private final Map<String, List<TextDisplay>> borders = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private Matrix4f base;
    private String hovered = "";
    private boolean closed;

    SidecardScene(final MainMenuPlugin plugin, final Player player, final ArcMenuSurface surface,
                  final SidecardSettings config, final SidecardLayout.Page page,
                  final Map<String, Rendered> cards, final int pageIndex, final int pageCount) {
        this.plugin = plugin;
        this.player = player;
        this.palette = config.palette();
        // Spawn in the viewer's owned region. Matrices position visuals on the menu plane.
        this.anchor = player.getLocation().clone();
        anchor.setYaw(0); anchor.setPitch(0);
        base = basis(surface);
        try {
            for (final var placement : page.cards()) drawCard(placement, cards.get(placement.id()));
            if (pageCount > 1) for (final double x : new double[] {config.area().leftX(), config.area().rightX()}) {
                final double y = config.area().top() + config.area().height() + 14;
                final double width = config.area().width();
                if (pageIndex > 0) footer(x + 8, y, "‹", -1);
                if (pageIndex + 1 < pageCount) footer(x + width - 34, y, "›", 1);
                text("page-" + x, Component.text("侧卡 " + (pageIndex + 1) + " / " + pageCount),
                        x + width / 2, y + 11, width - 76, 8, palette.text(), true);
            }
            update(cards);
            for (final var visual : visuals) player.showEntity(plugin, visual.entity());
        } catch (final RuntimeException e) {
            close();
            throw e;
        }
    }

    private void drawCard(final SidecardLayout.Placement p, final Rendered card) {
        final double x = p.x(), y = p.y(), w = p.width(), h = p.height();
        borders.put(p.id(), panel(x, y, w, h, palette.border(), -2));
        panel(x + 1.5, y + 1.5, w - 3, h - 3, palette.background(), -1);
        rectangle(x + w - 15, y + 12, 3, 3, palette.border(), 0);
        if (card.content().icon() != null) item(card.content(), x + 26, y + 25, 25);
        final double titleX = x + (card.content().icon() == null ? 14 : 46);
        text(p.id() + ":title", card.content().title(), titleX, y + 25, x + w - 23 - titleX,
                10, palette.text(), false);
        double rowY = y + 55;
        for (int i = 0; i < card.content().rows().size(); i++) {
            final var row = card.content().rows().get(i);
            final String prefix = p.id() + ":row:" + i;
            text(prefix + ":label", row.label(), x + 14, rowY, w * .48 - 14, 8.8, palette.muted(), false);
            text(prefix + ":value", row.value(), x + w * .52, rowY, w * .48 - 14, 9, palette.value(), false);
            if (row.progress() >= 0) {
                final List<TextDisplay> cells = new ArrayList<>();
                final double cellWidth = (w - 28 - 27) / 10;
                for (int cell = 0; cell < 10; cell++) {
                    cells.add(rectangle(x + 14 + cell * (cellWidth + 3), rowY + 12, cellWidth, 5.5, palette.track(), 0));
                }
                meters.put(prefix, new Meter(List.copyOf(cells)));
            }
            rowY += row.progress() >= 0 ? 32 : 20;
        }
        // Buttons precede the full panel in hit testing.
        final int count = card.content().buttons().size();
        for (int i = 0; i < count; i++) {
            final var button = card.content().buttons().get(i);
            final double bw = (w - 28 - (count - 1) * 8) / count;
            final double bx = x + 14 + i * (bw + 8), by = y + h - 36;
            panel(bx, by, bw, 24, palette.button(), 0);
            text(p.id() + ":button:" + button.id(), button.label(), bx + bw / 2, by + 12,
                    bw - 12, 8.5, palette.text(), true);
            if (card.clickable(button.id())) hits.add(new Hit(p.id(), button.id(), bx, by, bw, 24, 0));
        }
        if (card.clickable()) hits.add(new Hit(p.id(), "card", x, y, w, h, 0));
    }

    private void footer(final double x, final double y, final String label, final int delta) {
        panel(x, y, 26, 22, palette.background(), 0);
        text("footer-" + x, Component.text(label), x + 13, y + 11, 20, 12, palette.text(), true);
        hits.add(new Hit("", "page", x, y, 26, 22, delta));
    }

    void update(final Map<String, Rendered> cards) {
        for (final var entry : cards.entrySet()) {
            final String id = entry.getKey();
            final var content = entry.getValue().content();
            replace(id + ":title", content.title());
            for (int i = 0; i < content.rows().size(); i++) {
                final var row = content.rows().get(i);
                final String key = id + ":row:" + i;
                replace(key + ":label", row.label());
                replace(key + ":value", row.value());
                final var meter = meters.get(key);
                if (meter != null) for (int cell = 0; cell < 10; cell++) {
                    final Color color = Color.fromRGB(cell < Math.round(row.progress() * 10) ? palette.border() : palette.track());
                    final var display = meter.cells().get(cell);
                    if (!color.equals(display.getBackgroundColor())) display.setBackgroundColor(color);
                }
            }
            content.buttons().forEach(button -> replace(id + ":button:" + button.id(), button.label()));
        }
    }

    private void replace(final String key, final Component content) {
        final Text text = texts.get(key);
        if (text == null) return;
        final Component clipped = clip(content, text.width(), text.size()).colorIfAbsent(TextColor.color(text.color()));
        if (!clipped.equals(text.entity().text())) text.entity().text(clipped);
        final double ink = ink(clipped, text.size());
        final double centerX = text.centered() ? text.x() : text.x() + ink / 2;
        final Matrix4f next = offset(centerX, text.y() + .55 * text.size(), 2)
                .scale((float) (G * text.size() / (1 + ZK * 2) / .225));
        final Visual visual = visuals.stream().filter(v -> v.entity() == text.entity()).findFirst().orElseThrow();
        if (!visual.local().equals(next)) {
            visual.local().set(next);
            text.entity().setTransformationMatrix(new Matrix4f(base).mul(next));
        }
    }

    Hit hit(final double x, final double y) {
        return hits.stream().filter(hit -> hit.contains(x, y)).findFirst().orElse(null);
    }

    void hover(final Hit hit) {
        final String id = hit == null ? "" : hit.id();
        if (hovered.equals(id)) return;
        if (borders.containsKey(hovered)) borders.get(hovered).forEach(d -> d.setBackgroundColor(Color.fromRGB(palette.border())));
        if (borders.containsKey(id)) borders.get(id).forEach(d -> d.setBackgroundColor(Color.fromRGB(palette.hover())));
        hovered = id;
    }

    void surface(final ArcMenuSurface surface) {
        final Matrix4f next = basis(surface);
        if (base.equals(next, .00001f)) return;
        base = next;
        visuals.forEach(v -> v.entity().setTransformationMatrix(new Matrix4f(base).mul(v.local())));
    }

    private List<TextDisplay> panel(final double x, final double y, final double w, final double h, final int color, final int z) {
        return List.of(rectangle(x + 4, y, w - 8, 4, color, z),
                rectangle(x, y + 4, w, h - 8, color, z), rectangle(x + 4, y + h - 4, w - 8, 4, color, z));
    }

    private TextDisplay rectangle(final double x, final double y, final double w, final double h, final int color, final int z) {
        final TextDisplay display = spawn(TextDisplay.class);
        display.text(Component.space());
        display.setBackgroundColor(Color.fromRGB(color));
        final double s = 1 + ZK * z, height = h / RECT_HEIGHT;
        final float rw = (float) (G * w / s / RECT_WIDTH), rh = (float) (G * height / s);
        final Matrix4f local = offset(x + w / 2, y + h - height / 2, z)
                .translate(-rw / 2, -rh / 2, 0).scale(rw, rh, 1).translate(.4f, 0, 0).scale(8.08f, 3.66f, 1);
        add(display, local);
        return display;
    }

    private void text(final String key, final Component content, final double x, final double y, final double width,
                      final double size, final int color, final boolean centered) {
        final TextDisplay display = spawn(TextDisplay.class);
        display.setBackgroundColor(Color.fromARGB(0));
        display.setLineWidth(8192);
        texts.put(key, new Text(display, width, size, color, x, y, centered));
        add(display, new Matrix4f());
        replace(key, content);
    }

    private void item(final MenuSidecard.Content content, final double x, final double y, final double size) {
        final ItemDisplay display = spawn(ItemDisplay.class);
        display.setItemStack(new ItemStack(content.icon()));
        final boolean gui = content.iconContext() == MenuSidecard.IconContext.GUI;
        display.setItemDisplayTransform(gui ? ItemDisplay.ItemDisplayTransform.GUI : ItemDisplay.ItemDisplayTransform.FIXED);
        final float value = (float) (G * size / (1 + ZK));
        final Matrix4f transform = offset(x, y, 1);
        if (gui) transform.rotateY((float) Math.PI);
        add(display, transform.scale(value, value, gui ? .1f : value));
    }

    private <T extends Display> T spawn(final Class<T> type) {
        return anchor.getWorld().spawn(anchor, type, display -> {
            display.setVisibleByDefault(false);
            display.addScoreboardTag("mainmenu:sidecard");
            display.setPersistent(false);
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setSilent(true);
            display.setBillboard(Display.Billboard.FIXED);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setInterpolationDuration(0);
            display.setTeleportDuration(0);
            if (display instanceof TextDisplay text) {
                text.setDefaultBackground(false);
                text.setShadowed(false);
                text.setAlignment(TextDisplay.TextAlignment.CENTER);
            }
        });
    }

    private void add(final Display entity, final Matrix4f local) {
        visuals.add(new Visual(entity, local));
        entity.setTransformationMatrix(new Matrix4f(base).mul(local));
    }

    private Matrix4f basis(final ArcMenuSurface surface) {
        final var right = surface.right(); final var up = surface.up(); final var normal = surface.normal();
        final var origin = surface.origin();
        final double ppb = surface.getCanvas().getPixelsPerBlock();
        return new Matrix4f((float) (right.getX() / ppb), (float) (right.getY() / ppb), (float) (right.getZ() / ppb), 0,
                (float) (up.getX() / ppb), (float) (up.getY() / ppb), (float) (up.getZ() / ppb), 0,
                (float) (normal.getX() / ppb), (float) (normal.getY() / ppb), (float) (normal.getZ() / ppb), 0,
                (float) (origin.getX() - anchor.getX()), (float) (origin.getY() - anchor.getY()), (float) (origin.getZ() - anchor.getZ()), 1);
    }

    private static Matrix4f offset(final double x, final double y, final int z) {
        final double s = 1 + ZK * z;
        return new Matrix4f().translate((float) (G * (x - 400) / s), (float) (G * (225 - y) / s), z);
    }

    private static double ink(final Component content, final double size) {
        final String text = PlainTextComponentSerializer.plainText().serialize(content);
        double width = 0;
        for (final int c : text.codePoints().toArray()) {
            width += c > 0x2E80 ? 9 : "i!.,:;|'".indexOf(c) >= 0 ? 2 : c == 'l' ? 3
                    : "It ".indexOf(c) >= 0 ? 4 : "fk<>".indexOf(c) >= 0 ? 5 : 6;
        }
        final int last = text.isEmpty() ? 0 : text.codePointBefore(text.length());
        return Math.max(0, width - (last > 0x2E80 ? 2.5 : 1)) * .1106 * size;
    }

    private static Component clip(final Component content, final double max, final double size) {
        if (ink(content, size) <= max) return content;
        final String text = PlainTextComponentSerializer.plainText().serialize(content).replace('\n', ' ');
        final StringBuilder shortText = new StringBuilder();
        for (final int c : text.codePoints().toArray()) {
            final Component candidate = Component.text(shortText.toString() + new String(Character.toChars(c)) + "…");
            if (ink(candidate, size) > max) break;
            shortText.appendCodePoint(c);
        }
        return Component.text(shortText.append('…').toString());
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        hits.clear();
        final Plugin arcMenu = Bukkit.getPluginManager().getPlugin("ArcMenu");
        final Plugin cleanupOwner = plugin.isEnabled() ? plugin : arcMenu;
        for (final var visual : visuals) {
            final Display display = visual.entity();
            if (!display.isValid()) continue;
            if (Bukkit.isOwnedByCurrentRegion(display)) display.remove();
            else if (cleanupOwner != null && cleanupOwner.isEnabled()) {
                // ArcMenu owns the parent session and can finish cleanup while MainMenu disables.
                display.getScheduler().execute(cleanupOwner, display::remove, null, 1);
            }
        }
        visuals.clear(); texts.clear(); meters.clear(); borders.clear();
    }
}
