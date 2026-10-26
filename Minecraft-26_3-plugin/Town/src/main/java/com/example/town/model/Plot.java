package com.example.town.model;

import java.util.Set;
import java.util.UUID;

/** 不可变地块；边界编辑保持 ID 与租约，revision 用于付款/确认后的乐观并发校验。 */
public record Plot(String id, String name, UUID owner, PlotKind kind, PlotBounds bounds,
                   Set<UUID> trusted, PlotLease lease, long revision, boolean publicInteraction,
                   long offeredRentCents, long offeredDurationMillis, String offeredCurrency) {
    public Plot {
        if (id == null || id.isBlank() || !id.matches("[a-z0-9_-]+") || name == null || name.isBlank()
                || owner == null || kind == null || bounds == null || revision < 0
                || offeredRentCents < 0 || offeredDurationMillis < 0)
            throw new IllegalArgumentException("地块数据无效");
        trusted=Set.copyOf(trusted);
        offeredCurrency=offeredCurrency==null?"":offeredCurrency;
    }
    public Plot(String id,String name,UUID owner,PlotKind kind,PlotBounds bounds,Set<UUID> trusted,PlotLease lease,
                long revision,boolean publicInteraction,long offeredRentCents,long offeredDurationMillis) {
        this(id,name,owner,kind,bounds,trusted,lease,revision,publicInteraction,offeredRentCents,offeredDurationMillis,"");
    }
    public boolean canUse(UUID actor, long now) {
        if (lease != null && lease.active(now)) return lease.permits(actor);
        return owner.equals(actor) || trusted.contains(actor);
    }
    public boolean publicInteractionAt(long now) {return lease!=null && lease.active(now)?lease.publicInteraction():publicInteraction;}
    public boolean requiresTenantConsent(PlotBounds next, long now) {
        return lease != null && lease.active(now) && !next.contains(bounds);
    }
    public Plot withBounds(PlotBounds next) {
        return new Plot(id,name,owner,kind,next,trusted,lease,Math.incrementExact(revision),publicInteraction,offeredRentCents,offeredDurationMillis,offeredCurrency);
    }
}
