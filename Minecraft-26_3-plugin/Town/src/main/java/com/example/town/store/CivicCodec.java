package com.example.town.store;

import com.example.town.economy.CivicState;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;

public final class CivicCodec {
    private CivicCodec() { }
    public static Map<String, Object> encode(CivicState s) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("balances", s.balances()); result.put("contributions", s.contributions());
        result.put("technologies", s.technologies());
        result.put("policies", s.policies().values().stream().map(p -> Map.of("id", p.id(), "expires", p.expiresAt())).toList());
        result.put("applied", s.applied().stream().sorted().toList());
        result.put("ledger", s.ledger().stream().map(e -> Map.of("id", e.operation(), "actor", e.actor().toString(),
                "currency", e.currency(), "delta", e.delta(), "reason", e.reason(), "at", e.at())).toList());
        return result;
    }
    public static CivicState decode(ConfigurationSection s) {
        if (s == null) return CivicState.empty();
        Map<String, Long> balances = numbers(s.getConfigurationSection("balances"));
        Map<String, Long> contributions = numbers(s.getConfigurationSection("contributions"));
        Map<String, Integer> tech = new HashMap<>();
        numbers(s.getConfigurationSection("technologies")).forEach((k, v) -> tech.put(k, Math.toIntExact(v)));
        Map<String, CivicState.Policy> policies = new HashMap<>();
        for (Map<?, ?> raw : s.getMapList("policies")) {
            String id = raw.get("id").toString();
            policies.put(id, new CivicState.Policy(id, number(raw.get("expires"))));
        }
        List<CivicState.Entry> ledger = new ArrayList<>();
        for (Map<?, ?> raw : s.getMapList("ledger")) ledger.add(new CivicState.Entry(raw.get("id").toString(),
                UUID.fromString(raw.get("actor").toString()), raw.get("currency").toString(), number(raw.get("delta")),
                raw.get("reason").toString(), number(raw.get("at"))));
        return new CivicState(balances, contributions, tech, policies, Set.copyOf(s.getStringList("applied")), ledger);
    }
    private static Map<String, Long> numbers(ConfigurationSection section) {
        Map<String, Long> result = new HashMap<>();
        if (section != null) section.getValues(false).forEach((k, v) -> result.put(k, number(v)));
        return result;
    }
    private static long number(Object value) { return new java.math.BigDecimal(value.toString()).longValueExact(); }
}
