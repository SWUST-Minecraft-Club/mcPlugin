package com.example.town.service;

import com.example.town.*;
import com.example.town.economy.*;
import com.example.town.model.*;
import com.example.town.store.*;
import com.example.town.util.Threads;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/** 成立、圈地、祝福、镇库和租赁共用可恢复的支付入口。所有落账只依赖持久数据。 */
public final class CivicService {
    private final TownPlugin plugin;
    private final TownRegistry registry;
    private final TownStore store;
    private final DurablePayments payments;
    private final Set<UUID> submitting=ConcurrentHashMap.newKeySet();
    private volatile CivicCatalog catalog;

    public CivicService(TownPlugin plugin,TownStore store) throws IOException {
        this.plugin=plugin;this.registry=plugin.registry();this.store=store;
        reload();
        var hook=plugin.service().wallet();
        payments=new DurablePayments(new PaymentJournal(plugin.getDataFolder().toPath().resolve("payments.bin")),
                new DurablePayments.Wallet() {
                    public java.util.concurrent.CompletableFuture<Void> debit(Payment p) {
                        if(hook==null) return java.util.concurrent.CompletableFuture.failedFuture(new IOException("经济服务暂不可用"));
                        hook.checkBinding(p);
                        return hook.debitCents(p);
                    }
                    public java.util.concurrent.CompletableFuture<Void> credit(Payment p,boolean refund) {
                        if(hook==null) return java.util.concurrent.CompletableFuture.failedFuture(new IOException("经济服务暂不可用"));
                        hook.checkBinding(p);
                        return hook.creditCents(p,refund);
                    }
                    public boolean rejected(Throwable error) {return hook!=null && hook.definiteRejection(error);}
                },task->Threads.async(plugin,task),error->plugin.getLogger().warning("小镇交易等待恢复："+error.getClass().getSimpleName()));
        for(String kind:List.of("found","claim","buff","donate","withdraw","technology","policy","rent"))
            payments.handler(kind,this::apply);
    }
    public void reload() { catalog=CivicCatalog.load(plugin.getConfig()); }
    public CivicCatalog catalog() {return catalog;}
    public DurablePayments payments() {return payments;}
    public boolean pending(String townId) {return payments.all().stream().anyMatch(p->!p.terminal() && townId.equals(p.payload().get("town")));}
    public String currency() {
        var hook=plugin.service().wallet();
        return hook==null?"":hook.resolveCurrency(plugin.config().currencyCode());
    }
    private String currency(long cents) {
        String code=currency();if(cents>0 && code.isBlank()) fail("wallet-missing");return code;
    }
    public int claimLimit(Town town) {return catalog.claimLimit(plugin.config().maxChunksPerTown(),town.civic());}

