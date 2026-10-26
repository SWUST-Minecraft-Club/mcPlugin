package com.example.town.economy;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** 钱包幂等请求与本地幂等业务之间的恢复协调器。无世界/玩家访问，可在 IO 线程执行。 */
public final class DurablePayments {
    public interface Wallet {
        CompletableFuture<Void> debit(Payment payment);
        CompletableFuture<Void> credit(Payment payment, boolean refund);
        /** 只有明确的业务拒绝才能终止；超时与服务端错误必须保留原业务号。 */
        boolean rejected(Throwable error);
    }
    public enum Applied { YES, REJECT, WAIT }
    @FunctionalInterface public interface Handler { Applied apply(Payment payment) throws Exception; }
    private final PaymentJournal journal;
    private final Wallet wallet;
    private final Executor executor;
    private final Consumer<Throwable> errorSink;
    private final Map<String, Handler> handlers = new ConcurrentHashMap<>();
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    private final Map<String, Consumer<Payment>> callbacks = new ConcurrentHashMap<>();

    public DurablePayments(PaymentJournal journal, Wallet wallet, Executor executor, Consumer<Throwable> errorSink) {
        this.journal = journal; this.wallet = wallet; this.executor = executor; this.errorSink = errorSink;
    }
    public void handler(String kind, Handler handler) { handlers.put(kind, handler); }
    public void submit(Payment payment, Consumer<Payment> callback) throws IOException {
        journal.insert(payment);
        if (callback != null) callbacks.put(payment.id(), callback);
        kick(payment.id());
    }
    public Payment get(String id) { return journal.get(id); }
    public List<Payment> all() { return journal.all(); }
    public boolean busy(UUID payer) { return all().stream().anyMatch(p -> p.payer().equals(payer) && !p.terminal()); }
    public void recover() { for (Payment p : journal.all()) if (!p.terminal()) kick(p.id()); }
    /** 业务已经恢复原状后才允许退款。无收款人的系统服务支持验收撤回。 */
    public void refundAfterRollback(String id) throws IOException {
        Payment p = Objects.requireNonNull(journal.get(id));
        if (p.beneficiary() != null || p.creditCents() != 0) throw new IllegalStateException("转账不能从此路径撤回");
        if (p.stage() == Payment.Stage.REFUNDED || p.stage() == Payment.Stage.REFUND) return;
        if (p.stage() != Payment.Stage.DONE && p.stage() != Payment.Stage.APPLIED && p.stage() != Payment.Stage.PAID)
            throw new IllegalStateException("扣费尚未确认");
        journal.transition(id, p.stage(), Payment.Stage.REFUND);
        kick(id);
    }
    public void kick(String id) {
        if (!running.add(id)) return;
        executor.execute(() -> {
            CompletableFuture<Void> work;
            try { work = drive(id); } catch (Throwable error) { work = CompletableFuture.failedFuture(error); }
            work.whenComplete((ignored, error) -> {
                running.remove(id);
                if (error != null) errorSink.accept(unwrap(error));
                Payment payment = journal.get(id);
                if (payment != null && payment.terminal()) {
                    Consumer<Payment> callback = callbacks.remove(id);
                    if (callback != null) callback.accept(payment);
                }
            });
        });
    }
    private CompletableFuture<Void> drive(String id) throws Exception {
        Payment p = journal.get(id);
        if (p == null || p.terminal()) return CompletableFuture.completedFuture(null);
        if (p.stage() == Payment.Stage.REQUESTED) {
            CompletableFuture<Void> debit = p.cents() == 0 ? CompletableFuture.completedFuture(null) : wallet.debit(p);
            return debit.handleAsync((ignored, error) -> {
                try {
                    if (error != null) {
                        if (wallet.rejected(unwrap(error))) journal.transition(id, p.stage(), Payment.Stage.DECLINED);
                        else throw new CompletionException(unwrap(error));
                    } else journal.transition(id, p.stage(), Payment.Stage.PAID);
                    return null;
                } catch (IOException failure) { throw new CompletionException(failure); }
            }, executor).thenComposeAsync(ignored -> resume(id), executor);
        }
        if (p.stage() == Payment.Stage.PAID) {
            Handler handler = handlers.get(p.kind());
            if (handler == null) return CompletableFuture.completedFuture(null);
            Applied applied = handler.apply(p);
            if (applied == Applied.WAIT) return CompletableFuture.completedFuture(null);
            journal.transition(id, p.stage(), applied == Applied.YES ? Payment.Stage.APPLIED : Payment.Stage.REFUND);
            return resume(id);
        }
        boolean refund = p.stage() == Payment.Stage.REFUND;
        CompletableFuture<Void> credit = (refund ? p.cents() : p.creditCents()) == 0
                ? CompletableFuture.completedFuture(null) : wallet.credit(p, refund);
        return credit.thenRunAsync(() -> {
            try { journal.transition(id, p.stage(), refund ? Payment.Stage.REFUNDED : Payment.Stage.DONE); }
            catch (IOException error) { throw new CompletionException(error); }
        }, executor);
    }
    private CompletableFuture<Void> resume(String id) {
        try { return drive(id); } catch (Exception e) { return CompletableFuture.failedFuture(e); }
    }
    private static Throwable unwrap(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) error = error.getCause();
        return error;
    }
}
