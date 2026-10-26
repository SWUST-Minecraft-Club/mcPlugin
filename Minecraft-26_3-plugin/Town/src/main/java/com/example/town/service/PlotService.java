package com.example.town.service;

import com.example.town.TownRegistry;
import com.example.town.model.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 所有地块变更使用注册表锁；待确认编辑只保存提案，绝不提前改变权属。 */
public final class PlotService {
    public record Limits(int maxArea,int maxPlots,long proposalMillis) {
        public Limits {
            if (maxArea<1 || maxArea>4_194_304 || maxPlots<1 || proposalMillis<1000)
                throw new IllegalArgumentException("地块限制配置无效");
        }
    }
    public record EditProposal(String token,String townId,String plotId,long revision,UUID proposer,
                               PlotBounds previous,PlotBounds next,Set<UUID> awaiting,long expiresAt) {
        public EditProposal { awaiting=Set.copyOf(awaiting); }
    }
    private final TownRegistry registry;
    private final Runnable changed;
    private volatile Limits limits;
    private volatile java.util.function.Predicate<PlotBounds> constructionLock=bounds->false;
    private volatile java.util.function.Predicate<String> commercialAssets=id->false;
    public void commercialAssets(java.util.function.Predicate<String> hasAssets) {commercialAssets=hasAssets;}
    private void noAssets(String id) {if(commercialAssets.test(id)) fail("plot-commercial-assets");}
    public void constructionLock(java.util.function.Predicate<PlotBounds> lock) {constructionLock=lock;}
    private void unlocked(PlotBounds bounds) {if(constructionLock.test(bounds)) fail("move-area-locked");}
    /** 全部读写在 registry.guard 内。 */
    private final Map<String,EditProposal> proposals=new HashMap<>();

    public PlotService(TownRegistry registry,Runnable changed,Limits limits) {
        this.registry=registry; this.changed=changed; this.limits=limits;
    }
    public void limits(Limits limits) { this.limits=limits; }

    public Plot create(UUID actor,String name,PlotKind kind,PlotBounds bounds,UUID owner) {
        if (name==null || name.isBlank() || name.length()>24) fail("plot-name-invalid");
        try (var ignored=registry.guard()) {
            Town town=registry.byMember(actor);
            requireManager(town,actor);
            String id;
            do { id="p"+UUID.randomUUID().toString().replace("-",""); }
            while (town.plots().containsKey(id));
            Plot plot=new Plot(id,name,kind==PlotKind.PUBLIC?town.mayor():owner,kind,bounds,Set.of(),null,0,kind==PlotKind.PUBLIC,0,0);
            unlocked(bounds);
            PlotRules.validate(town,plot,limits.maxArea(),limits.maxPlots());
            town.plots().put(id,plot);
            changed.run();
            return plot;
        }
    }

    public EditProposal propose(UUID actor,String townId,String plotId,PlotBounds next,long now) {
        try (var ignored=registry.guard()) {
            cleanup(now);
            Town town=town(townId);
            Plot plot=plot(town,plotId);
            requireEditor(town,plot,actor);
            noAssets(plotId);
            unlocked(next);
            PlotRules.validate(town,plot.withBounds(next),limits.maxArea(),limits.maxPlots());
            Set<UUID> awaiting=new HashSet<>();
            if (!plot.owner().equals(actor) && plot.kind()!=PlotKind.PUBLIC) awaiting.add(plot.owner());
            if (plot.requiresTenantConsent(next,now) && !plot.lease().tenant().equals(actor)) awaiting.add(plot.lease().tenant());
            proposals.values().removeIf(proposal->proposal.proposer().equals(actor));
            EditProposal proposal=new EditProposal(UUID.randomUUID().toString(),townId,plotId,plot.revision(),actor,plot.bounds(),next,
                    awaiting,Math.addExact(now,limits.proposalMillis()));
            proposals.put(proposal.token(),proposal);
            return proposal;
        }
    }

