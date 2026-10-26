package com.example.mainmenu.sidecard;

import static org.junit.jupiter.api.Assertions.*;

import com.example.mainmenu.api.MenuSidecard.Side;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SidecardLayoutTest {
    private static final SidecardLayout.Area AREA = SidecardLayout.Area.defaults();
    private static SidecardLayout.Card card(final int order, final double height, final Side side) {
        return new SidecardLayout.Card("card:" + order, order, side, height);
    }

    @Test void sixMixedHeightsFitAndSeventhMovesToAnotherPage() {
        final var cards = new ArrayList<>(List.of(card(0, 88, Side.AUTO), card(1, 156, Side.AUTO), card(2, 112, Side.AUTO),
                card(3, 88, Side.AUTO), card(4, 88, Side.AUTO), card(5, 88, Side.AUTO)));
        final var pages = SidecardLayout.pack(cards, AREA);
        assertEquals(1, pages.size());
        assertEquals(3, pages.getFirst().cards().stream().filter(c -> c.side() == Side.LEFT).count());
        cards.add(card(6, 88, Side.AUTO));
        assertEquals(2, SidecardLayout.pack(cards, AREA).size());
    }

    @Test void heightCapacityAndPinnedSidesDeterminePagination() {
        final var cards = List.of(card(0, 200, Side.LEFT), card(1, 180, Side.LEFT), card(2, 160, Side.RIGHT));
        final var pages = SidecardLayout.pack(cards, AREA);
        assertEquals(2, pages.size());
        assertEquals(1, pages.getFirst().cards().size());
        assertEquals(Side.LEFT, pages.get(1).cards().getFirst().side());
        assertEquals(Side.RIGHT, pages.get(1).cards().getLast().side());
    }

    @Test void emptyAndInvalidInputsAreExplicit() {
        assertTrue(SidecardLayout.pack(List.of(), AREA).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> SidecardLayout.pack(List.of(card(0, 361, Side.AUTO)), AREA));
        assertThrows(IllegalArgumentException.class, () -> card(0, Double.NaN, Side.AUTO));
        assertThrows(IllegalArgumentException.class, () -> SidecardLayout.pack(List.of(card(0, 88, Side.AUTO), card(0, 88, Side.RIGHT)), AREA));
        assertThrows(IllegalArgumentException.class, () -> new SidecardLayout.Area(40, 552, 240, 35, 360, 12, 3));
    }

    @Test void arbitraryCountsRemainDeterministicCompleteAndNonOverlapping() {
        final var random = new Random(20261026);
        final var cards = new ArrayList<SidecardLayout.Card>();
        for (int index = 0; index < 257; index++) cards.add(card(index, 48 + random.nextInt(313), Side.values()[random.nextInt(3)]));
        for (final int size : new int[] {0, 1, 2, 6, 7, 16, 257}) {
            final var subset = new ArrayList<>(cards.subList(0, size));
            final var pages = SidecardLayout.pack(subset, AREA);
            Collections.shuffle(subset, random);
            assertEquals(pages, SidecardLayout.pack(subset, AREA));
            final var seen = new HashSet<String>();
            for (final var page : pages) for (final Side side : List.of(Side.LEFT, Side.RIGHT)) {
                final var column = page.cards().stream().filter(c -> c.side() == side).toList();
                assertTrue(column.size() <= AREA.maxPerColumn());
                double end = AREA.top() - AREA.gap();
                for (final var p : column) {
                    assertTrue(seen.add(p.id()));
                    assertTrue(p.y() >= end + AREA.gap() - .00001);
                    assertTrue(p.y() + p.height() <= AREA.top() + AREA.height() + .00001);
                    assertTrue(p.x() + p.width() <= (side == Side.LEFT ? 264 : 792));
                    end = p.y() + p.height();
                }
                if (!column.isEmpty()) assertEquals(AREA.top() + AREA.height() / 2,
                        (column.getFirst().y() + end) / 2, .00001);
            }
            assertEquals(size, seen.size());
        }
    }
}
