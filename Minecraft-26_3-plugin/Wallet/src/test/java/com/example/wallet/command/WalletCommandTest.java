package com.example.wallet.command;

import static org.junit.jupiter.api.Assertions.*;
import com.example.wallet.WalletConfig;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.lang.reflect.Proxy;
import java.util.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class WalletCommandTest {
    CommandSourceStack source(boolean admin) {
        var sender = (CommandSender) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{CommandSender.class},
                (p, m, a) -> m.getName().equals("hasPermission") ? admin : null);
        return (CommandSourceStack) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{CommandSourceStack.class},
                (p, m, a) -> m.getName().equals("getSender") ? sender : null);
    }
    @Test void rootSuggestionsRespectPermissionsAndTypedPrefix() {
        var command = new WalletCommand(null, null, WalletConfig.load(new YamlConfiguration()), null, null);
        assertEquals(Set.of("help", "balance", "history"), new HashSet<>(command.suggest(source(false), new String[]{""})));
        assertEquals(List.of("set"), command.suggest(source(true), new String[]{"s"}));
        assertFalse(command.suggest(source(false), new String[]{""}).contains("set"));
        assertTrue(command.suggest(source(true), new String[]{""}).containsAll(List.of("add", "set", "take", "assets", "players")));
    }
    @Test void setAcceptsZeroButAddRejectsZeroAndBothRejectNegativeOrScientificNotation() {
        assertNotNull(WalletAdminCommand.parseAmount("0", true));
        assertNull(WalletAdminCommand.parseAmount("0", false));
        for (String value : List.of("-1", "1e3", "NaN", "")) {
            assertNull(WalletAdminCommand.parseAmount(value, true));
            assertNull(WalletAdminCommand.parseAmount(value, false));
        }
    }
}
