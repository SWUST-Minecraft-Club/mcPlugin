package com.example.wallet.local;

import com.example.wallet.api.WalletException;
import com.example.wallet.api.WalletTransaction;
import com.example.wallet.api.WalletTransactionPage;
import com.google.gson.Gson;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;

/** Forced append journal. Import rows and their one-time marker share one commit. */
public final class LocalLedger implements AutoCloseable {
    public record Imported(UUID player, String name, String code, BigDecimal amount, String siteUser) { }
    public record Migration(boolean complete, long at, String source, int rows) { }
    private record Entry(UUID player, String name, WalletTransaction transaction) { }
    private record Event(int version, String kind, String source, long at, List<Entry> entries) { }
    private static final int MAGIC = 0x574c5431;
    private static final int MAX_RECORD = 64 * 1024 * 1024;
    private final Gson gson = new Gson();
    private final FileChannel channel;
    private final FileLock lock;
    private final Map<String, BigDecimal> balances = new HashMap<>();
    private final Map<String, Entry> business = new HashMap<>();
    private final List<Entry> history = new ArrayList<>();
    private Migration migration = new Migration(false, 0, "", 0);
    private boolean importing, failed;

    public LocalLedger(final Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        try {
            lock = channel.tryLock();
            if (lock == null) throw new IOException("钱包账本已被另一进程占用");
            if (channel.size() == 0) {
                write(ByteBuffer.allocate(4).putInt(MAGIC).flip());
                channel.force(true);
            }
            load();
        } catch (IOException | RuntimeException error) {
            channel.close();
            throw error;
        }
    }

    private static String key(final UUID player, final String code) { return player + "|" + code; }
    public synchronized BigDecimal balance(final UUID player, final String code) {
        return balances.getOrDefault(key(player, code), BigDecimal.ZERO);
    }
    public synchronized boolean hasAccount(final UUID player, final String code) {
        return balances.containsKey(key(player, code));
    }
    public synchronized Migration migration() { return migration; }
    public synchronized boolean available() { return !failed && channel.isOpen() && !importing; }
    public synchronized boolean needsRecovery() { return failed || !channel.isOpen(); }

    public synchronized WalletTransaction change(final UUID player, final String name, final String code,
            final BigDecimal amount, final boolean credit, final String businessNo, final String remark) {
        healthy();
        if (importing) throw new WalletException(0, "官网余额正在迁入，请稍后重试");
        if (player == null || code == null || code.isBlank() || businessNo == null || businessNo.isBlank()
                || amount == null || amount.signum() <= 0) {
            throw new WalletException(0, "玩家、正数金额和业务号不能为空");
        }
        if (businessNo.startsWith("account:") || businessNo.startsWith("site-import:")) {
            throw new WalletException(0, "业务号使用了钱包内部保留前缀");
        }
        final Entry previous = business.get(businessNo);
        if (previous != null) {
            final WalletTransaction tx = previous.transaction();
            if (!previous.player().equals(player) || !tx.assetCode().equals(code)
                    || !tx.type().equals(credit ? "CREDIT" : "DEBIT") || tx.amount().compareTo(amount) != 0) {
                throw new WalletException(0, "业务号已被不同的交易使用");
            }
            return tx;
        }
        final BigDecimal after = balance(player, code).add(credit ? amount : amount.negate());
        if (after.signum() < 0) throw new WalletException(0, "余额不足");
        if (after.precision() > 15) throw new WalletException(0, "余额超过安全精度范围");
        final long now = System.currentTimeMillis();
        final WalletTransaction tx = new WalletTransaction(UUID.randomUUID().toString(), businessNo,
                credit ? "CREDIT" : "DEBIT", "LOCAL", code, credit ? "IN" : "OUT", amount, after,
                remark == null ? "" : remark, now);
        commit(new Event(1, "CHANGE", "", now, List.of(new Entry(player, name, tx))));
        return tx;
    }

