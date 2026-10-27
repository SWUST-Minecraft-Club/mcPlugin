package com.example.yudreamui.plugin;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ShopOrdersTest {
    @TempDir Path directory;
    private ShopOrders.Order order() {return new ShopOrders.Order(UUID.randomUUID().toString(),UUID.randomUUID(),"Tester","bread","BREAD",8,"coin",new BigDecimal("12.00"),"wallet-fingerprint",ShopOrders.Status.PAYMENT_PENDING);}
    @Test void paidSurvivesRestartAndAmbiguousDeliveryNeedsReview() throws Exception {
        var original=order();
        try(var store=new ShopOrders(directory)) {
            var created=store.create(original).join();var paid=store.transition(created,ShopOrders.Status.PAYMENT_PENDING,ShopOrders.Status.PAID).join();
            assertEquals(ShopOrders.Status.PAID,paid.status());
        }
        try(var store=new ShopOrders(directory)) {
            var paid=store.pending(original.player()).getFirst();assertEquals(original.businessNo(),paid.businessNo());
            store.transition(paid,ShopOrders.Status.PAID,ShopOrders.Status.DELIVERING).join();
        }
        try(var store=new ShopOrders(directory)) {assertEquals(ShopOrders.Status.REVIEW,store.pending(original.player()).getFirst().status());}
    }
    @Test void duplicateDeliveryTransitionIsRejected() throws Exception {
        try(var store=new ShopOrders(directory)) {
            var created=store.create(order()).join();var paid=store.transition(created,ShopOrders.Status.PAYMENT_PENDING,ShopOrders.Status.PAID).join();
            store.transition(paid,ShopOrders.Status.PAID,ShopOrders.Status.DELIVERING).join();
            assertThrows(Exception.class,()->store.transition(paid,ShopOrders.Status.PAID,ShopOrders.Status.DELIVERING).join());
        }
    }
    @Test void corruptLedgerDoesNotResetOrders() throws Exception {
        Files.writeString(directory.resolve(UUID.randomUUID()+".json"),"{broken");assertThrows(java.io.IOException.class,()->new ShopOrders(directory));
    }
    @Test void conditionsFailClosedForUnresolvedState() {
        assertTrue(LegacyPresentation.test("{meta:count} >= 2 && true == true",s->s.replace("{meta:count}","3")));
        assertFalse(LegacyPresentation.test("{meta:count} >= 2",s->s));
        assertThrows(IllegalArgumentException.class,()->LegacyPresentation.supportedCondition("1 == 1 || true"));
    }
}
