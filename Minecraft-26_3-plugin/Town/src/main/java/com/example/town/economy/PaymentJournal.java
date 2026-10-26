package com.example.town.economy;

import com.example.town.store.AtomicFiles;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** WAL：先持久化请求，再调用钱包。损坏时停止经济服务，不能丢弃未结算记录。 */
public final class PaymentJournal {
    private final Path file;
    private final Map<String, Payment> entries = new LinkedHashMap<>();
    public PaymentJournal(Path file) throws IOException {
        this.file = file;
        if (!Files.exists(file)) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != 0x54504A31) throw new IOException("支付台账格式不正确");
            int count = bounded(in.readInt(), 1_000_000);
            for (int i = 0; i < count; i++) {
                String id = in.readUTF(), kind = in.readUTF();
                UUID payer = UUID.fromString(in.readUTF());
                String name = in.readUTF(), currency = in.readUTF();
                long cents = in.readLong();
                UUID beneficiary = in.readBoolean() ? UUID.fromString(in.readUTF()) : null;
                String beneficiaryName = in.readUTF();
                long credit = in.readLong(), created = in.readLong();
                Payment.Stage stage = Payment.Stage.valueOf(in.readUTF());
                int fields = bounded(in.readInt(), 256);
                Map<String, String> payload = new LinkedHashMap<>();
                for (int j = 0; j < fields; j++) payload.put(in.readUTF(), in.readUTF());
                Payment entry = new Payment(id, kind, payer, name, currency, cents, beneficiary,
                        beneficiaryName, credit, payload, created, stage);
                if (entries.putIfAbsent(id, entry) != null) throw new IOException("重复支付记录");
            }
            if (in.read() != -1) throw new IOException("支付台账存在多余数据");
        } catch (RuntimeException e) { throw new IOException("支付台账损坏", e); }
    }
    public synchronized Payment get(String id) { return entries.get(id); }
    public synchronized List<Payment> all() { return List.copyOf(entries.values()); }
    public synchronized void insert(Payment payment) throws IOException {
        if (entries.containsKey(payment.id())) throw new IllegalArgumentException("重复支付 ID");
        if (entries.values().stream().anyMatch(p -> p.payer().equals(payment.payer()) && !p.terminal()))
            throw new IllegalArgumentException("action-busy");
        write(payment);
    }
    public synchronized Payment transition(String id, Payment.Stage expected, Payment.Stage next) throws IOException {
        Payment old = Objects.requireNonNull(entries.get(id));
        if (old.stage() != expected) throw new IllegalStateException("支付状态已改变");
        Payment updated = old.at(next);
        write(updated);
        return updated;
    }
    private void write(Payment payment) throws IOException {
        Map<String, Payment> snapshot = new LinkedHashMap<>(entries);
        snapshot.put(payment.id(), payment);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(0x54504A31); out.writeInt(snapshot.size());
            for (Payment e : snapshot.values()) {
                out.writeUTF(e.id()); out.writeUTF(e.kind()); out.writeUTF(e.payer().toString());
                out.writeUTF(e.payerName()); out.writeUTF(e.currency()); out.writeLong(e.cents());
                out.writeBoolean(e.beneficiary() != null);
                if (e.beneficiary() != null) out.writeUTF(e.beneficiary().toString());
                out.writeUTF(e.beneficiaryName() == null ? "" : e.beneficiaryName());
                out.writeLong(e.creditCents()); out.writeLong(e.createdAt()); out.writeUTF(e.stage().name());
                out.writeInt(e.payload().size());
                for (var field : new TreeMap<>(e.payload()).entrySet()) { out.writeUTF(field.getKey()); out.writeUTF(field.getValue()); }
            }
        }
        AtomicFiles.write(file, bytes.toByteArray());
        entries.put(payment.id(), payment);
    }
    private static int bounded(int value, int max) throws IOException {
        if (value < 0 || value > max) throw new IOException("支付台账长度无效");
        return value;
    }
}
