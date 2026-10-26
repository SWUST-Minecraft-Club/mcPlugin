package com.example.town.model;

import java.util.Set;
import java.util.UUID;

/** 有效租约内业主保留行政管理权，容器与建造权由租户及其受托人持有。 */
public record PlotLease(UUID tenant, long expiresAt, long paidCents, String currency, Set<UUID> trusted,boolean publicInteraction) {
    public PlotLease {
        if (tenant == null || expiresAt <= 0 || paidCents < 0 || currency == null)
            throw new IllegalArgumentException("租约无效");
        trusted = Set.copyOf(trusted);
    }
    public PlotLease(UUID tenant,long expiresAt,long paidCents,String currency,Set<UUID> trusted) {
        this(tenant,expiresAt,paidCents,currency,trusted,false);
    }
    public boolean active(long now) { return expiresAt > now; }
    public boolean permits(UUID actor) { return tenant.equals(actor) || trusted.contains(actor); }
}
