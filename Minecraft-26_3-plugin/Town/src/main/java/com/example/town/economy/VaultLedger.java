package com.example.town.economy;

import com.example.town.store.AtomicFiles;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Vault 没有业务号：先记录调用意图，成功可重放；结果未知时停住，交由管理员核对。 */
public final class VaultLedger {
    public enum State { PENDING, SUCCEEDED, REJECTED }
    public enum Resolution { SUCCESS, FAILED, RETRY }
    public static final class Rejected extends RuntimeException {
        public Rejected(String message) { super(message); }
    }
    public static final class Uncertain extends RuntimeException {
        public Uncertain(String key) { super("Vault 交易结果待核对：" + key); }
    }
    public static final class NotCalled extends RuntimeException {
        public NotCalled(String message) { super(message); }
    }
    public record Entry(String key, String provider, UUID account, long cents, boolean credit, State state) {
        public Entry {
            if (key == null || key.isBlank() || provider == null || provider.isBlank()
                    || account == null || cents < 0 || state == null) throw new IllegalArgumentException("Vault 记录无效");
        }
        Entry at(State next) { return new Entry(key, provider, account, cents, credit, next); }
    }

    private final Path file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Set<String> running = new HashSet<>();

    public VaultLedger(Path file) throws IOException {
        this.file = file;
        if (!Files.exists(file)) return;
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            if (in.readInt() != 0x54564C31) throw new IOException("Vault 台账格式不正确");
            int count = in.readInt();
            if (count < 0 || count > 1_000_000) throw new IOException("Vault 台账长度无效");
            for (int i = 0; i < count; i++) {
                Entry e = new Entry(in.readUTF(), in.readUTF(), UUID.fromString(in.readUTF()),
                        in.readLong(), in.readBoolean(), State.valueOf(in.readUTF()));
                if (entries.putIfAbsent(e.key(), e) != null) throw new IOException("重复 Vault 交易");
            }
            if (in.read() != -1) throw new IOException("Vault 台账存在多余数据");
        } catch (RuntimeException error) { throw new IOException("Vault 台账损坏", error); }
    }

    public synchronized List<Entry> pending() {
        return entries.values().stream().filter(e -> e.state() != State.SUCCEEDED).toList();
    }

    public CompletableFuture<Void> execute(Entry request, Supplier<CompletableFuture<Void>> action, Executor io) {
        synchronized (this) {
            Entry previous = entries.get(request.key());
            if (previous != null) {
                if (!previous.at(State.PENDING).equals(request.at(State.PENDING))) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Vault 业务号对应的账户、来源或金额已改变"));
                }
                if (previous.state() == State.SUCCEEDED) return CompletableFuture.completedFuture(null);
                return CompletableFuture.failedFuture(previous.state() == State.REJECTED
                        ? new Rejected("Vault 已拒绝此业务号") : new Uncertain(request.key()));
            }
            try { save(request.key(), request.at(State.PENDING)); }
            catch (IOException error) { return CompletableFuture.failedFuture(error); }
            running.add(request.key());
        }
        CompletableFuture<Void> work;
        try { work = Objects.requireNonNull(action.get()); }
        catch (Throwable error) { work = CompletableFuture.failedFuture(error); }
        return work.handleAsync((ignored, error) -> {
            synchronized (this) {
                try {
                    Throwable cause = unwrap(error);
                    if (cause == null) save(request.key(), request.at(State.SUCCEEDED));
                    else if (cause instanceof Rejected) save(request.key(), request.at(State.REJECTED));
                    else if (cause instanceof NotCalled) save(request.key(), null);
                    if (cause != null) throw new CompletionException(cause);
                    return (Void) null;
                } catch (IOException failure) { throw new CompletionException(failure); }
                finally { running.remove(request.key()); }
            }
        }, io);
    }

    /** 只能在外部经济账本核对后调用；正在进行的调用与已成功记录不能手工重发。 */
    public synchronized void resolve(String key, Resolution resolution) throws IOException {
        Entry e = entries.get(key);
        if (e == null) throw new IllegalArgumentException("没有这条 Vault 记录");
        if (running.contains(key)) throw new IllegalArgumentException("调用仍在执行，请等待结束后核对");
        if (e.state() == State.SUCCEEDED) throw new IllegalArgumentException("已成功的 Vault 交易不能再次发送");
        save(key, switch (resolution) {
            case SUCCESS -> e.at(State.SUCCEEDED);
            case FAILED -> e.at(State.REJECTED);
            case RETRY -> null;
        });
    }

    private void save(String key, Entry entry) throws IOException {
        Map<String, Entry> snapshot = new LinkedHashMap<>(entries);
        if (entry == null) snapshot.remove(key); else snapshot.put(key, entry);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(0x54564C31); out.writeInt(snapshot.size());
            for (Entry e : snapshot.values()) {
                out.writeUTF(e.key()); out.writeUTF(e.provider()); out.writeUTF(e.account().toString());
                out.writeLong(e.cents()); out.writeBoolean(e.credit()); out.writeUTF(e.state().name());
            }
        }
        AtomicFiles.write(file, bytes.toByteArray());
        entries.clear(); entries.putAll(snapshot);
    }

    private static Throwable unwrap(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) error = error.getCause();
        return error;
    }
}
