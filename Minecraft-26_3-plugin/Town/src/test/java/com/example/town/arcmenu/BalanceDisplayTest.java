package com.example.town.arcmenu;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BalanceDisplayTest {
    @Test
    void normalBalanceKeepsItsFraction() {
        assertEquals("8,984.75", BalanceDisplay.amount(new BigDecimal("8984.75")));
        assertEquals("0", BalanceDisplay.amount(BigDecimal.ZERO));
    }

    @Test
    void fullValueNeverLosesCents() {
        assertEquals("92,233,720,368,547,758.07",
                BalanceDisplay.full(new BigDecimal("92233720368547758.07")));
    }

    @Test
    void extremeAmountsStayWithinTheHeaderAndMarkApproximation() {
        for (final String value : new String[]{"123456789012345.67", "92233720368547758.07",
                "1.7976931348623157E+308", "0.0000000000000000000000123456789"}) {
            final String display = BalanceDisplay.amount(new BigDecimal(value));
            assertTrue(MenuBuilder.inkWidth(display, 15) <= 170, display);
            assertTrue(display.startsWith("≈"), display);
        }
    }

    @Test
    void currencyClipsByGlyphWidthAndPreservesFullNameSeparately() {
        final String longName = "翡翠小镇建设专用纪念金币";
        final String display = BalanceDisplay.currency(longName);
        assertTrue(display.endsWith("…"));
        assertTrue(MenuBuilder.inkWidth(display, 10) <= 72);
        assertEquals(longName, BalanceDisplay.plain(longName));
        assertEquals("金币", BalanceDisplay.currency("金币"));
    }

    @Test
    void providerLabelsCannotIntroduceStyleOrNewlines() {
        assertEquals("金币 TEST", BalanceDisplay.plain("<gold>金币</gold>\n&#FFFFFFTEST"));
    }
}
