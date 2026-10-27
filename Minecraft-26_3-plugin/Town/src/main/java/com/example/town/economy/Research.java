package com.example.town.economy;

import java.util.List;

/** Persisted base work and speed windows. Offline time is integrated across policy expirations. */
public record Research(String technology, int targetLevel, long requiredMillis, double completedMillis,
                       long updatedAt, double speedCap, List<Boost> boosts) {
    public record Boost(double bonus, long expiresAt) {
        public Boost {
            if (!Double.isFinite(bonus) || bonus < 0 || bonus > 1 || expiresAt < 0)
                throw new IllegalArgumentException("科研加速数据无效");
        }
    }
    public Research {
        boosts = List.copyOf(boosts);
        if (technology == null || !technology.matches("[a-z0-9_-]+") || targetLevel < 1 || targetLevel > 100
                || requiredMillis < 1 || !Double.isFinite(completedMillis) || completedMillis < 0
                || completedMillis > requiredMillis || updatedAt < 0 || !Double.isFinite(speedCap) || speedCap < 0 || speedCap > 1)
            throw new IllegalArgumentException("研究数据无效");
    }
    public boolean complete() { return completedMillis >= requiredMillis; }
    public double speedBonus(long at) {
        return Math.min(speedCap, boosts.stream().filter(b -> b.expiresAt() > at).mapToDouble(Boost::bonus).sum());
    }
    public Research advance(long now) {
        if (now <= updatedAt || complete()) return this;
        long cursor = updatedAt;
        double work = completedMillis;
        for (long end : boosts.stream().mapToLong(Boost::expiresAt).filter(t -> t > updatedAt && t < now).distinct().sorted().toArray()) {
            work = Math.min(requiredMillis, work + (end - cursor) * (1 + speedBonus(cursor)));
            cursor = end;
        }
        work = Math.min(requiredMillis, work + (now - cursor) * (1 + speedBonus(cursor)));
        return new Research(technology, targetLevel, requiredMillis, work, now, speedCap, boosts);
    }
    public Research withBonuses(double cap, List<Boost> next) {
        return new Research(technology, targetLevel, requiredMillis, completedMillis, updatedAt, cap, next);
    }
    public long remainingMillis(long now) {
        Research current = advance(now);
        if (current.complete()) return 0;
        long cursor = Math.max(now, current.updatedAt);
        long start = cursor;
        double left = requiredMillis - current.completedMillis, elapsed = 0;
        for (long end : boosts.stream().mapToLong(Boost::expiresAt).filter(t -> t > start).distinct().sorted().toArray()) {
            double rate = 1 + speedBonus(cursor), segment = end - cursor;
            if (left <= segment * rate) return (long)Math.ceil(elapsed + left / rate);
            left -= segment * rate; elapsed += segment; cursor = end;
        }
        return (long)Math.ceil(elapsed + left);
    }
    public int percent(long now) { return (int)Math.floor(100 * advance(now).completedMillis / requiredMillis); }
}
