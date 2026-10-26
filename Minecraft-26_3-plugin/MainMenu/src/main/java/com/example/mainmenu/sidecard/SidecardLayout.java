package com.example.mainmenu.sidecard;

import com.example.mainmenu.api.MenuSidecard.Side;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Stable height-aware packing in the phone's 800 x 450 design coordinates. */
public final class SidecardLayout {
    public record Area(double leftX, double rightX, double width, double top, double height,
                       double gap, int maxPerColumn) {
        public Area {
            for (final double value : new double[] { leftX, rightX, width, top, height, gap }) {
                if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite sidecard layout");
            }
            if (leftX < 8 || rightX < 536 || width < 140 || leftX + width > 264
                    || rightX + width > 792 || top < 8 || height < 96 || top + height > 404
                    || gap < 4 || gap > 32 || maxPerColumn < 1 || maxPerColumn > 6) {
                throw new IllegalArgumentException("Sidecard columns must fit beside the phone and leave footer space");
            }
        }
        public static Area defaults() { return new Area(32, 552, 216, 35, 360, 12, 3); }
    }
    public record Card(String id, int order, Side side, double height) {
        public Card {
            if (id == null || side == null || !Double.isFinite(height) || height <= 0) {
                throw new IllegalArgumentException("Invalid layout card");
            }
        }
    }
    public record Placement(String id, Side side, double x, double y, double width, double height) {
        public boolean contains(final double px, final double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
    public record Page(List<Placement> cards) { public Page { cards = List.copyOf(cards); } }
    private record Pending(Card card, Side side, double y) { }

    private SidecardLayout() { }

    public static List<Page> pack(final List<Card> input, final Area area) {
        final List<Card> sorted = input.stream().sorted(Comparator.comparingInt(Card::order).thenComparing(Card::id)).toList();
        if (sorted.stream().map(Card::id).distinct().count() != sorted.size()) {
            throw new IllegalArgumentException("Duplicate sidecard layout id");
        }
        final List<Page> pages = new ArrayList<>();
        final List<Pending> pending = new ArrayList<>();
        final double[] used = {0, 0};
        final int[] count = {0, 0};
        for (final Card card : sorted) {
            if (card.height() > area.height()) throw new IllegalArgumentException("Card taller than column: " + card.id());
            int side = choose(card, area, used, count);
            if (side < 0) {
                pages.add(finish(pending, used, area));
                pending.clear();
                used[0] = used[1] = 0;
                count[0] = count[1] = 0;
                side = choose(card, area, used, count);
            }
            final double y = used[side] + (count[side] == 0 ? 0 : area.gap());
            pending.add(new Pending(card, side == 0 ? Side.LEFT : Side.RIGHT, y));
            used[side] = y + card.height();
            count[side]++;
        }
        if (!pending.isEmpty()) pages.add(finish(pending, used, area));
        return List.copyOf(pages);
    }

    private static int choose(final Card card, final Area area, final double[] used, final int[] count) {
        int best = -1;
        for (int index = 0; index < 2; index++) {
            if (card.side() != Side.AUTO && card.side() != (index == 0 ? Side.LEFT : Side.RIGHT)) continue;
            if (count[index] >= area.maxPerColumn()
                    || used[index] + (count[index] == 0 ? 0 : area.gap()) + card.height() > area.height()) continue;
            if (best < 0 || used[index] < used[best]) best = index;
        }
        return best;
    }

    private static Page finish(final List<Pending> pending, final double[] used, final Area area) {
        return new Page(pending.stream().map(p -> new Placement(p.card().id(), p.side(),
                p.side() == Side.LEFT ? area.leftX() : area.rightX(),
                area.top() + (area.height() - used[p.side() == Side.LEFT ? 0 : 1]) / 2 + p.y(),
                area.width(), p.card().height())).toList());
    }
}