    public synchronized void createAccount(final UUID player, final String name, final String code) {
        healthy();
        if (importing) throw new WalletException(0, "官网余额正在迁入");
        if (player == null || code == null || code.isBlank()) throw new WalletException(0, "账户参数无效");
        if (hasAccount(player, code)) return;
        final long now = System.currentTimeMillis();
        final WalletTransaction tx = new WalletTransaction(UUID.randomUUID().toString(),
                "account:" + player + ":" + code, "ACCOUNT", "LOCAL", code, "IN", BigDecimal.ZERO,
                BigDecimal.ZERO, "创建账户", now);
        commit(new Event(1, "CHANGE", "", now, List.of(new Entry(player, name, tx))));
    }

    public synchronized WalletTransaction setBalance(final UUID player, final String name, final String code,
            final BigDecimal amount, final String businessNo, final String remark) {
        healthy();
        if (importing) throw new WalletException(0, "官网余额正在迁入，请稍后重试");
        if (player == null || code == null || code.isBlank() || amount == null || amount.signum() < 0
                || amount.precision() > 15 || businessNo == null || businessNo.isBlank()) {
            throw new WalletException(0, "玩家、币种、非负目标余额和业务号无效");
        }
        if (businessNo.startsWith("account:") || businessNo.startsWith("site-import:")) {
            throw new WalletException(0, "业务号使用了钱包内部保留前缀");
        }
        final Entry previous = business.get(businessNo);
        if (previous != null) {
            final WalletTransaction tx = previous.transaction();
            if (!previous.player().equals(player) || !tx.assetCode().equals(code) || !tx.type().equals("SET")
                    || tx.amount().compareTo(amount) != 0) throw new WalletException(0, "业务号已被不同的交易使用");
            return tx;
        }
        final long now = System.currentTimeMillis();
        final WalletTransaction tx = new WalletTransaction(UUID.randomUUID().toString(), businessNo, "SET", "LOCAL",
                code, "SET", amount, amount, (remark == null ? "" : remark) + "；原余额 " + balance(player, code), now);
        commit(new Event(1, "CHANGE", "", now, List.of(new Entry(player, name, tx))));
        return tx;
    }

    public synchronized void beginImport() {
        healthy();
        if (migration.complete()) throw new WalletException(0, "官网余额已经迁入，禁止重复执行");
        if (importing) throw new WalletException(0, "迁入已在进行中");
        importing = true;
    }
    public synchronized void endImport() { importing = false; }

    public synchronized Migration importOnce(final String source, final List<Imported> rows) {
        healthy();
        if (!importing || migration.complete()) throw new WalletException(0, "迁入状态无效或已经完成");
        final List<Entry> entries = new ArrayList<>();
        final var seen = new HashSet<String>();
        final long now = System.currentTimeMillis();
        for (final Imported row : rows) {
            final String account = key(row.player(), row.code());
            if (row.player() == null || row.code() == null || row.code().isBlank() || !seen.add(account)
                    || row.amount() == null || row.amount().signum() < 0
                    || row.siteUser() == null || row.siteUser().isBlank()
                    || business.containsKey("site-import:" + account)
                    || balance(row.player(), row.code()).add(row.amount()).precision() > 15) {
                throw new WalletException(0, "迁入包含重复账户或无效余额");
            }
            final WalletTransaction tx = new WalletTransaction(UUID.randomUUID().toString(),
                    "site-import:" + account, "IMPORT", "SITE_MIGRATION", row.code(), "IN", row.amount(),
                    balance(row.player(), row.code()).add(row.amount()), "旧官网余额迁入；用户 " + row.siteUser(), now);
            entries.add(new Entry(row.player(), row.name(), tx));
        }
        if (entries.isEmpty()) throw new WalletException(0, "没有可迁入且身份校验通过的账户，未消耗一次性迁入机会");
        commit(new Event(1, "IMPORT", source, now, List.copyOf(entries)));
        return migration;
    }

    public synchronized WalletTransactionPage transactions(final UUID player, final List<String> codes,
                                                           final int page, final int size) {
        final List<WalletTransaction> records = new ArrayList<>();
        for (int index = history.size() - 1; index >= 0; index--) {
            final Entry entry = history.get(index);
            if (entry.player().equals(player) && !entry.transaction().type().equals("ACCOUNT")
                    && (codes == null || codes.contains(entry.transaction().assetCode()))) {
                records.add(entry.transaction());
            }
        }
        final int safeSize = Math.clamp(size, 1, 200);
        final int from = (int) Math.min((long) Math.max(0, page - 1) * safeSize, records.size());
        final int to = Math.min(from + safeSize, records.size());
        return new WalletTransactionPage(List.copyOf(records.subList(from, to)), records.size());
    }