    public EditProposal approve(UUID actor,String token,long now) {
        try (var ignored=registry.guard()) {
            EditProposal proposal=proposal(token,now);
            if (!proposal.awaiting().contains(actor)) fail("plot-consent-denied");
            Set<UUID> awaiting=new HashSet<>(proposal.awaiting());
            awaiting.remove(actor);
            EditProposal approved=new EditProposal(proposal.token(),proposal.townId(),proposal.plotId(),proposal.revision(),
                    proposal.proposer(),proposal.previous(),proposal.next(),awaiting,proposal.expiresAt());
            proposals.put(token,approved);
            return approved;
        }
    }

    public Plot confirm(UUID actor,String token,long now) {
        try (var ignored=registry.guard()) {
            EditProposal proposal=proposal(token,now);
            if (!proposal.proposer().equals(actor)) fail("plot-consent-denied");
            if (!proposal.awaiting().isEmpty()) fail("plot-consent-pending");
            Town town=town(proposal.townId());
            Plot previous=plot(town,proposal.plotId());
            if (previous.revision()!=proposal.revision()) fail("action-state-changed");
            requireEditor(town,previous,actor);
            noAssets(previous.id());
            unlocked(proposal.next());
            Plot next=previous.withBounds(proposal.next());
            PlotRules.validate(town,next,limits.maxArea(),limits.maxPlots());
            town.plots().put(next.id(),next);
            proposals.remove(token);
            changed.run();
            return next;
        }
    }

    /** 业主委托与租户委托分别保存，租约期间业主不能给自己授予容器访问权。 */
    public Plot trust(UUID actor,String townId,String plotId,UUID target,boolean grant,long now) {
        try (var ignored=registry.guard()) {
            Town town=town(townId);
            Plot p=plot(town,plotId);
            boolean leased=p.lease()!=null && p.lease().active(now);
            UUID controller=leased?p.lease().tenant():p.owner();
            if (!controller.equals(actor)) fail("plot-owner-required");
            Set<UUID> trusted=new HashSet<>(leased?p.lease().trusted():p.trusted());
            if (grant) trusted.add(target); else trusted.remove(target);
            PlotLease lease=leased?new PlotLease(p.lease().tenant(),p.lease().expiresAt(),p.lease().paidCents(),p.lease().currency(),trusted,p.lease().publicInteraction()):p.lease();
            Plot next=new Plot(p.id(),p.name(),p.owner(),p.kind(),p.bounds(),leased?p.trusted():trusted,lease,
                    Math.incrementExact(p.revision()),p.publicInteraction(),p.offeredRentCents(),p.offeredDurationMillis(),p.offeredCurrency());
            town.plots().put(next.id(),next);
            changed.run();
            return next;
        }
    }

    public Plot offerRent(UUID actor,String townId,String plotId,long cents,long durationMillis,long now,String currency) {
        if (currency==null || currency.isBlank() || cents<0 || durationMillis<60_000 || durationMillis>365L*86400_000) fail("plot-rent-invalid");
        try (var ignored=registry.guard()) {
            Town town=town(townId);
            Plot p=plot(town,plotId);
            if (!p.owner().equals(actor)) fail("plot-owner-required");
            if (p.kind()==PlotKind.PUBLIC) fail("plot-public-rent-denied");
            if (p.lease()!=null && p.lease().active(now)) fail("plot-already-leased");
            Plot next=new Plot(p.id(),p.name(),p.owner(),p.kind(),p.bounds(),p.trusted(),p.lease(),
                    Math.incrementExact(p.revision()),p.publicInteraction(),cents,durationMillis,currency);
            town.plots().put(next.id(),next);
            changed.run();
            return next;
        }
    }

