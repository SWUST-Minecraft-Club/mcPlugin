package com.example.mainmenu.api;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** A viewer-specific sidecard. Providers run on the viewer's Folia region; never block on IO. */
public final class MenuSidecard {
    public enum Side { AUTO, LEFT, RIGHT }
    public enum IconContext { FIXED, GUI }

    /** progress < 0 means a text row; otherwise it is a fraction between zero and one. */
    public record Row(Component label, Component value, double progress) {
        public Row {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(value, "value");
            if (!Double.isFinite(progress)) throw new IllegalArgumentException("Non-finite progress");
            progress = progress < 0 ? -1 : Math.clamp(progress, 0, 1);
        }

        public static Row text(final String label, final String value) {
            return new Row(Component.text(label), Component.text(value), -1);
        }

        public static Row progress(final String label, final String value, final double fraction) {
            return new Row(Component.text(label), Component.text(value), Math.clamp(fraction, 0, 1));
        }
    }

    public record Button(String id, Component label) {
        public Button {
            if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,31}") || id.equals("card")) {
                throw new IllegalArgumentException("Invalid sidecard button id: " + id);
            }
            Objects.requireNonNull(label, "label");
        }

        public Button(final String id, final String label) { this(id, Component.text(label)); }
    }

    public record Content(Component title, Material icon, IconContext iconContext,
                          List<Row> rows, List<Button> buttons) {
        public Content {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(iconContext, "iconContext");
            if (icon == Material.AIR || icon == Material.CAVE_AIR || icon == Material.VOID_AIR) {
                throw new IllegalArgumentException("Sidecard icon cannot be AIR");
            }
            rows = List.copyOf(rows);
            buttons = List.copyOf(buttons);
            if (rows.size() > 8 || buttons.size() > 2) {
                throw new IllegalArgumentException("Sidecards support at most 8 rows and 2 buttons; use a detail menu");
            }
            if (buttons.stream().map(Button::id).distinct().count() != buttons.size()) {
                throw new IllegalArgumentException("Duplicate sidecard button id");
            }
        }

        public Content(final String title, final Material icon, final List<Row> rows) {
            this(Component.text(title), icon, IconContext.FIXED, rows, List.of());
        }
    }

    @FunctionalInterface
    public interface ClickHandler {
        /** action is "card" for the panel, or a button's id. */
        void onClick(Player viewer, String action, ClickType click);
    }

    private final String id;
    private final int order;
    private final Side side;
    private final double minHeight;
    private final Predicate<Player> visible;
    private final Function<Player, Content> content;
    private final ClickHandler handler;

    private MenuSidecard(final Builder builder) {
        id = builder.id;
        order = builder.order;
        side = builder.side;
        minHeight = builder.minHeight;
        visible = builder.visible;
        content = Objects.requireNonNull(builder.content, "contentForViewer is required");
        handler = builder.handler;
    }

    public String id() { return id; }
    public int order() { return order; }
    public Side side() { return side; }
    public double minHeight() { return minHeight; }
    public boolean visible(final Player viewer) { return visible.test(viewer); }
    /** Returning null hides the card for this viewer. */
    public Content content(final Player viewer) { return content.apply(viewer); }
    public boolean clickable() { return handler != null; }
    public void onClick(final Player viewer, final String action, final ClickType click) {
        if (handler != null) handler.onClick(viewer, action, click);
    }

    public static Builder builder(final String id) { return new Builder(id); }

    public static final class Builder {
        private final String id;
        private int order;
        private Side side = Side.AUTO;
        private double minHeight = 88;
        private Predicate<Player> visible = player -> true;
        private Function<Player, Content> content;
        private ClickHandler handler;

        private Builder(final String id) {
            if (id == null || !id.matches("[a-z0-9][a-z0-9_.-]*:[a-z0-9][a-z0-9_./-]*")) {
                throw new IllegalArgumentException("Use a namespaced sidecard id, e.g. farming:skills");
            }
            this.id = id;
        }

        public Builder order(final int value) { order = value; return this; }
        public Builder side(final Side value) { side = Objects.requireNonNull(value); return this; }
        public Builder minHeight(final double value) {
            if (!Double.isFinite(value) || value < 48 || value > 392) {
                throw new IllegalArgumentException("minHeight must be between 48 and 392 design units");
            }
            minHeight = value; return this;
        }
        public Builder visibleWhen(final Predicate<Player> value) { visible = Objects.requireNonNull(value); return this; }
        public Builder contentForViewer(final Function<Player, Content> value) { content = value; return this; }
        public Builder onClick(final ClickHandler value) { handler = Objects.requireNonNull(value); return this; }
        public MenuSidecard build() { return new MenuSidecard(this); }
    }
}