    private void healthy() {
        if (failed || !channel.isOpen()) throw new WalletException(0, "钱包账本不可用；请检查磁盘后重启，禁止继续记账");
    }

    private void commit(final Event event) {
        final byte[] bytes = gson.toJson(event).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_RECORD) throw new WalletException(0, "单次迁入超过账本限制，未写入");
        final CRC32 crc = new CRC32(); crc.update(bytes);
        final ByteBuffer record = ByteBuffer.allocate(bytes.length + 8).putInt(bytes.length).put(bytes).putInt((int) crc.getValue());
        record.flip();
        try {
            channel.position(channel.size());
            write(record);
            channel.force(true);
        } catch (final IOException error) {
            failed = true;
            throw new WalletException(0, "账本提交结果待核对，请重启恢复；当前已停止资金操作");
        }
        apply(event);
    }

    private void apply(final Event event) {
        for (final Entry entry : event.entries()) {
            balances.put(key(entry.player(), entry.transaction().assetCode()), entry.transaction().balanceAfter());
            business.put(entry.transaction().businessNo(), entry);
            history.add(entry);
        }
        if (event.kind().equals("IMPORT")) {
            migration = new Migration(true, event.at(), event.source(), event.entries().size());
        }
    }

    private void load() throws IOException {
        channel.position(0);
        final ByteBuffer header = ByteBuffer.allocate(4);
        if (!read(header) || header.flip().getInt() != MAGIC) throw new IOException("钱包账本文件头损坏");
        while (channel.position() < channel.size()) {
            final long start = channel.position();
            final ByteBuffer length = ByteBuffer.allocate(4);
            if (!read(length)) { truncateTail(start); break; }
            final int count = length.flip().getInt();
            if (count <= 0 || count > MAX_RECORD) throw new IOException("钱包账本记录长度损坏");
            if (channel.size() - channel.position() < count + 4L) { truncateTail(start); break; }
            final ByteBuffer body = ByteBuffer.allocate(count);
            final ByteBuffer checksum = ByteBuffer.allocate(4);
            if (!read(body) || !read(checksum)) throw new IOException("钱包账本读取中断");
            final CRC32 crc = new CRC32(); crc.update(body.array());
            if ((int) crc.getValue() != checksum.flip().getInt()) throw new IOException("钱包账本校验失败，停止加载");
            try {
                final Event event = gson.fromJson(new String(body.array(), StandardCharsets.UTF_8), Event.class);
                if (event.version() != 1 || (!event.kind().equals("IMPORT") && !event.kind().equals("CHANGE"))
                        || event.entries() == null || (event.kind().equals("IMPORT") && migration.complete())) {
                    throw new IllegalArgumentException("Invalid journal event");
                }
                for (final Entry entry : event.entries()) {
                    if (entry.player() == null || entry.transaction().balanceAfter().signum() < 0
                            || entry.transaction().balanceAfter().precision() > 15
                            || business.containsKey(entry.transaction().businessNo())) {
                        throw new IllegalArgumentException("Invalid journal entry");
                    }
                }
                if (event.entries().stream().map(e -> e.transaction().businessNo()).distinct().count() != event.entries().size()) {
                    throw new IllegalArgumentException("Duplicate business number in journal event");
                }
                apply(event);
            } catch (final RuntimeException error) { throw new IOException("钱包账本记录损坏，停止加载", error); }
        }
    }

    private void truncateTail(final long start) throws IOException { channel.truncate(start); channel.force(true); }
    private boolean read(final ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) if (channel.read(buffer) < 0) return false;
        return true;
    }
    private void write(final ByteBuffer buffer) throws IOException { while (buffer.hasRemaining()) channel.write(buffer); }
    @Override public synchronized void close() throws IOException {
        try { if (lock.isValid()) lock.release(); }
        finally { channel.close(); }
    }
}
