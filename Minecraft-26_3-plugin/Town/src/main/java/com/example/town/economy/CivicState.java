package com.example.town.economy;

import java.util.*;

/** 与小镇权属一起保存的经济状态；applied 是支付与 towns.yml 之间的幂等凭据。 */
public record CivicState(Map<String, Long> balances, Map<String, Long> contributions,
                         Map<String, Integer> technologies, Map<String, Policy> policies,
                         Set<String> applied, List<Entry> ledger, Research research) {
    public record Policy(String id, long expiresAt) { }
    public record Entry(String operation, UUID actor, String currency, long delta, String reason, long at) { }
    public CivicState {
        balances = Map.copyOf(balances); contributions = Map.copyOf(contributions);
        technologies = Map.copyOf(technologies); policies = Map.copyOf(policies);
        applied = Set.copyOf(applied); ledger = List.copyOf(ledger);
        if (balances.values().stream().anyMatch(v -> v < 0) || contributions.values().stream().anyMatch(v -> v < 0)
                || technologies.values().stream().anyMatch(v -> v < 0)) throw new IllegalArgumentException("镇库数据无效");
        if (research != null && research.targetLevel() != technologies.getOrDefault(research.technology(), 0) + 1)
            throw new IllegalArgumentException("研究等级与科技存档不一致");
    }
    public CivicState(Map<String, Long> balances, Map<String, Long> contributions, Map<String, Integer> technologies,
                      Map<String, Policy> policies, Set<String> applied, List<Entry> ledger) {
        this(balances, contributions, technologies, policies, applied, ledger, null);
    }
    public static CivicState empty() { return new CivicState(Map.of(), Map.of(), Map.of(), Map.of(), Set.of(), List.of()); }
    public long balance(String currency) { return balances.getOrDefault(currency, 0L); }
    public int level(String technology) { return technologies.getOrDefault(technology, 0); }
    public CivicState transact(String operation, UUID actor, String currency, long delta, String reason, long now, boolean donation) {
        if (applied.contains(operation)) return this;
        Map<String, Long> money = new HashMap<>(balances), donated = new HashMap<>(contributions);
        long next = Math.addExact(balance(currency), delta);
        if (next < 0) throw new IllegalArgumentException("civic-insufficient");
        money.put(currency, next);
        if (donation && delta > 0) {
            String key = actor + "@" + currency;
            donated.put(key, Math.addExact(donated.getOrDefault(key, 0L), delta));
        }
        Set<String> operations = new HashSet<>(applied); operations.add(operation);
        List<Entry> entries = new ArrayList<>(ledger);
        entries.add(new Entry(operation, actor, currency, delta, reason, now));
        if (entries.size() > 1000) entries = new ArrayList<>(entries.subList(entries.size() - 1000, entries.size()));
        return new CivicState(money, donated, technologies, policies, operations, entries, research);
    }
    public CivicState technology(String id, int level) {
        Map<String, Integer> values = new HashMap<>(technologies); values.put(id, level);
        return new CivicState(balances, contributions, values, policies, applied, ledger, research);
    }
    public CivicState policy(String id, long expiresAt) {
        Map<String, Policy> values = new HashMap<>(policies); values.put(id, new Policy(id, expiresAt));
        return new CivicState(balances, contributions, technologies, values, applied, ledger, research);
    }
    public CivicState startResearch(String id, long requiredMillis, long now, CivicCatalog catalog) {
        if (research != null) throw new IllegalArgumentException("civic-research-busy");
        return new CivicState(balances, contributions, technologies, policies, applied, ledger,
                new Research(id, level(id) + 1, requiredMillis, 0, now, catalog.researchSpeedCap(), catalog.researchBoosts(this, now)));
    }
    public CivicState advanceResearch(long now) {
        if (research == null) return this;
        Research next = research.advance(now);
        if (next.complete()) {
            Map<String, Integer> values = new HashMap<>(technologies); values.put(next.technology(), next.targetLevel());
            return new CivicState(balances, contributions, values, policies, applied, ledger, null);
        }
        return new CivicState(balances, contributions, technologies, policies, applied, ledger, next);
    }
    public CivicState researchBonuses(CivicCatalog catalog, long now) {
        CivicState current = advanceResearch(now);
        if (current.research == null) return current;
        return new CivicState(current.balances, current.contributions, current.technologies, current.policies,
                current.applied, current.ledger, current.research.withBonuses(catalog.researchSpeedCap(), catalog.researchBoosts(current, now)));
    }
}
