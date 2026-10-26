package com.example.town.economy;

import static org.junit.jupiter.api.Assertions.*;
import com.example.town.hook.EconomyHook;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class EconomyBindingTest {
    private EconomyHook provider(String id) {
        return (EconomyHook) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{EconomyHook.class},
                (proxy, method, args) -> method.getName().equals("id") ? id : InvocationHandler.invokeDefault(proxy, method, args));
    }
    private Payment payment() {
        return Payment.create("donate", UUID.randomUUID(), "Player", "TEST", 100, null, "", 0, Map.of());
    }
    @Test void legacyWalletPaymentCanOnlyRecoverAgainstWallet() {
        Payment p = payment();
        assertDoesNotThrow(() -> provider("wallet").checkBinding(p));
        assertThrows(IllegalStateException.class, () -> provider("vault:provider").checkBinding(p));
    }
    @Test void vaultPaymentCannotRecoverAgainstWalletOrAnotherVaultProvider() {
        Payment p = payment().withEconomy("vault:provider");
        assertDoesNotThrow(() -> provider("vault:provider").checkBinding(p));
        assertThrows(IllegalStateException.class, () -> provider("wallet").checkBinding(p));
        assertThrows(IllegalStateException.class, () -> provider("vault:other").checkBinding(p));
    }
    @Test void walletCannotAcceptOldVaultListingsOrTreasuryCurrency() {
        assertTrue(provider("wallet").acceptsCurrency("TEST"));
        assertFalse(provider("wallet").acceptsCurrency("vault:provider"));
        assertFalse(provider("wallet").acceptsCurrency(""));
    }
}
