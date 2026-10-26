package com.example.town.store;

import com.example.town.model.Plot;
import com.example.town.model.PlotBounds;
import com.example.town.model.PlotKind;
import com.example.town.model.PlotLease;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** 独立的地块序列化，能在不启动 Bukkit 的情况下验证租约和授权往返。 */
public final class PlotCodec {
    private PlotCodec() { }

    public static Map<String,Object> encode(Plot plot) {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id",plot.id());
        out.put("name",plot.name());
        out.put("owner",plot.owner().toString());
        out.put("kind",plot.kind().name());
        out.put("world",plot.bounds().world());
        out.put("min-x",plot.bounds().minX());
        out.put("min-z",plot.bounds().minZ());
        out.put("max-x",plot.bounds().maxX());
        out.put("max-z",plot.bounds().maxZ());
        out.put("trusted",plot.trusted().stream().map(UUID::toString).sorted().toList());
        out.put("revision",plot.revision());
        out.put("public-interaction",plot.publicInteraction());
        out.put("offered-rent-cents",plot.offeredRentCents());
        out.put("offered-duration-millis",plot.offeredDurationMillis());
        out.put("offered-currency",plot.offeredCurrency());
        if (plot.lease()!=null) {
            PlotLease lease=plot.lease();
            out.put("lease",Map.of("tenant",lease.tenant().toString(),"expires",lease.expiresAt(),
                    "paid-cents",lease.paidCents(),"currency",lease.currency(),
                    "trusted",lease.trusted().stream().map(UUID::toString).sorted().toList(),"public-interaction",lease.publicInteraction()));
        }
        return out;
    }

    public static Plot decode(Map<?,?> in) {
        PlotLease lease=null;
        if (in.get("lease") instanceof Map<?,?> raw) {
            lease=new PlotLease(UUID.fromString(text(raw,"tenant")),number(raw,"expires"),
                    number(raw,"paid-cents"),text(raw,"currency"),trusted(raw.get("trusted")),Boolean.TRUE.equals(raw.get("public-interaction")));
        }
        return new Plot(text(in,"id"),text(in,"name"),UUID.fromString(text(in,"owner")),
                PlotKind.valueOf(text(in,"kind")),
                new PlotBounds(text(in,"world"),Math.toIntExact(number(in,"min-x")),Math.toIntExact(number(in,"min-z")),
                        Math.toIntExact(number(in,"max-x")),Math.toIntExact(number(in,"max-z"))),
                trusted(in.get("trusted")),lease,number(in,"revision"),Boolean.TRUE.equals(in.get("public-interaction")),
                number(in,"offered-rent-cents"),number(in,"offered-duration-millis"),
                in.get("offered-currency") instanceof String currency?currency:"");
    }

    private static String text(Map<?,?> in,String key) {
        if (!(in.get(key) instanceof String value)) throw new IllegalArgumentException("地块缺少字段 "+key);
        return value;
    }
    private static long number(Map<?,?> in,String key) {
        Object value=in.get(key);
        if (value==null) return 0;
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue()!=n.longValue())
            throw new IllegalArgumentException("地块数值无效 "+key);
        return n.longValue();
    }
    private static Set<UUID> trusted(Object raw) {
        if (raw==null) return Set.of();
        if (!(raw instanceof List<?> list)) throw new IllegalArgumentException("地块授权列表无效");
        return list.stream().map(value->UUID.fromString(String.valueOf(value))).collect(Collectors.toUnmodifiableSet());
    }
}
