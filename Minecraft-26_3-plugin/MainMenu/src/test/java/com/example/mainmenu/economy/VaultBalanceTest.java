package com.example.mainmenu.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VaultBalanceTest {
    @Test
    void largeBalancesStayReadableWithoutLosingTheFullAmount() {
        assertEquals("0.00", VaultBalance.format(0));
        assertEquals("1,234.56", VaultBalance.format(1234.56));
        assertEquals("9,999.99", VaultBalance.compact(9999.99));
        assertEquals("1 万", VaultBalance.compact(10000));
        assertEquals("1.23 亿", VaultBalance.compact(123456789.12));
        assertEquals("123,456,789.12", VaultBalance.format(123456789.12));
    }
}
