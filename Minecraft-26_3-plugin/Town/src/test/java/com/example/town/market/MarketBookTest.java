package com.example.town.market;

import static org.junit.jupiter.api.Assertions.*;
import com.example.town.economy.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarketBookTest {
    @TempDir Path directory;
    UUID owner=UUID.randomUUID(),buyer=UUID.randomUUID();
    MarketBook.Listing listing(MarketBook.Kind kind,int count) {return new MarketBook.Listing(UUID.randomUUID().toString(),"town","plot",owner,"Owner",kind,"serialized-item",count,150,"coin",0,"world",1,64,1,MarketBook.Status.OPEN);}
    MarketBook.Trade trade(MarketBook.Listing l,String action,int quantity) {
        Payment p=Payment.create("market",buyer,"Buyer","coin",quantity*150L,owner,"Owner",quantity*140L,Map.of("town","town"));
        return new MarketBook.Trade(p.id(),action,l.id(),quantity,buyer,l.item(),p,MarketBook.TradeStage.RESERVED);
    }
    @Test void crashAfterStockReservationPreservesQuantityAndOriginalPayment() throws Exception {
        Path file=directory.resolve("market");MarketBook book=new MarketBook(file);var l=listing(MarketBook.Kind.STOCK,10);var trade=trade(l,"buy",4);
        book.update(e->{e.listings.put(l.id(),l);e.reserve(l.id(),4,0,MarketBook.Kind.STOCK);e.trades.put(trade.id(),trade);return null;});
        MarketBook restored=new MarketBook(file);assertEquals(6,restored.state().listings().get(l.id()).remaining());assertEquals(trade.payment(),restored.state().trades().get(trade.id()).payment());
        restored.update(e->{e.deliver(e.trades.get(trade.id()));return null;});
        restored=new MarketBook(file);restored.update(e->{e.deliver(e.trades.get(trade.id()));return null;});
        assertEquals(1,restored.state().mail().size());assertEquals(4,restored.state().mail().get(trade.id()).quantity());assertEquals(6,restored.state().listings().get(l.id()).remaining());
    }
    @Test void declinedPurchaseReleasesStockExactlyOnce() throws Exception {
        MarketBook b=new MarketBook(directory.resolve("market"));var l=listing(MarketBook.Kind.STOCK,8);var t=trade(l,"buy",3);
        b.update(e->{e.listings.put(l.id(),l);e.reserve(l.id(),3,0,MarketBook.Kind.STOCK);e.trades.put(t.id(),t);return null;});
        b.update(e->{e.release(e.trades.get(t.id()));return null;});b.update(e->{e.release(e.trades.get(t.id()));return null;});
        assertEquals(8,b.state().listings().get(l.id()).remaining());assertTrue(b.state().mail().isEmpty());
    }
    @Test void twoConcurrentBuyersCannotSellTheSameStockTwice() throws Exception {
        MarketBook b=new MarketBook(directory.resolve("market"));var l=listing(MarketBook.Kind.STOCK,5);b.update(e->{e.listings.put(l.id(),l);return null;});
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> request=()->{try {b.update(e->{e.reserve(l.id(),4,0,MarketBook.Kind.STOCK);return null;});return true;}catch(IllegalArgumentException rejected){return false;}};
            var first=pool.submit(request);var second=pool.submit(request);assertNotEquals(first.get(),second.get());
        }
        assertEquals(1,b.state().listings().get(l.id()).remaining());
    }
    @Test void failedWriteKeepsAllLiveStateAtPreviousVersion() throws Exception {
        Path file=directory.resolve("market");MarketBook b=new MarketBook(file);var l=listing(MarketBook.Kind.STOCK,10);Files.createDirectory(file);
        assertThrows(java.io.IOException.class,()->b.update(e->{e.listings.put(l.id(),l);return null;}));assertTrue(b.state().listings().isEmpty());
    }
    @Test void inventoryIntentSurvivesRestartAndExcludesSecondDeliveryForSamePlayer() throws Exception {
        Path file=directory.resolve("market");MarketBook b=new MarketBook(file);String id=UUID.randomUUID().toString();List<String> before=new ArrayList<>(Collections.nCopies(36,"")),after=new ArrayList<>(before);after.set(0,"item-after");
        var intent=new MarketBook.Intent(id,buyer,MarketBook.IntentKind.COLLECT,"mail",1,before,after,false);b.update(e->{e.intent(intent);return null;});
        MarketBook restored=new MarketBook(file);assertEquals(intent,restored.pending(buyer));
        assertThrows(IllegalArgumentException.class,()->restored.update(e->{e.intent(new MarketBook.Intent(UUID.randomUUID().toString(),buyer,MarketBook.IntentKind.COLLECT,"mail2",1,before,after,false));return null;}));
    }
    @Test void malformedJournalCannotDiscardEscrow() throws Exception {
        Path file=directory.resolve("market");Files.writeString(file,"{\"listings\":null}");assertThrows(java.io.IOException.class,()->new MarketBook(file));
    }
    @Test void prepaymentOnlyOpensOrderAfterLocalPaymentApply() throws Exception {
        MarketBook b=new MarketBook(directory.resolve("market"));var l=listing(MarketBook.Kind.ORDER,10).at(MarketBook.Status.DRAFT);var t=trade(l,"order",10);
        b.update(e->{e.listings.put(l.id(),l);e.trades.put(t.id(),t);return null;});assertEquals(MarketBook.Status.DRAFT,b.state().listings().get(l.id()).status());
        b.update(e->{e.deliver(e.trades.get(t.id()));return null;});assertEquals(MarketBook.Status.OPEN,b.state().listings().get(l.id()).status());assertTrue(b.state().mail().isEmpty());
    }
    @Test void walletJournalSerializesAllServicesForSamePayer() throws Exception {
        PaymentJournal journal=new PaymentJournal(directory.resolve("payments"));
        Payment a=Payment.create("donate",buyer,"Buyer","coin",100,null,"",0,Map.of()),b=Payment.create("move",buyer,"Buyer","coin",100,null,"",0,Map.of());
        journal.insert(a);assertThrows(IllegalArgumentException.class,()->journal.insert(b));assertEquals(1,journal.all().size());
        journal.transition(a.id(),Payment.Stage.REQUESTED,Payment.Stage.DECLINED);journal.insert(b);assertEquals(2,journal.all().size());
    }
}
