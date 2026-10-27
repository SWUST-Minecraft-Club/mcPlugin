package com.example.yudreamui.plugin;

import com.google.gson.Gson;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Durable order boundaries; money and Minecraft inventory cannot form one atomic transaction. */
final class ShopOrders implements AutoCloseable {
    enum Status { PAYMENT_PENDING, PAID, DELIVERING, DELIVERED, DECLINED, REVIEW }
    record Order(String id, UUID player, String playerName, String product, String material, int amount,
                 String currency, BigDecimal price, String wallet, Status status) {
        Order status(Status next) { return new Order(id,player,playerName,product,material,amount,currency,price,wallet,next); }
        String businessNo() { return "yudreamui:shop:" + id; }
    }
    private final Path directory;
    private final Gson gson = new Gson();
    private final Map<String,Order> orders = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("yudreamui-orders").factory());
    ShopOrders(Path directory) throws IOException {
        this.directory = directory; Files.createDirectories(directory);
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                Order order;
                try { order = gson.fromJson(Files.readString(file), Order.class); validate(order); }
                catch (Exception error) { throw new IOException("Invalid shop order: " + file.getFileName(),error); }
                if (!file.getFileName().toString().equals(order.id + ".json")) throw new IOException("Order filename mismatch");
                if (order.status == Status.DELIVERING) { order = order.status(Status.REVIEW); write(order); }
                orders.put(order.id, order);
            }
        }
    }
    static void validate(Order order) {
        Objects.requireNonNull(order); UUID.fromString(order.id); Objects.requireNonNull(order.player); Objects.requireNonNull(order.status);
        if (order.amount < 1 || order.amount > 4096 || order.price == null || order.price.signum() <= 0 || order.wallet == null)
            throw new IllegalArgumentException("Invalid order amount");
    }
    List<Order> pending(UUID player) { return orders.values().stream().filter(o -> o.player.equals(player)
        && o.status != Status.DELIVERED && o.status != Status.DECLINED).sorted(Comparator.comparing(Order::id)).toList(); }
    List<Order> all() { return List.copyOf(orders.values()); }
    CompletableFuture<Order> create(Order order) {
        return CompletableFuture.supplyAsync(() -> { validate(order); if(orders.containsKey(order.id)) throw new IllegalStateException("Duplicate order");
            try { write(order); orders.put(order.id,order); return order; } catch(IOException error){throw new CompletionException(error);}
        },io);
    }
    CompletableFuture<Order> transition(Order order, Status expected, Status next) {
        return CompletableFuture.supplyAsync(() -> {
            var current = orders.get(order.id);
            if (current == null || current.status != expected) throw new IllegalStateException("Order state changed");
            var updated = current.status(next);
            try { write(updated); orders.put(updated.id,updated); return updated; } catch(IOException error){throw new CompletionException(error);}
        },io);
    }
    private void write(Order order) throws IOException {
        Path target=directory.resolve(order.id+".json"), temp=directory.resolve(order.id+".tmp");
        byte[] data=gson.toJson(order).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try(var channel=FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
            ByteBuffer buffer=ByteBuffer.wrap(data); while(buffer.hasRemaining()) channel.write(buffer); channel.force(true);
        }
        Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        try(var channel=FileChannel.open(directory,StandardOpenOption.READ)){channel.force(true);}
    }
    @Override public void close() { io.shutdown(); try { if(!io.awaitTermination(5,TimeUnit.SECONDS)) io.shutdownNow(); }
        catch(InterruptedException error){Thread.currentThread().interrupt();io.shutdownNow();} }
}
