package com.example.town.market;

import com.example.town.*;
import com.example.town.economy.*;
import com.example.town.model.*;
import com.example.town.relocation.*;
import com.example.town.util.Threads;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.entity.Player;

/** 商铺库存与订单物品使用托管收件箱交付，结算使用 Wallet 的原业务号恢复。 */
public final class MarketService {
    private final TownPlugin plugin;
    private final MarketBook book;
    private final Set<UUID> busy=ConcurrentHashMap.newKeySet();
    private final Set<String> reconciling=ConcurrentHashMap.newKeySet();
    private final Map<UUID,String> conflicts=new ConcurrentHashMap<>();
    public MarketService(TownPlugin plugin) throws IOException {
        this.plugin=plugin;book=new MarketBook(plugin.getDataFolder().toPath().resolve("market.json"));
        plugin.civic().payments().handler("market",this::paid);
    }
    public MarketBook book() {return book;}
    public boolean frozen(UUID actor) {return busy.contains(actor) || book.pending(actor)!=null;}
    public String conflict(UUID actor) {return conflicts.getOrDefault(actor,"");}
    public void disconnected(UUID actor) {busy.remove(actor);}
    public boolean hasAssets(String plot) {return book.state().listings().values().stream().anyMatch(l->l.plot().equals(plot) && l.status()!=MarketBook.Status.CLOSED);}
    public List<MarketBook.Listing> listings() {return book.state().listings().values().stream().filter(l->l.status()==MarketBook.Status.OPEN).sorted(Comparator.comparing(MarketBook.Listing::id)).toList();}
    public List<MarketBook.Mail> mail(UUID actor) {return book.state().mail().values().stream().filter(m->m.owner().equals(actor)).sorted(Comparator.comparing(MarketBook.Mail::id)).toList();}
    private String sample(Player p) {
        var item=p.getInventory().getItemInMainHand();if(item.isEmpty()) fail("请在主手拿着商品样品。");return InventoryImage.encode(item.asOne());
    }
    private MarketBook.Listing draft(Player p,int quantity,long unit,MarketBook.Kind kind) {
        if(quantity<1 || quantity>Math.clamp(plugin.getConfig().getInt("market.max-quantity",100000),1,1000000) || unit<=0) fail("数量或单价无效。");
        Math.multiplyExact(quantity,unit);
        Location at=p.getLocation();var town=plugin.service().townAt(ChunkKey.of(at));
        if(town==null || !town.active()) fail("请站在商业地块内创建商铺或订单。");
        Plot plot=PlotRules.at(town.raw(),at.getWorld().getName(),at.getBlockX(),at.getBlockZ());
        if(plot==null || plot.kind()!=PlotKind.COMMERCIAL || !plot.canUse(p.getUniqueId(),System.currentTimeMillis())) fail("需要你拥有或获授权的商业用地。");
        if(plugin.relocation().locks().intersects(plot.bounds())) fail("该地块正在施工。");
        long count=book.state().listings().values().stream().filter(l->l.owner().equals(p.getUniqueId()) && l.status()!=MarketBook.Status.CLOSED).count();
        if(count>=Math.clamp(plugin.getConfig().getInt("market.max-listings-per-player",32),1,1024)) fail("已达到商铺和订单数量上限。");
        String currency=plugin.civic().currency();if(currency.isBlank()) fail("钱包货币尚不可用。");
        return new MarketBook.Listing(UUID.randomUUID().toString(),town.id(),plot.id(),p.getUniqueId(),p.getName(),kind,sample(p),quantity,unit,currency,0,
                at.getWorld().getName(),at.getBlockX(),at.getBlockY(),at.getBlockZ(),MarketBook.Status.DRAFT);
    }
    public void stock(Player p,int quantity,long unit) {
        MarketBook.Listing l=draft(p,quantity,unit,MarketBook.Kind.STOCK);
        inventoryIntent(p,MarketBook.IntentKind.LIST,l.id(),l.item(),quantity,false,e->{locationValid(l);e.listings.put(l.id(),l);return null;});
    }
    public void order(Player p,int quantity,long unit) {
        MarketBook.Listing l=draft(p,quantity,unit,MarketBook.Kind.ORDER);
        Payment pay=Payment.create("market",p.getUniqueId(),p.getName(),l.currency(),Math.multiplyExact(unit,quantity),null,"",0,Map.of("town",l.town(),"action","order"));
        marketAction(p,e->{
            locationValid(l);
            e.listings.put(l.id(),l);e.trades.put(pay.id(),new MarketBook.Trade(pay.id(),"order",l.id(),quantity,p.getUniqueId(),l.item(),pay,MarketBook.TradeStage.RESERVED));return null;
        },"订单已登记，预付款确认后对外开放。",true);
    }
    /** 购买必须携带看到的版本和单价，库存变化后需要重新预览。 */
    public void buy(Player p,String id,int quantity,long revision,long unit) {
        buy(p,id,quantity,revision,unit,netQuote(listing(id),p.getUniqueId(),quantity));
    }
    public void buy(Player p,String id,int quantity,long revision,long unit,long expectedNet) {
        MarketBook.Listing l=listing(id);available(l);if(l.owner().equals(p.getUniqueId())) fail("不能购买自己的商品。");
        if(l.unitCents()!=unit) fail("单价已改变，请刷新。");
        long total=Math.multiplyExact(unit,quantity),net=checkNet(l,p.getUniqueId(),quantity,expectedNet);
        Payment pay=Payment.create("market",p.getUniqueId(),p.getName(),l.currency(),total,l.owner(),l.ownerName(),net,Map.of("town",l.town(),"action","buy"));
        marketAction(p,e->{available(l);checkNet(l,p.getUniqueId(),quantity,expectedNet);e.reserve(id,quantity,revision,MarketBook.Kind.STOCK);
            e.trades.put(pay.id(),new MarketBook.Trade(pay.id(),"buy",id,quantity,p.getUniqueId(),l.item(),pay,MarketBook.TradeStage.RESERVED));return null;
        },"商品已预留，结算后送入你的收件箱。",true);
    }
    public void supply(Player p,String id,int quantity,long revision,long unit) {
        supply(p,id,quantity,revision,unit,netQuote(listing(id),p.getUniqueId(),quantity));
    }
    public void supply(Player p,String id,int quantity,long revision,long unit,long expectedNet) {
        MarketBook.Listing l=listing(id);available(l);if(l.owner().equals(p.getUniqueId())) fail("不能供应自己的订单。");
        if(l.unitCents()!=unit) fail("单价已改变，请刷新。");
        Payment pay=Payment.create("market",p.getUniqueId(),p.getName(),l.currency(),0,p.getUniqueId(),p.getName(),checkNet(l,p.getUniqueId(),quantity,expectedNet),Map.of("town",l.town(),"action","supply"));
        inventoryIntent(p,MarketBook.IntentKind.SUPPLY,id,l.item(),quantity,false,e->{
            available(l);checkNet(l,p.getUniqueId(),quantity,expectedNet);
            e.reserve(id,quantity,revision,MarketBook.Kind.ORDER);
            e.trades.put(pay.id(),new MarketBook.Trade(pay.id(),"supply",id,quantity,l.owner(),l.item(),pay,MarketBook.TradeStage.RESERVED));return pay.id();
        });
    }
    private long checkNet(MarketBook.Listing listing,UUID trader,int quantity,long expectedNet) {
        if(quantity<1) fail("数量必须大于零。");
        long current=netQuote(listing,trader,quantity);
        if(current!=expectedNet) fail("手续费或政策已改变，请重新预览交易。");
        return current;
    }
    public void collect(Player p,String mail,int quantity) {
        MarketBook.Mail m=book.state().mail().get(mail);
        if(m==null || !m.owner().equals(p.getUniqueId()) || quantity<1 || quantity>m.quantity()) fail("收件数量已改变。");
        inventoryIntent(p,MarketBook.IntentKind.COLLECT,mail,m.item(),quantity,true,e->{
            MarketBook.Mail current=e.mail.get(mail);if(!m.equals(current)) fail("收件内容已改变。");return null;
        });
    }
    public void close(Player p,String id,long revision) {
        MarketBook.Listing l=listing(id);if(!l.owner().equals(p.getUniqueId())) fail("只能下架自己的商铺或订单。");
        if(plugin.relocation().locks().locked(l.world(),l.x(),l.y(),l.z())) fail("商铺正在迁移，请先验收工程。");
        marketAction(p,e->{
            var current=e.listings.get(id);
            if(current==null || current.status()!=MarketBook.Status.OPEN || current.revision()!=revision) fail("商品状态已改变。");
            if(e.trades.values().stream().anyMatch(t->t.listing().equals(id) && t.stage()==MarketBook.TradeStage.RESERVED)
                    || e.intents.values().stream().anyMatch(i->i.target().equals(id) && !i.done())) fail("仍有交易正在结算，请稍后下架。");
            if(current.kind()==MarketBook.Kind.STOCK) {
                if(current.remaining()>0) {String mid=UUID.randomUUID().toString();e.mail.put(mid,new MarketBook.Mail(mid,p.getUniqueId(),current.item(),current.remaining(),"商铺下架"));}
                e.listings.put(id,current.quantity(0).at(MarketBook.Status.CLOSED));
            }else {
                Payment pay=Payment.create("market",p.getUniqueId(),p.getName(),current.currency(),0,p.getUniqueId(),p.getName(),Math.multiplyExact(current.remaining(),current.unitCents()),Map.of("town",current.town(),"action","cancel"));
                e.listings.put(id,current.quantity(0).at(MarketBook.Status.CLOSED));
                e.trades.put(pay.id(),new MarketBook.Trade(pay.id(),"cancel",id,0,p.getUniqueId(),current.item(),pay,MarketBook.TradeStage.RESERVED));
            }return null;
        },"已下架。剩余商品进入收件箱，未使用的预付款会自动结算回钱包。",false);
    }
    private long net(MarketBook.Listing l,UUID trader,long total) {
        var town=plugin.registry().snapshot(l.town());
        double discount=town==null?0:plugin.civic().catalog().active(town.civic(),System.currentTimeMillis()).stream()
                .filter(d->CivicEffects.benefits(d.scope(),town,trader,System.currentTimeMillis())).mapToDouble(CivicCatalog.PolicyDef::tradeDiscount).sum();
        double rate=Math.clamp(plugin.getConfig().getDouble("civic.market-fee-percent",5),0,100)/100d*(1-Math.clamp(discount,0,plugin.civic().catalog().discountCap()));
        long fee=java.math.BigDecimal.valueOf(total).multiply(java.math.BigDecimal.valueOf(rate)).setScale(0,java.math.RoundingMode.DOWN).longValueExact();
        return total-fee;
    }
    public long netQuote(MarketBook.Listing l,UUID trader,int quantity) {return net(l,trader,Math.multiplyExact(quantity,l.unitCents()));}
    private MarketBook.Listing listing(String id) {var l=book.state().listings().get(id);if(l==null) fail("找不到这个商铺或订单。");return l;}
    private void available(MarketBook.Listing l) {
        if(l.status()!=MarketBook.Status.OPEN) fail("商品尚未开放交易。");
        locationValid(l);
    }
    private void locationValid(MarketBook.Listing l) {
        var town=plugin.registry().snapshot(l.town());Plot plot=town==null?null:town.plots().get(l.plot());
        if(plot==null || plot.kind()!=PlotKind.COMMERCIAL || !plot.bounds().contains(l.world(),l.x(),l.z()) || !plot.canUse(l.owner(),System.currentTimeMillis())) fail("经营者的商业地块授权已到期，暂停交易。");
        if(plugin.relocation().locks().locked(l.world(),l.x(),l.y(),l.z())) fail("商铺正在迁移，请稍后交易。");
    }
    private void marketAction(Player p,java.util.function.Function<MarketBook.Edit,Void> action,String message,boolean noPendingPayment) {
        UUID actor=p.getUniqueId();if(!busy.add(actor)) fail("上一笔操作仍在处理中。");
        if(book.pending(actor)!=null || (noPendingPayment && plugin.civic().payments().busy(actor))) {busy.remove(actor);fail("上一笔交易仍在处理中。");}
        Threads.async(plugin,()->{
            try {try(var ignored=plugin.registry().guard()) {book.update(action);}tell(p,message);recoverPayments();}
            catch(Exception e) {tell(p,"操作未完成："+RelocationService.reason(e));}
            finally {busy.remove(actor);}
        });
    }
    private void inventoryIntent(Player p,MarketBook.IntentKind kind,String target,String item,int quantity,boolean give,
                                 java.util.function.Function<MarketBook.Edit,String> prepare) {
        UUID actor=p.getUniqueId();if(!busy.add(actor)) fail("上一笔交付仍在处理中。");
        try {
            if(book.pending(actor)!=null || plugin.civic().payments().busy(actor)) fail("上一笔交易仍在处理中。");
            if(!p.getItemOnCursor().isEmpty()) fail("请先放下鼠标上的物品。");p.closeInventory();
            List<String> before=InventoryImage.capture(p.getInventory().getStorageContents());
            List<String> after=InventoryImage.change(before,item,quantity,give);
            Threads.async(plugin,()->{
                try {
                    MarketBook.Intent intent;
                    try(var ignored=plugin.registry().guard()) {intent=book.update(e->{
                        String trade=prepare.apply(e);String id=trade==null?UUID.randomUUID().toString():trade;
                        var next=new MarketBook.Intent(id,actor,kind,target,quantity,before,after,false);e.intent(next);return next;
                    });}
                    Threads.forPlayer(plugin,p,()->finishInventory(p,intent));
                }catch(Exception error) {busy.remove(actor);tell(p,"交付准备失败："+RelocationService.reason(error));}
            });
        }catch(RuntimeException error) {busy.remove(actor);throw error;}
    }
    private void finishInventory(Player p,MarketBook.Intent intent) {
        try {
            List<String> actual=InventoryImage.capture(p.getInventory().getStorageContents());
            if(actual.equals(intent.before())) p.getInventory().setStorageContents(InventoryImage.restore(intent.after()));
            else if(!actual.equals(intent.after())) {
                conflicts.put(p.getUniqueId(),"背包与交付前后快照不一致；已保留托管记录，等待管理员核对。");
                tell(p,conflict(p.getUniqueId()));busy.remove(p.getUniqueId());return;
            }
            // 先让 player.dat 记录交付结果，再消费收件或开放商品；重启按前/后快照恢复。
            p.saveData();
            Threads.async(plugin,()->{
                try {
                    book.update(e->{
                        MarketBook.Intent current=e.intents.get(intent.id());if(current==null || current.done()) return null;
                        if(intent.kind()==MarketBook.IntentKind.LIST) {var l=e.listings.get(intent.target());e.listings.put(l.id(),l.at(MarketBook.Status.OPEN));}
                        if(intent.kind()==MarketBook.IntentKind.COLLECT) {
                            var m=Objects.requireNonNull(e.mail.get(intent.target()));int remain=m.quantity()-intent.quantity();
                            if(remain<0) throw new IllegalStateException("收件数量不一致");
                            if(remain==0) e.mail.remove(m.id());else e.mail.put(m.id(),m.quantity(remain));
                        }
                        e.intents.put(intent.id(),current.complete());return null;
                    });
                    conflicts.remove(p.getUniqueId());tell(p,"物品交付已保存，可在商铺或收件箱查看。");recoverPayments();
                }catch(Exception error) {tell(p,"交付结果等待恢复，请保留当前背包："+RelocationService.reason(error));}
                finally {busy.remove(p.getUniqueId());}
            });
        }catch(RuntimeException error) {busy.remove(p.getUniqueId());tell(p,"背包交付已暂停："+RelocationService.reason(error));}
    }
    public void resume(Player p) {
        var intent=book.pending(p.getUniqueId());if(intent!=null && !conflicts.containsKey(p.getUniqueId()) && busy.add(p.getUniqueId())) finishInventory(p,intent);
    }
    /** 管理员只选择确切的前/后快照，先写 player.dat 后继续原意图，不增加新物品流水。 */
    public void resolve(Player admin,Player target,boolean after) {
        if(!admin.hasPermission("town.admin")) fail("需要管理员权限。");
        Threads.forPlayer(plugin,target,()->{
            var intent=book.pending(target.getUniqueId());if(intent==null || !busy.add(target.getUniqueId())) return;
            target.getInventory().setStorageContents(InventoryImage.restore(after?intent.after():intent.before()));
            conflicts.remove(target.getUniqueId());finishInventory(target,intent);
        });
    }
    private DurablePayments.Applied paid(Payment payment) throws IOException {
        MarketBook.Trade t=book.state().trades().get(payment.id());if(t==null) return DurablePayments.Applied.WAIT;
        if(t.stage()==MarketBook.TradeStage.RELEASED) return DurablePayments.Applied.REJECT;
        if(t.stage()==MarketBook.TradeStage.DELIVERED) return DurablePayments.Applied.YES;
        var intent=book.state().intents().get(t.id());if(intent!=null && !intent.done()) return DurablePayments.Applied.WAIT;
        return book.update(e->{e.deliver(e.trades.get(payment.id()));return DurablePayments.Applied.YES;});
    }
    public void recoverPayments() {
        for(var t:book.state().trades().values()) {
            if(t.stage()!=MarketBook.TradeStage.RESERVED || !reconciling.add(t.id())) continue;
            var intent=book.state().intents().get(t.id());if(intent!=null && !intent.done()) {reconciling.remove(t.id());continue;}
            Threads.async(plugin,()->{
                try {
                    Payment p=plugin.civic().payments().get(t.id());
                    if(p==null) {
                        if(!plugin.civic().payments().busy(t.payment().payer())) plugin.civic().payments().submit(t.payment(),null);
                    }else if(p.stage()==Payment.Stage.DECLINED || p.stage()==Payment.Stage.REFUNDED) book.update(e->{e.release(e.trades.get(t.id()));return null;});
                    else plugin.civic().payments().kick(t.id());
                }catch(Exception error) {plugin.getLogger().warning("商业结算等待恢复："+t.id()+" · "+RelocationService.reason(error));}
                finally {reconciling.remove(t.id());}
            });
        }
    }
    public void start() {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,task->{recoverPayments();
            for(Player p:Bukkit.getOnlinePlayers()) if(book.pending(p.getUniqueId())!=null) Threads.forPlayer(plugin,p,()->resume(p));
        },40,40);
    }
    public void validateMove(MoveTransform move) {
        for(var l:book.state().listings().values()) {
            if(l.status()==MarketBook.Status.CLOSED || !l.world().equals(move.source().world())) continue;
            if(!move.source().contains(l.x(),l.y(),l.z()) && !move.target().contains(l.x(),l.y(),l.z())) continue;
            if(book.state().trades().values().stream().anyMatch(t->t.listing().equals(l.id()) && t.stage()==MarketBook.TradeStage.RESERVED)
                    || book.state().intents().values().stream().anyMatch(i->i.target().equals(l.id()) && !i.done())) fail("商铺仍有物品交付或交易结算，请稍后迁移。");
        }
    }
    public void relocate(MoveJob job) throws IOException {
        if(book.state().moves().contains(job.id())) return;
        book.update(e->{
            if(!e.moves.add(job.id())) return null;
            for(var l:new ArrayList<>(e.listings.values())) if(l.status()!=MarketBook.Status.CLOSED && l.world().equals(job.transform().source().world())
                    && job.transform().source().contains(l.x(),l.y(),l.z())) {
                var at=job.transform().apply(l.x(),l.y(),l.z());e.listings.put(l.id(),l.moved(at.x(),at.y(),at.z()));
            }return null;
        });
    }
    private void tell(Player p,String message) {Threads.forPlayer(plugin,p,()->p.sendPlainMessage("[小镇] "+message));}
    private static void fail(String message) {throw new IllegalArgumentException(message);}
}