    public void found(Player player,Consumer<TownService.Result> feedback) {
        try(var ignored=registry.guard()) {
            Town town=member(player.getUniqueId());
            validateFound(town,player.getUniqueId());
            submit(player,Payment.create("found",player.getUniqueId(),player.getName(),currency(Money.cents(plugin.config().foundingCost())),Money.cents(plugin.config().foundingCost()),
                    null,"",0,Map.of("town",town.id())),feedback,"founded");
        }catch(IllegalArgumentException error){failure(feedback,error);}
    }
    public void claim(Player player,ChunkKey key,Consumer<TownService.Result> feedback) {
        try(var ignored=registry.guard()) {
            Town town=member(player.getUniqueId());
            validateClaim(town,player.getUniqueId(),key);
            long price=town.freeClaimsRemaining()>0?0:Money.cents(plugin.config().pricePerChunk());
            submit(player,Payment.create("claim",player.getUniqueId(),player.getName(),currency(price),price,null,"",0,
                    Map.of("town",town.id(),"chunk",key.encode())),feedback,"claim-success");
        }catch(IllegalArgumentException error){failure(feedback,error);}
    }
    public void buff(Player player,String id,Consumer<TownService.Result> feedback) {
        try(var ignored=registry.guard()) {
            Town town=member(player.getUniqueId());manager(town,player.getUniqueId());
            TownConfig.BuffDef def=buff(id);
            if(town.buffs().getOrDefault(id,0L)==-1) fail("buff-active");
            submit(player,Payment.create("buff",player.getUniqueId(),player.getName(),currency(Money.cents(def.cost())),Money.cents(def.cost()),null,"",0,
                    Map.of("town",town.id(),"buff",id,"duration",String.valueOf(def.durationMillis()))),feedback,"civic-purchased");
        }catch(IllegalArgumentException error){failure(feedback,error);}
    }
    public void donate(Player player,long cents,Consumer<TownService.Result> feedback) {
        try(var ignored=registry.guard()) {
            if(cents<=0) fail("civic-amount-invalid");
            Town town=member(player.getUniqueId());
            if(town.state()!=TownState.ACTIVE) fail("must-be-active");
            submit(player,Payment.create("donate",player.getUniqueId(),player.getName(),currency(cents),cents,null,"",0,
                    Map.of("town",town.id())),feedback,"civic-donated");
        }catch(IllegalArgumentException error){failure(feedback,error);}
    }
    public void withdraw(Player player,long cents,Consumer<TownService.Result> feedback) {
        try(var ignored=registry.guard()) {
            Town town=member(player.getUniqueId());
            if(!town.mayor().equals(player.getUniqueId())) fail("transfer-must-mayor");
            if(cents<=0) fail("civic-amount-invalid");
            if(town.civic().balance(currency())<cents) fail("civic-insufficient");
            submit(player,Payment.create("withdraw",player.getUniqueId(),player.getName(),currency(cents),0,
                    player.getUniqueId(),player.getName(),cents,Map.of("town",town.id())),feedback,"civic-withdrawn");
        }catch(IllegalArgumentException error){failure(feedback,error);}
    }
    public void purchase(Player player,String id,boolean technology,Consumer<TownService.Result> feedback) {
        try(var ignored=registry.guard()) {
            Town town=member(player.getUniqueId());manager(town,player.getUniqueId());
            long price;int level=town.civic().level(id);
            if(technology) {
                var def=catalog.technologies().get(id);
                if(def==null) fail("civic-not-found");
                if(level>=def.maxLevel()) fail("civic-max-level");
                price=def.cost(level);
            }else {
                var def=catalog.policies().get(id);
                if(def==null) fail("civic-not-found");
                validatePolicy(town,id,System.currentTimeMillis());price=def.cents();
            }
            if(town.civic().balance(currency())<price) fail("civic-insufficient");
            submit(player,Payment.create(technology?"technology":"policy",player.getUniqueId(),player.getName(),currency(),0,null,"",0,
                    Map.of("town",town.id(),"id",id,"price",String.valueOf(price),"level",String.valueOf(level))),feedback,"civic-purchased");
        }catch(IllegalArgumentException error){failure(feedback,error);}
    }
    public void rent(Player player,String plotId,Consumer<TownService.Result> feedback) {
        try(var ignored=registry.guard()) {
            Town town=registry.all().stream().filter(t->t.plots().containsKey(plotId)).findFirst().orElseThrow(()->new IllegalArgumentException("plot-not-found"));
            Plot plot=town.plots().get(plotId);validateRent(plot,player.getUniqueId(),System.currentTimeMillis());
            submit(player,Payment.create("rent",player.getUniqueId(),player.getName(),plot.offeredCurrency(),plot.offeredRentCents(),
                    plot.owner(),plot.owner().toString(),plot.offeredRentCents(),Map.of("town",town.id(),"plot",plot.id(),
                            "revision",String.valueOf(plot.revision()),"duration",String.valueOf(plot.offeredDurationMillis()))),feedback,"plot-rented");
        }catch(IllegalArgumentException error){failure(feedback,error);}
    }
    public void submit(Player player,Payment payment,Consumer<TownService.Result> feedback,String success) {
        if((payment.cents()>0 || payment.creditCents()>0) && currency().isBlank()) {feedback.accept(TownService.Result.fail("wallet-missing"));return;}
        var hook=plugin.service().wallet();
        if(hook!=null && (payment.cents()>0 || payment.creditCents()>0) && !hook.acceptsCurrency(payment.currency())) {
            feedback.accept(TownService.Result.fail("action-state-changed"));return;
        }
        Payment bound=hook==null?payment:payment.withEconomy(hook.id());
        UUID actor=player.getUniqueId();
        if(!submitting.add(actor)) {feedback.accept(TownService.Result.fail("action-busy"));return;}
        if(payments.busy(actor)) {submitting.remove(actor);feedback.accept(TownService.Result.fail("action-busy"));return;}
        plugin.messages().send(player,"payment-processing",Placeholder.unparsed("id",payment.id()));
        Threads.async(plugin,()->{
            try {
                payments.submit(bound,done->{
                    submitting.remove(actor);
                    Threads.forPlayer(plugin,player,()->{
                        if(done.stage()!=Payment.Stage.DONE) {
                            feedback.accept(TownService.Result.fail(done.stage()==Payment.Stage.REFUNDED?"payment-refunded":"wallet-failed"));return;
                        }
                        var view=plugin.service().townOf(actor);
                        if(done.kind().equals("found") && view!=null && !view.hallSet()) plugin.townHalls().give(player);
                        feedback.accept(TownService.Result.ok(success,Placeholder.unparsed("name",view==null?"":view.name()),
                                Placeholder.unparsed("count",view==null?"0":String.valueOf(view.claimCount()))));
                    });
                });
            }catch(IOException|RuntimeException error) {
                submitting.remove(actor);
                Threads.forPlayer(plugin,player,()->feedback.accept(TownService.Result.fail("payment-storage-failed")));
                plugin.getLogger().severe("支付请求持久化失败："+error.getClass().getSimpleName());
            }
        });
    }
    private DurablePayments.Applied apply(Payment p) throws IOException {
        try(var ignored=registry.guard()) {
            Town town=registry.byId(p.payload().get("town"));
            if(town==null) return DurablePayments.Applied.REJECT;
            if(town.civic().applied().contains(p.id())) return DurablePayments.Applied.YES;
            CivicState previous=town.civic();TownState state=town.state();
            Map<String,Plot> plots=new HashMap<>(town.plots());Map<String,Long> buffs=new HashMap<>(town.buffs());
            ChunkKey added=null;int free=town.freeClaimsRemaining();
            long now=System.currentTimeMillis();
            try {
                switch(p.kind()) {
                    case "found" -> {validateFound(town,p.payer());if(p.cents()!=Money.cents(plugin.config().foundingCost())) fail("action-state-changed");town.state(TownState.ACTIVE);}
                    case "claim" -> {
                        ChunkKey key=ChunkKey.decode(p.payload().get("chunk"));
                        if(key==null) fail("action-state-changed");
                        validateClaim(town,p.payer(),key);
                        if(p.cents()!=(free>0?0:Money.cents(plugin.config().pricePerChunk()))) fail("action-state-changed");
                        town.claims().add(key);registry.indexClaim(key,town.id());added=key;
                        if(free>0) town.freeClaimsRemaining(free-1);
                    }
                    case "buff" -> {
                        manager(town,p.payer());var def=buff(p.payload().get("buff"));
                        if(p.cents()!=Money.cents(def.cost()) || def.durationMillis()!=Long.parseLong(p.payload().get("duration"))) fail("action-state-changed");
                        long existing=town.buffs().getOrDefault(def.id(),0L);
                        if(existing==-1) fail("buff-active");
                        town.buffs().put(def.id(),def.permanent()?-1:Math.addExact(Math.max(now,existing),def.durationMillis()));
                    }
                    case "donate" -> {
                        if(!town.members().containsKey(p.payer()) || town.state()!=TownState.ACTIVE) fail("action-state-changed");
                        town.civic(previous.transact(p.id(),p.payer(),p.currency(),p.cents(),"镇库贡献",now,true));
                    }
                    case "withdraw" -> {
                        if(!town.mayor().equals(p.payer())) fail("transfer-must-mayor");
                        town.civic(previous.transact(p.id(),p.payer(),p.currency(),-p.creditCents(),"镇库提取",now,false));
                    }
                    case "technology","policy" -> {
                        manager(town,p.payer());String id=p.payload().get("id");long price=Long.parseLong(p.payload().get("price"));
                        if(p.kind().equals("technology")) {
                            var def=catalog.technologies().get(id);int level=previous.level(id);
                            if(def==null || level!=Integer.parseInt(p.payload().get("level")) || level>=def.maxLevel() || price!=def.cost(level)) fail("action-state-changed");
                            town.civic(previous.transact(p.id(),p.payer(),p.currency(),-price,"科技："+id,now,false).technology(id,level+1));
                        }else {
                            var def=catalog.policies().get(id);if(def==null || price!=def.cents()) fail("action-state-changed");
                            validatePolicy(town,id,now);
                            long base=Math.max(now,previous.policies().getOrDefault(id,new CivicState.Policy(id,0)).expiresAt());
                            town.civic(previous.transact(p.id(),p.payer(),p.currency(),-price,"政策："+id,now,false).policy(id,Math.addExact(base,def.durationMillis())));
                        }
                    }
                    case "rent" -> {
                        Plot plot=town.plots().get(p.payload().get("plot"));validateRent(plot,p.payer(),now);
                        if(plot.revision()!=Long.parseLong(p.payload().get("revision")) || !p.currency().equals(plot.offeredCurrency()) || p.cents()!=plot.offeredRentCents()
                                || !plot.owner().equals(p.beneficiary()) || p.creditCents()!=p.cents()
                                || plot.offeredDurationMillis()!=Long.parseLong(p.payload().get("duration"))) fail("action-state-changed");
                        town.plots().put(plot.id(),new Plot(plot.id(),plot.name(),plot.owner(),plot.kind(),plot.bounds(),plot.trusted(),
                                new PlotLease(p.payer(),Math.addExact(now,plot.offeredDurationMillis()),p.cents(),p.currency(),Set.of()),
                                Math.incrementExact(plot.revision()),plot.publicInteraction(),0,0));
                    }
                    default -> throw new IllegalStateException("未知支付类型");
                }
                if(!town.civic().applied().contains(p.id())) town.civic(town.civic().transact(p.id(),p.payer(),p.currency(),0,p.kind(),now,false));
                store.saveChecked(registry);
            }catch(IllegalArgumentException|ArithmeticException rejection) {
                rollback(town,previous,state,plots,buffs,added,free);return DurablePayments.Applied.REJECT;
            }catch(IOException|RuntimeException error) {
                rollback(town,previous,state,plots,buffs,added,free);throw error;
            }
                String townId=town.id(),townName=town.name();ChunkKey claim=added;
                try { Threads.global(plugin,()->{
                    if(p.kind().equals("found")) plugin.events().fireFounded(p.payer(),townId,townName);
                    if(claim!=null) plugin.events().fireClaim(townId,townName,p.payer(),claim);
                    if(p.kind().equals("buff")) plugin.events().fireBuffActivate(townId,p.payload().get("buff"));
                }); } catch(RuntimeException unavailable) {
                    plugin.getLogger().warning("交易已落盘，事件通知未调度："+p.id());
                }
            return DurablePayments.Applied.YES;
        }
    }
    private void rollback(Town town,CivicState previous,TownState state,Map<String,Plot> plots,Map<String,Long> buffs,ChunkKey added,int free) {
        town.civic(previous);town.state(state);town.plots().clear();town.plots().putAll(plots);town.buffs().clear();town.buffs().putAll(buffs);
        if(added!=null && town.claims().remove(added)) registry.unindexClaim(added);
        town.freeClaimsRemaining(free);
    }
    private void validateFound(Town town,UUID actor) {
        if(town.state()!=TownState.PENDING) fail("already-active");
        if(!town.mayor().equals(actor)) fail("transfer-must-mayor");
        if(town.members().size()<plugin.config().minMembers()) fail("found-min-members");
    }
    private void validateClaim(Town town,UUID actor,ChunkKey key) {
        manager(town,actor);
        if(!town.isHallSet()) fail("hall-not-set");
        if(!plugin.service().worldAllowed(key.world())) fail("claim-world-denied");
        if(registry.byChunk(key)!=null) fail("claim-protected-other");
        if(town.claims().stream().noneMatch(c->c.isAdjacentTo(key,plugin.config().diagonalConnected()))) fail("claim-adjacent-required");
        int max=claimLimit(town);if(max>0 && town.claims().size()>=max) fail("claim-max-reached");
    }
    private void validatePolicy(Town town,String id,long now) {
        boolean renewal=town.civic().policies().getOrDefault(id,new CivicState.Policy(id,0)).expiresAt()>now;
        if(!renewal && catalog.active(town.civic(),now).size()>=catalog.slots(town.civic())) fail("civic-slots-full");
    }
    private void validateRent(Plot plot,UUID actor,long now) {
        if(plot==null) fail("plot-not-found");
        if(plugin.relocation()!=null && plugin.relocation().locks().intersects(plot.bounds())) fail("move-area-locked");
        if(plot.owner().equals(actor)) fail("plot-rent-self");
        if(plot.kind()==PlotKind.PUBLIC || plot.offeredDurationMillis()<60_000 || plot.offeredCurrency().isBlank()) fail("plot-not-offered");
        if(plot.lease()!=null && plot.lease().active(now)) fail("plot-already-leased");
    }
    private TownConfig.BuffDef buff(String id) {return plugin.config().buffs().stream().filter(d->d.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("buff-not-found"));}
    private Town member(UUID actor) {Town t=registry.byMember(actor);if(t==null) fail("not-in-town");return t;}
    private void manager(Town town,UUID actor) {
        if(town.state()!=TownState.ACTIVE) fail("must-be-active");
        Role role=town.members().get(actor);if(role==null || !role.canManage()) fail("plot-manager-required");
    }
    private static void fail(String key) {throw new IllegalArgumentException(key);}
    private void failure(Consumer<TownService.Result> feedback,IllegalArgumentException error) {
        String key=error.getMessage();
        if(key==null || !key.matches("[a-z][a-z-]+")) key="civic-amount-invalid";
        feedback.accept(TownService.Result.fail(key,Placeholder.unparsed("min",String.valueOf(plugin.config().minMembers())),
                Placeholder.unparsed("count",""),Placeholder.unparsed("max",String.valueOf(plugin.config().maxChunksPerTown()))));
    }
}