    public Plot update(UUID actor,String townId,String plotId,String name,PlotKind kind,Boolean publicInteraction) {
        try(var ignored=registry.guard()) {
            Town town=town(townId);Plot old=plot(town,plotId);
            boolean leased=old.lease()!=null && old.lease().active(System.currentTimeMillis());
            UUID controller=leased?old.lease().tenant():old.owner();
            boolean publicManager=old.kind()==PlotKind.PUBLIC && town.members().get(actor)!=null && town.members().get(actor).canManage();
            if(publicInteraction!=null && !controller.equals(actor) && !publicManager) fail("plot-owner-required");
            if((name!=null || kind!=null) && !old.owner().equals(actor) && !publicManager) fail("plot-owner-required");
            if(name!=null && (name.isBlank() || name.length()>24)) fail("plot-name-invalid");
            if(kind!=null) {
                noAssets(plotId);
                requireManager(town,actor);
                if(old.lease()!=null && old.lease().active(System.currentTimeMillis())) fail("plot-lease-active");
            }
            PlotLease nextLease=leased && publicInteraction!=null?new PlotLease(old.lease().tenant(),old.lease().expiresAt(),old.lease().paidCents(),old.lease().currency(),old.lease().trusted(),publicInteraction):old.lease();
            Plot next=new Plot(old.id(),name==null?old.name():name,old.owner(),kind==null?old.kind():kind,old.bounds(),old.trusted(),
                    nextLease,Math.incrementExact(old.revision()),publicInteraction==null || leased?old.publicInteraction():publicInteraction,
                    old.offeredRentCents(),old.offeredDurationMillis(),old.offeredCurrency());
            PlotRules.validate(town,next,limits.maxArea(),limits.maxPlots());
            town.plots().put(next.id(),next);changed.run();return next;
        }
    }
    public void remove(UUID actor,String townId,String plotId,long revision,long now) {
        try(var ignored=registry.guard()) {
            Town town=town(townId);Plot old=plot(town,plotId);
            if(!old.owner().equals(actor)) fail("plot-owner-required");
            noAssets(plotId);
            if(old.revision()!=revision) fail("action-state-changed");
            if(old.lease()!=null && old.lease().active(now)) fail("plot-lease-active");
            town.plots().remove(plotId);changed.run();
        }
    }
    public Plot transfer(UUID actor,String townId,String plotId,UUID receiver) {
        try(var ignored=registry.guard()) {
            Town town=town(townId);Plot old=plot(town,plotId);
            if(!old.owner().equals(actor) || old.kind()==PlotKind.PUBLIC) fail("plot-owner-required");
            noAssets(plotId);
            Plot next=new Plot(old.id(),old.name(),receiver,old.kind(),old.bounds(),Set.of(),old.lease(),
                    Math.incrementExact(old.revision()),old.publicInteraction(),0,0);
            town.plots().put(next.id(),next);changed.run();return next;
        }
    }
    public Plot cancelOffer(UUID actor,String townId,String plotId) {
        try(var ignored=registry.guard()) {
            Town town=town(townId);Plot old=plot(town,plotId);
            if(!old.owner().equals(actor)) fail("plot-owner-required");
            Plot next=new Plot(old.id(),old.name(),old.owner(),old.kind(),old.bounds(),old.trusted(),old.lease(),
                    Math.incrementExact(old.revision()),old.publicInteraction(),0,0);
            town.plots().put(next.id(),next);changed.run();return next;
        }
    }

    public EditProposal pending(UUID actor) {
        try (var ignored=registry.guard()) {
            cleanup(System.currentTimeMillis());
            return proposals.values().stream().filter(p->p.proposer().equals(actor)).findFirst().orElse(null);
        }
    }

    public java.util.List<EditProposal> awaiting(UUID actor) {
        try (var ignored=registry.guard()) {
            cleanup(System.currentTimeMillis());
            return proposals.values().stream().filter(p->p.awaiting().contains(actor)).toList();
        }
    }

    private EditProposal proposal(String token,long now) {
        cleanup(now);
        EditProposal proposal=proposals.get(token);
        if (proposal==null) fail("plot-proposal-expired");
        return proposal;
    }
    private void cleanup(long now) { proposals.values().removeIf(p->p.expiresAt()<=now); }
    private Town town(String id) {
        Town town=registry.byId(id);
        if (town==null) fail("not-in-town");
        return town;
    }
    private Plot plot(Town town,String id) {
        Plot plot=town.plots().get(id);
        if (plot==null) fail("plot-not-found");
        unlocked(plot.bounds());
        return plot;
    }
    private void requireManager(Town town,UUID actor) {
        Role role=town==null?null:town.members().get(actor);
        if (role==null || !role.canManage()) fail("plot-manager-required");
    }
    private void requireEditor(Town town,Plot plot,UUID actor) {
        if (!plot.owner().equals(actor)) requireManager(town,actor);
    }
    private static void fail(String code) { throw new IllegalArgumentException(code); }
}
