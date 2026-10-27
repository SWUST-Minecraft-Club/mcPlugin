package com.example.yudreamui.protocol;

import com.google.gson.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Stable application protocol. Its messages contain no mapped game types. */
public final class Wire {
    public static final String CHANNEL = "yudreamui:wire";
    public static final int VERSION = 1, MAX_MESSAGE = 512 * 1024, CHUNK = 24 * 1024;
    private static final int MAGIC = 0x59554931, HEADER = 20;
    private static final AtomicLong IDS = new AtomicLong();
    private Wire() {}

    public static JsonObject message(String kind) {
        var value = new JsonObject();
        value.addProperty("protocol", VERSION);
        value.addProperty("kind", kind);
        return value;
    }
    public static List<byte[]> encode(JsonObject value) {
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_MESSAGE) throw new IllegalArgumentException("UI message too large");
        int count = Math.max(1, (bytes.length + CHUNK - 1) / CHUNK);
        long id = IDS.incrementAndGet();
        var result = new ArrayList<byte[]>(count);
        for (int i = 0; i < count; i++) {
            int size = Math.min(CHUNK, bytes.length - i * CHUNK);
            result.add(ByteBuffer.allocate(HEADER + size).putInt(MAGIC).putLong(id)
                    .putShort((short) i).putShort((short) count).putInt(bytes.length)
                    .put(bytes, i * CHUNK, size).array());
        }
        return result;
    }
    public static final class Decoder {
        private final Map<Long, Pending> pending = new HashMap<>();
        public synchronized JsonObject accept(byte[] frame) {
            long now = System.nanoTime();
            pending.values().removeIf(p -> now - p.created > 10_000_000_000L);
            if (frame.length < HEADER || frame.length > HEADER + CHUNK) throw new IllegalArgumentException("Invalid UI fragment");
            var buffer = ByteBuffer.wrap(frame);
            if (buffer.getInt() != MAGIC) throw new IllegalArgumentException("Invalid UI magic");
            long id = buffer.getLong();
            int index = Short.toUnsignedInt(buffer.getShort()), count = Short.toUnsignedInt(buffer.getShort()), total = buffer.getInt();
            if (total < 1 || total > MAX_MESSAGE || count != (total + CHUNK - 1) / CHUNK || index >= count)
                throw new IllegalArgumentException("Invalid UI fragment bounds");
            int expected = Math.min(CHUNK, total - index * CHUNK);
            if (buffer.remaining() != expected) throw new IllegalArgumentException("Invalid UI fragment length");
            if (!pending.containsKey(id) && pending.size() >= 4) throw new IllegalArgumentException("Too many UI transfers");
            var p = pending.computeIfAbsent(id, key -> new Pending(total, count, now));
            if (p.bytes.length != total || p.received.length != count) { pending.remove(id); throw new IllegalArgumentException("Changed UI transfer"); }
            if (!p.received[index]) {
                buffer.get(p.bytes, index * CHUNK, expected);
                p.received[index] = true;
                p.count++;
            }
            if (p.count != count) return null;
            pending.remove(id);
            var json = JsonParser.parseString(new String(p.bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!json.has("protocol") || json.get("protocol").getAsInt() != VERSION) throw new IllegalArgumentException("Unsupported UI protocol");
            return json;
        }
        public synchronized void clear() { pending.clear(); }
        private static final class Pending {
            final byte[] bytes; final boolean[] received; final long created; int count;
            Pending(int size, int parts, long created) { bytes = new byte[size]; received = new boolean[parts]; this.created = created; }
        }
    }
}
