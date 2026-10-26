package com.example.town.economy;

import static org.junit.jupiter.api.Assertions.*;
import com.example.town.store.AtomicFiles;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DurablePaymentsTest {
    @TempDir Path directory;
    private final UUID buyer=UUID.randomUUID(),seller=UUID.randomUUID();
    private Payment payment(String kind,long cents,long credit) {
        return Payment.create(kind,buyer,"Buyer","coin",cents,credit>0?seller:null,"Seller",credit,Map.of("town","example"));
    }
    private DurablePayments engine(PaymentJournal journal,FakeWallet wallet) {
        return new DurablePayments(journal,wallet,Runnable::run,error->{});
    }
    @Test void uncertainDebitAndCrashAfterLocalCommitReplayWithoutDoubleChargeOrDoubleEffect() throws Exception {
        Path journalFile=directory.resolve("payments.bin"),effect=directory.resolve("effect");
        FakeWallet wallet=new FakeWallet();wallet.uncertainDebit=true;
        Payment p=payment("donate",1000,0);
        DurablePayments first=engine(new PaymentJournal(journalFile),wallet);
        first.handler("donate",ignored->{fail("不确定扣款不能提前落账");return DurablePayments.Applied.YES;});
        first.submit(p,null);
        assertEquals(Payment.Stage.REQUESTED,first.get(p.id()).stage());
        assertEquals(9000,wallet.balance(buyer));
        AtomicInteger writes=new AtomicInteger();
        DurablePayments second=engine(new PaymentJournal(journalFile),wallet);
        second.handler("donate",operation->{
            if(!Files.exists(effect)) {
                AtomicFiles.write(effect,operation.id().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                writes.incrementAndGet();
                throw new IOException("模拟本地提交后、支付状态保存前崩溃");
            }
            return DurablePayments.Applied.YES;
        });
        second.recover();
        assertEquals(Payment.Stage.PAID,second.get(p.id()).stage());
        DurablePayments third=engine(new PaymentJournal(journalFile),wallet);
        third.handler("donate",operation->{
            assertEquals(operation.id(),Files.readString(effect));return DurablePayments.Applied.YES;
        });
        third.recover();third.recover();
        assertEquals(Payment.Stage.DONE,third.get(p.id()).stage());
        assertEquals(1,writes.get());assertEquals(1,wallet.debits.size());assertEquals(9000,wallet.balance(buyer));
    }
    @Test void rejectedApplyRefundsWithSameCreditKeyAfterUncertainResponse() throws Exception {
        FakeWallet wallet=new FakeWallet();wallet.uncertainCredit=true;
        Payment p=payment("rent",700,700);
        DurablePayments first=engine(new PaymentJournal(directory.resolve("payments.bin")),wallet);
        first.handler("rent",ignored->DurablePayments.Applied.REJECT);first.submit(p,null);
        assertEquals(Payment.Stage.REFUND,first.get(p.id()).stage());
        assertEquals(10_000,wallet.balance(buyer));assertEquals(10_000,wallet.balance(seller));
        DurablePayments restored=engine(new PaymentJournal(directory.resolve("payments.bin")),wallet);
        restored.recover();
        assertEquals(Payment.Stage.REFUNDED,restored.get(p.id()).stage());
        assertEquals(Set.of(p.refundKey()),wallet.credits);assertEquals(10_000,wallet.balance(buyer));
    }
    @Test void offlineRecipientOnlyPaidAfterDurableLocalApply() throws Exception {
        FakeWallet wallet=new FakeWallet();AtomicInteger applied=new AtomicInteger();
        Payment p=payment("rent",900,900);
        wallet.beforeCredit=()->assertEquals(1,applied.get());
        DurablePayments engine=engine(new PaymentJournal(directory.resolve("payments.bin")),wallet);
        engine.handler("rent",ignored->{applied.incrementAndGet();return DurablePayments.Applied.YES;});
        engine.submit(p,null);engine.recover();
        assertEquals(Payment.Stage.DONE,engine.get(p.id()).stage());
        assertEquals(9100,wallet.balance(buyer));assertEquals(10900,wallet.balance(seller));assertEquals(1,applied.get());
    }
    @Test void insufficientFundsDoesNotApplyOrRetryLater() throws Exception {
        FakeWallet wallet=new FakeWallet();Payment p=payment("donate",20_000,0);
        DurablePayments engine=engine(new PaymentJournal(directory.resolve("payments.bin")),wallet);
        engine.handler("donate",ignored->{fail("余额不足不能落账");return DurablePayments.Applied.YES;});
        engine.submit(p,null);engine.recover();
        assertEquals(Payment.Stage.DECLINED,engine.get(p.id()).stage());assertTrue(wallet.debits.isEmpty());
    }
    @Test void cannotCallWalletWithoutWritableJournalAndCannotSkipCorruptJournal() throws Exception {
        Path file=directory.resolve("journal");
        FakeWallet wallet=new FakeWallet();PaymentJournal journal=new PaymentJournal(file);Files.createDirectory(file);
        DurablePayments engine=engine(journal,wallet);
        assertThrows(IOException.class,()->engine.submit(payment("donate",1,0),null));
        assertTrue(wallet.debits.isEmpty());assertTrue(journal.all().isEmpty());
        Files.write(directory.resolve("corrupt"),new byte[]{1,2,3});
        assertThrows(IOException.class,()->new PaymentJournal(directory.resolve("corrupt")));
    }
    @Test void serviceUndoRefundsOnceAndTransfersCannotUseUndoPath() throws Exception {
        FakeWallet wallet=new FakeWallet();DurablePayments engine=engine(new PaymentJournal(directory.resolve("p")),wallet);
        engine.handler("move",ignored->DurablePayments.Applied.YES);
        Payment p=payment("move",100,0);engine.submit(p,null);
        assertEquals(9900,wallet.balance(buyer));engine.refundAfterRollback(p.id());engine.refundAfterRollback(p.id());
        assertEquals(10_000,wallet.balance(buyer));assertEquals(Payment.Stage.REFUNDED,engine.get(p.id()).stage());
        Payment transfer=payment("move",100,100);engine.submit(transfer,null);
        assertThrows(IllegalStateException.class,()->engine.refundAfterRollback(transfer.id()));
    }
    private static final class Rejected extends RuntimeException { }
    private static final class FakeWallet implements DurablePayments.Wallet {
        final Map<UUID,Long> balances=new HashMap<>();final Set<String> debits=new HashSet<>(),credits=new HashSet<>();
        boolean uncertainDebit,uncertainCredit;Runnable beforeCredit=()->{};
        long balance(UUID id) {return balances.getOrDefault(id,10_000L);}
        public CompletableFuture<Void> debit(Payment p) {
            if(debits.contains(p.debitKey())) return CompletableFuture.completedFuture(null);
            if(balance(p.payer())<p.cents()) return CompletableFuture.failedFuture(new Rejected());
            balances.put(p.payer(),balance(p.payer())-p.cents());debits.add(p.debitKey());
            if(uncertainDebit) {uncertainDebit=false;return CompletableFuture.failedFuture(new IOException("reply lost"));}
            return CompletableFuture.completedFuture(null);
        }
        public CompletableFuture<Void> credit(Payment p,boolean refund) {
            beforeCredit.run();String key=refund?p.refundKey():p.creditKey();UUID who=refund?p.payer():p.beneficiary();
            if(credits.add(key)) balances.put(who,balance(who)+(refund?p.cents():p.creditCents()));
            if(uncertainCredit) {uncertainCredit=false;return CompletableFuture.failedFuture(new IOException("reply lost"));}
            return CompletableFuture.completedFuture(null);
        }
        public boolean rejected(Throwable error) {return error instanceof Rejected;}
    }
}
