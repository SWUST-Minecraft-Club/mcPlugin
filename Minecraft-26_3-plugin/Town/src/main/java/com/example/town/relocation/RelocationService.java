package com.example.town.relocation;

import com.example.town.*;
import com.example.town.economy.*;
import com.example.town.model.*;
import com.example.town.util.Threads;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.*;
import org.bukkit.entity.Player;

/** 三维草案 → 审批 → 快照/支付 → 施工 → 验收。异步任务不持有世界或背包对象。 */
public final class RelocationService {
    public record Draft(String token,UUID actor,String town,MoveTransform move,String plot,long revision,boolean hall,
                        Set<UUID> required,Set<UUID> approved,long expiresAt,MoveSnapshot preview,String problem,boolean scanning) {
        public Draft {required=Set.copyOf(required);approved=Set.copyOf(approved);}
        public boolean ready() {return !scanning && problem.isBlank() && preview!=null && approved.containsAll(required);}
    }
    private final TownPlugin plugin;
    private final MoveJobs jobs;
    private final AreaLocks locks=new AreaLocks();
    private final WorldMover world;
    private final Map<UUID,Draft> drafts=new ConcurrentHashMap<>();
    private final Set<String> working=ConcurrentHashMap.newKeySet();
    private final Set<UUID> preparing=ConcurrentHashMap.newKeySet();
    private final Map<UUID,Map<MoveTransform.Point,org.bukkit.block.data.BlockData>> ghosts=new ConcurrentHashMap<>();
    private final Set<String> refreshed=ConcurrentHashMap.newKeySet();
    public RelocationService(TownPlugin plugin) throws IOException {
        this.plugin=plugin;world=new WorldMover(plugin,plugin.craftEngine());
        jobs=new MoveJobs(plugin.getDataFolder().toPath().resolve("relocations"));
        for(MoveJob job:jobs.all()) if(!job.terminal() && !locks.acquire(reservation(job))) throw new IOException("施工日志的选区互相重叠");
        plugin.civic().payments().handler("move",this::paid);
    }
    public AreaLocks locks() {return locks;}
    public boolean internalFurniture(Location at) {return world.internalFurniture(at);}
    public Draft draft(UUID actor) {return drafts.get(actor);}
    public List<MoveJob> jobs(UUID actor) {return jobs.all().stream().filter(j->j.actor().equals(actor)).toList();}
    public List<Draft> approvals(UUID actor) {return drafts.values().stream().filter(d->d.required().contains(actor) && !d.approved().contains(actor)).toList();}
    public void begin(Player player,int x,int y,int z,String plot,boolean hall) {
        if(preparing.contains(player.getUniqueId())) fail("move-busy");
        BlockBox source=plugin.selections().box(player.getUniqueId());
        long maximum=Math.clamp(plugin.getConfig().getLong("relocation.max-volume",65536),1,262144);
        if(source.volume()>maximum) fail("move-too-large");
        var view=plugin.service().townAt(new ChunkKey(source.world(),source.minX()>>4,source.minZ()>>4));
        if(view==null) fail("move-outside-town");
        replace(player,view.id(),new MoveTransform(source,x,y,z,0),plot==null?"":plot,hall);
    }
    public void adjust(Player player,int x,int y,int z,boolean rotate) {
        Draft old=require(player.getUniqueId());
        if(preparing.contains(old.actor())) fail("move-busy");
        replace(player,old.town(),rotate?old.move().rotate():old.move().adjust(x,y,z),old.plot(),old.hall());
    }
    public void ground(Player player) {
        Draft old=require(player.getUniqueId());
        world.ground(old.move()).whenComplete((height,error)->Threads.forPlayer(plugin,player,()->{
            if(drafts.get(old.actor())!=old) return;
            if(error!=null) {tell(player,"贴地失败："+describe(reason(error)));return;}
            adjust(player,0,height-old.move().targetY(),0,false);
        }));
    }
    private void replace(Player player,String townId,MoveTransform move,String plotId,boolean hall) {
        if(move.source().equals(move.target()) && move.quarterTurns()==0) fail("move-no-change");
        clearGhost(player);var view=plugin.registry().snapshot(townId);
        if(view==null) fail("not-in-town");
        Set<UUID> consents=Set.of();String problem="";
        try {consents=MoveRules.validate(view,player.getUniqueId(),move,plotId,hall,plugin.config().diagonalConnected(),System.currentTimeMillis());}
        catch(IllegalArgumentException error) {problem=reason(error);}
        Plot plot=view.plots().get(plotId);
        Draft next=new Draft(UUID.randomUUID().toString(),player.getUniqueId(),townId,move,plotId,plot==null?0:plot.revision(),hall,
                consents,Set.of(),System.currentTimeMillis()+Math.clamp(plugin.getConfig().getLong("relocation.draft-seconds",600),60,3600)*1000,
                null,problem,problem.isBlank());
        drafts.put(next.actor(),next);render(player);
        if(!problem.isBlank()) {tell(player,"方案暂不可施工："+describe(problem));return;}
        tell(player,"正在检查选区与碰撞…");
        world.scan(move,true).whenComplete((snapshot,error)->Threads.forPlayer(plugin,player,()->{
            Draft done=new Draft(next.token(),next.actor(),next.town(),next.move(),next.plot(),next.revision(),next.hall(),next.required(),
                    next.approved(),next.expiresAt(),snapshot,error==null?"":reason(error),false);
            if(!drafts.replace(next.actor(),next,done)) return;
            if(error!=null) {tell(player,"方案暂不可施工："+describe(done.problem()));return;}
            showGhost(player,done);status(player);
            for(UUID id:done.required()) {
                Player approver=Bukkit.getPlayer(id);
                if(approver!=null) Threads.forPlayer(plugin,approver,()->approver.sendMessage(Component.text("[小镇] "+player.getName()+" 请求迁移建筑，点击查看审批。")
                        .clickEvent(ClickEvent.runCommand("/town move inspect "+done.token()))));
            }
        }));
    }
    public Draft inspect(String token) {
        return drafts.values().stream().filter(d->d.token().equals(token) && d.expiresAt()>System.currentTimeMillis()).findFirst().orElseThrow(()->new IllegalArgumentException("move-draft-expired"));
    }
    public void approve(Player player,String token) {
        Draft old=inspect(token);
        if(!old.required().contains(player.getUniqueId()) || old.scanning() || !old.problem().isBlank()) fail("plot-consent-denied");
        Set<UUID> approved=new HashSet<>(old.approved());approved.add(player.getUniqueId());
        Draft next=new Draft(old.token(),old.actor(),old.town(),old.move(),old.plot(),old.revision(),old.hall(),old.required(),approved,old.expiresAt(),old.preview(),old.problem(),false);
        if(!drafts.replace(old.actor(),old,next)) fail("action-state-changed");
        tell(player,"已同意当前坐标与朝向的方案；方案调整后需要重新同意。");
        Player proposer=Bukkit.getPlayer(old.actor());if(proposer!=null) Threads.forPlayer(plugin,proposer,()->status(proposer));
    }
    public long price(Draft draft) {
        long count=draft.preview()==null?draft.move().source().volume():draft.preview().blocks().entrySet().stream()
                .filter(e->draft.move().source().contains(e.getKey().x(),e.getKey().y(),e.getKey().z()) && !e.getValue().air()).count();
        long base=Math.addExact(Money.cents(plugin.getConfig().getDouble("relocation.base-price",20)),Math.multiplyExact(count,Money.cents(plugin.getConfig().getDouble("relocation.price-per-block",.05))));
        var town=plugin.registry().snapshot(draft.town());
        double discount=town==null?0:CivicEffects.moveDiscount(plugin.civic().catalog(),town,draft.actor(),System.currentTimeMillis());
        if(draft.hall()) base=Math.addExact(base,Money.cents(plugin.getConfig().getDouble("relocation.hall-surcharge",100)));
        return java.math.BigDecimal.valueOf(base).multiply(java.math.BigDecimal.valueOf(1-discount)).setScale(0,java.math.RoundingMode.HALF_UP).longValueExact();
    }
    public void confirm(Player player,String token,long expectedCents) {
        Draft draft=require(player.getUniqueId());
        if(!draft.token().equals(token) || !draft.ready() || price(draft)!=expectedCents) fail("move-quote-changed");
        if(plugin.civic().payments().busy(draft.actor()) || !preparing.add(draft.actor())) fail("action-busy");
        String id=UUID.randomUUID().toString();
        var reservation=new AreaLocks.Reservation(id,draft.town(),draft.move().source(),draft.move().target());
        try(var ignored=plugin.registry().guard()) {
            validate(draft);
            if(!locks.acquire(reservation)) fail("move-area-locked");
        }catch(RuntimeException error) {preparing.remove(draft.actor());throw error;}
        clearGhost(player);tell(player,"已锁定施工区域，正在保存最终快照。请离开选区。");
        world.scan(draft.move(),true).whenComplete((snapshot,error)->Threads.async(plugin,()->{
            boolean saved=false;
            try {
                if(error!=null) throw new CompletionException(error);
                try(var ignored=plugin.registry().guard()) {
                    validate(draft);
                    if(!snapshot.blocks().equals(draft.preview().blocks()) || !snapshot.furniture().equals(draft.preview().furniture())) fail("move-quote-changed");
                    String currency=plugin.civic().currency();if(expectedCents>0 && currency.isBlank()) fail("wallet-missing");
                    Town town=plugin.registry().byId(draft.town());
                    MoveJob job=new MoveJob(id,draft.town(),draft.actor(),player.getName(),draft.move(),draft.plot(),draft.revision(),draft.hall(),
                            draft.hall()?new MoveTransform.Point(town.hallX(),town.hallY(),town.hallZ()):null,town.spawnYaw(),town.spawnPitch(),currency,expectedCents,
                            System.currentTimeMillis(),MoveJob.Stage.PREPARED,"");
                    snapshot.save(jobs.snapshot(id));jobs.save(job);saved=true;
                    plugin.civic().payments().submit(job.payment(),null);
                }
                drafts.remove(draft.actor(),draft);notifyActor(draft.actor(),"工程已登记："+id+"。支付确认后开始施工。");
            }catch(Throwable failure) {
                if(!saved) locks.release(id);
                notifyActor(draft.actor(),"工程等待处理："+describe(reason(failure)));
            }finally {preparing.remove(draft.actor());}
        }));
    }
    private void validate(Draft draft) {
        if(plugin.market()!=null) plugin.market().validateMove(draft.move());
        var town=plugin.registry().snapshot(draft.town());if(town==null) fail("not-in-town");
        Set<UUID> required=MoveRules.validate(town,draft.actor(),draft.move(),draft.plot(),draft.hall(),plugin.config().diagonalConnected(),System.currentTimeMillis());
        if(!required.equals(draft.required()) || !draft.approved().containsAll(required)) fail("plot-consent-pending");
        if(!draft.plot().isBlank() && (town.plots().get(draft.plot())==null || town.plots().get(draft.plot()).revision()!=draft.revision())) fail("action-state-changed");
    }
    private DurablePayments.Applied paid(Payment payment) throws IOException {
        MoveJob job=jobs.get(payment.id());if(job==null) return DurablePayments.Applied.WAIT;
        return switch(job.stage()) {
            case PREPARED->{jobs.save(job.at(MoveJob.Stage.APPLYING));drive(job.id());yield DurablePayments.Applied.WAIT;}
            case APPLYING->{if(job.error().isBlank()) drive(job.id());yield DurablePayments.Applied.WAIT;}
            case REVIEW,ACCEPTING,ACCEPTED->DurablePayments.Applied.YES;
            case ROLLED_BACK,CANCELLED->DurablePayments.Applied.REJECT;
            default->DurablePayments.Applied.WAIT;
        };
    }
    public void recover() {
        for(MoveJob job:jobs.all()) {
            if(job.stage()==MoveJob.Stage.ROLLED_BACK) {
                Payment p=plugin.civic().payments().get(job.id());
                if(p!=null && (p.stage()==Payment.Stage.PAID || p.stage()==Payment.Stage.APPLIED || p.stage()==Payment.Stage.DONE))
                    Threads.async(plugin,()->{try {plugin.civic().payments().refundAfterRollback(p.id());}catch(IOException e){failed(job,e);}});
                continue;
            }
            if(job.terminal()) continue;
            Payment payment=plugin.civic().payments().get(job.id());
            if(job.stage()==MoveJob.Stage.PREPARED) {
                if(payment==null && working.add(job.id())) Threads.async(plugin,()->{
                    try {plugin.civic().payments().submit(job.payment(),null);}catch(IOException|RuntimeException e){failed(job,e);}
                    finally {working.remove(job.id());}
                });
                else if(payment!=null && (payment.stage()==Payment.Stage.DECLINED || payment.stage()==Payment.Stage.REFUNDED)) Threads.async(plugin,()->{
                    try {jobs.save(job.at(MoveJob.Stage.CANCELLED));locks.release(job.id());notifyActor(job.actor(),"工程付款未完成，已解除施工锁。");}
                    catch(IOException e){failed(job,e);}
                });
            }else if(job.error().isBlank() && (job.stage()!=MoveJob.Stage.REVIEW || refreshed.add(job.id()))) drive(job.id());
        }
    }
    public void accept(Player player,String id) {
        MoveJob job=owned(player,id);if(job.stage()!=MoveJob.Stage.REVIEW) fail("move-not-reviewing");
        asyncTransition(job,MoveJob.Stage.ACCEPTING);
    }
    /** 验收人可以走入完工区观察；容器与建造仍由施工锁保护。 */
    public boolean mayInspect(UUID actor,Location at) {
        return jobs(actor).stream().anyMatch(j->j.stage()==MoveJob.Stage.REVIEW && j.error().isBlank()
                && j.transform().source().world().equals(at.getWorld().getName())
                && (j.transform().source().contains(at.getBlockX(),at.getBlockY(),at.getBlockZ())
                || j.transform().target().contains(at.getBlockX(),at.getBlockY(),at.getBlockZ())));
    }
    public void undo(Player player,String id) {
        MoveJob job=owned(player,id);if(job.stage()!=MoveJob.Stage.REVIEW && job.stage()!=MoveJob.Stage.APPLYING) fail("move-not-reviewing");
        asyncTransition(job,MoveJob.Stage.ROLLING_BACK);
    }
    public void retry(Player player,String id) {
        MoveJob job=owned(player,id);if(job.terminal()) fail("action-state-changed");
        asyncTransition(job,job.stage());
    }
    private MoveJob owned(Player player,String id) {
        MoveJob job=jobs.get(id);if(job==null || (!job.actor().equals(player.getUniqueId()) && !player.hasPermission("town.admin"))) fail("move-job-denied");return job;
    }
    private void asyncTransition(MoveJob job,MoveJob.Stage next) {
        if(!working.add(job.id())) fail("move-busy");
        Threads.async(plugin,()->{
            try {if(jobs.get(job.id())!=job) fail("action-state-changed");jobs.save(job.at(next));}
            catch(Throwable error) {notifyActor(job.actor(),describe(reason(error)));}
            finally {working.remove(job.id());drive(job.id());}
        });
    }
    private void drive(String id) {
        if(!working.add(id)) return;
        Threads.async(plugin,()->{
            MoveJob job=jobs.get(id);
            try {
                if(job.stage()==MoveJob.Stage.ACCEPTING) {commit(job);working.remove(id);return;}
                if(job.stage()!=MoveJob.Stage.APPLYING && job.stage()!=MoveJob.Stage.ROLLING_BACK && job.stage()!=MoveJob.Stage.REVIEW) {working.remove(id);return;}
                MoveSnapshot snapshot=MoveSnapshot.load(jobs.snapshot(id));boolean rollback=job.stage()==MoveJob.Stage.ROLLING_BACK;
                world.write(job,snapshot,rollback,count->notifyProgress(job,count)).whenComplete((ignored,error)->Threads.async(plugin,()->{
                    try {
                        if(error!=null) throw new CompletionException(error);
                        jobs.save(job.at(rollback?MoveJob.Stage.ROLLED_BACK:MoveJob.Stage.REVIEW));
                        if(rollback) {locks.release(id);plugin.civic().payments().refundAfterRollback(id);notifyActor(job.actor(),"建筑已恢复原址，正在按原业务号退款。");}
                        else {refreshed.add(id);notifyActor(job.actor(),"施工完成，请验收。/town move accept "+id+" 或 /town move undo "+id);plugin.civic().payments().kick(id);}
                    }catch(Throwable failure) {failed(job,failure);}
                    finally {working.remove(id);}
                }));
            }catch(Throwable error) {failed(job,error);working.remove(id);}
        });
    }
    private void commit(MoveJob job) throws IOException {
        try(var ignored=plugin.registry().guard()) {
            if(plugin.market()!=null) plugin.market().relocate(job);
            Town town=plugin.registry().byId(job.town());if(town==null) throw new IOException("工程小镇已不存在");
            String operation="move:"+job.id();
            if(!town.civic().applied().contains(operation)) {
                Plot previous=job.plot().isBlank()?null:town.plots().get(job.plot());
                if(!job.plot().isBlank() && (previous==null || previous.revision()!=job.plotRevision())) throw new IOException("施工中的地块版本发生变化");
                var oldCivic=town.civic();var oldHall=new MoveTransform.Point(town.hallX(),town.hallY(),town.hallZ());
                float oldYaw=town.spawnYaw(),oldPitch=town.spawnPitch();
                try {
                    if(previous!=null) town.plots().put(previous.id(),previous.withBounds(job.transform().plot(previous.bounds())));
                    if(job.hall()) {
                        var point=job.transform().apply(job.hallBefore().x(),job.hallBefore().y(),job.hallBefore().z());
                        town.hall(job.transform().source().world(),point.x(),point.y(),point.z(),job.yaw()+job.transform().quarterTurns()*90,job.pitch());
                    }
                    town.civic(oldCivic.transact(operation,job.actor(),job.currency(),0,"建筑迁移验收",System.currentTimeMillis(),false));
                    plugin.store().saveChecked(plugin.registry());
                }catch(IOException|RuntimeException error) {
                    town.civic(oldCivic);if(previous!=null) town.plots().put(previous.id(),previous);
                    if(job.hall()) town.hall(job.transform().source().world(),oldHall.x(),oldHall.y(),oldHall.z(),oldYaw,oldPitch);throw error;
                }
            }
        }
        jobs.save(job.at(MoveJob.Stage.ACCEPTED));locks.release(job.id());notifyActor(job.actor(),"验收完成，建筑与关联地块/政厅坐标已更新。");
    }
    private void failed(MoveJob job,Throwable error) {
        String message=reason(error);
        try {MoveJob current=jobs.get(job.id());jobs.save(current.with(current.stage(),message));}
        catch(IOException failure) {plugin.getLogger().severe("工程日志不可写，保留施工锁："+job.id());}
        plugin.getLogger().warning("迁移工程 "+job.id()+" 等待恢复："+message);notifyActor(job.actor(),"工程已暂停并保留快照与施工锁："+describe(message)+"。可 /town move retry "+job.id());
    }
    public void cancel(Player player) {
        if(preparing.contains(player.getUniqueId())) fail("move-busy");
        clearGhost(player);drafts.remove(player.getUniqueId());tell(player,"已关闭迁移草案。");
    }
    public void start() {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,task->{
            recover();
            for(Player player:Bukkit.getOnlinePlayers()) if(drafts.containsKey(player.getUniqueId())) Threads.forPlayer(plugin,player,()->{
                Draft draft=drafts.get(player.getUniqueId());if(draft==null) return;
                if(draft.expiresAt()<System.currentTimeMillis()) {if(!preparing.contains(draft.actor())) cancel(player);return;}render(player);
            });
        },20,10);
    }
    public void status(Player player) {
        Draft d=require(player.getUniqueId());
        tell(player,"源区 "+box(d.move().source())+" → 目标 "+box(d.move().target())+"，旋转 "+d.move().quarterTurns()*90+"°");
        tell(player,d.scanning()?"正在检查…":!d.problem().isBlank()?"暂不可施工："+describe(d.problem()):"报价 "+Money.format(price(d))+" "+plugin.civic().currency()+"；待同意 "+d.required().stream().filter(id->!d.approved().contains(id)).count()+" 人。");
        if(d.ready()) player.sendMessage(Component.text("[确认施工]  [调整请用 /town move shift X Y Z；rotate；ground；cancel]")
                .clickEvent(ClickEvent.runCommand("/town move confirm "+d.token()+" "+price(d))));
    }
    private void render(Player player) {
        Draft d=drafts.get(player.getUniqueId());if(d==null || !player.getWorld().getName().equals(d.move().source().world())) return;
        outline(player,d.move().source(),Color.fromRGB(255,188,56));
        outline(player,d.move().target(),d.problem().isBlank()?Color.fromRGB(48,245,168):Color.fromRGB(255,74,74));
        player.sendActionBar(Component.text(d.scanning()?"正在测绘…":!d.problem().isBlank()?"不可施工："+describe(d.problem()):"目标 "+d.move().targetX()+" / "+d.move().targetY()+" / "+d.move().targetZ()+" · "+d.move().quarterTurns()*90+"° · /town move"));
    }
    private void showGhost(Player player,Draft d) {
        Map<MoveTransform.Point,org.bukkit.block.data.BlockData> restore=new HashMap<>();int count=0;
        for(var entry:d.preview().blocks().entrySet()) {
            var p=entry.getKey();if(!d.move().source().contains(p.x(),p.y(),p.z()) || entry.getValue().air()) continue;
            if(++count>Math.clamp(plugin.getConfig().getInt("relocation.preview-blocks",2048),64,8192)) break;
            var target=d.move().apply(p.x(),p.y(),p.z());
            restore.put(target,world.blocks().preview(d.preview().at(target.x(),target.y(),target.z()),0));
            player.sendBlockChange(new Location(player.getWorld(),target.x(),target.y(),target.z()),world.blocks().preview(entry.getValue(),d.move().quarterTurns()));
        }
        ghosts.put(player.getUniqueId(),restore);
    }
    private void clearGhost(Player player) {
        var previous=ghosts.remove(player.getUniqueId());if(previous==null) return;
        Draft d=drafts.get(player.getUniqueId());if(d==null) return;
        World w=Bukkit.getWorld(d.move().source().world());if(w==null) return;
        // 从各区域读取此刻的真实方块，避免把预览期间已经变化的旧外观发回客户端。
        previous.forEach((point,data)->Bukkit.getRegionScheduler().execute(plugin,w,point.x()>>4,point.z()>>4,()->{
            var actual=w.getBlockAt(point.x(),point.y(),point.z()).getBlockData();
            Threads.forPlayer(plugin,player,()->{
                var current=ghosts.get(player.getUniqueId());
                if(player.getWorld().equals(w) && (current==null || !current.containsKey(point)))
                    player.sendBlockChange(new Location(w,point.x(),point.y(),point.z()),actual);
            });
        }));
    }
    private void outline(Player player,BlockBox b,Color color) {
        var dust=new Particle.DustOptions(color,1.9f);double[] xs={b.minX(),b.maxX()+1d},ys={b.minY(),b.maxY()+1d},zs={b.minZ(),b.maxZ()+1d};
        for(int axis=0;axis<3;axis++) for(int a=0;a<2;a++) for(int c=0;c<2;c++) {
            double ax=axis==0?xs[0]:xs[a],bx=axis==0?xs[1]:xs[a];
            double ay=axis==1?ys[0]:ys[axis==0?a:c],by=axis==1?ys[1]:ys[axis==0?a:c];
            double az=axis==2?zs[0]:zs[c],bz=axis==2?zs[1]:zs[c];
            com.example.town.selection.BoundaryParticles.edge(player,ax,ay,az,bx,by,bz,dust);
        }
    }
    private Draft require(UUID actor) {
        Draft d=drafts.get(actor);if(d==null || d.expiresAt()<=System.currentTimeMillis()) fail("move-draft-expired");return d;
    }
    private void notifyProgress(MoveJob job,int count) {
        Player player=Bukkit.getPlayer(job.actor());if(player!=null) Threads.forPlayer(plugin,player,()->player.sendActionBar(Component.text("施工中 · 已处理 "+count+" 格 · 请勿进入施工区域")));
    }
    private void notifyActor(UUID actor,String message) {
        Player player=Bukkit.getPlayer(actor);if(player!=null) Threads.forPlayer(plugin,player,()->tell(player,message));
    }
    public static String box(BlockBox b) {return "("+b.minX()+","+b.minY()+","+b.minZ()+")—("+b.maxX()+","+b.maxY()+","+b.maxZ()+")";}
    public String describe(String message) {
        String key=message.split(":",2)[0],raw=plugin.config().messages().get(key);
        if(raw==null) return message;
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(raw))
                +(message.contains(":")?" · "+message.substring(message.indexOf(':')+1):"");
    }
    public static String reason(Throwable e) {
        while((e instanceof CompletionException || e instanceof java.lang.reflect.InvocationTargetException) && e.getCause()!=null) e=e.getCause();
        return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
    }
    private static AreaLocks.Reservation reservation(MoveJob job) {return new AreaLocks.Reservation(job.id(),job.town(),job.transform().source(),job.transform().target());}
    private static void tell(Player player,String text) {player.sendMessage(Component.text("[小镇] "+text));}
    private static void fail(String message) {throw new IllegalArgumentException(message);}
}
