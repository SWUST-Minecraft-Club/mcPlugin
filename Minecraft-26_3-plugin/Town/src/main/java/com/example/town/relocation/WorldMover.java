package com.example.town.relocation;

import com.example.town.TownPlugin;
import com.example.town.hook.CraftEngineHook;
import com.example.town.model.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.*;

/** 全部世界读写按区块进入 Folia 区域线程，每 tick 最多处理配置数量的方块。 */
public final class WorldMover {
    private final TownPlugin plugin;
    private final CraftEngineHook craft;
    private final BukkitBlocks blocks;
    private final Map<String,MoveTransform> furnitureWrites=new ConcurrentHashMap<>();
    public boolean internalFurniture(Location at) {return furnitureWrites.values().stream().anyMatch(m->m.source().world().equals(at.getWorld().getName())
            && (m.source().contains(at.getBlockX(),at.getBlockY(),at.getBlockZ()) || m.target().contains(at.getBlockX(),at.getBlockY(),at.getBlockZ())));}
    public WorldMover(TownPlugin plugin,CraftEngineHook craft) {this.plugin=plugin;this.craft=craft;blocks=new BukkitBlocks(craft);}
    public BukkitBlocks blocks() {return blocks;}
    public CompletableFuture<MoveSnapshot> scan(MoveTransform move,boolean preflight) {
        Map<MoveTransform.Point,BlockImage> images=new ConcurrentHashMap<>();
        Map<UUID,MoveSnapshot.Furniture> furniture=new ConcurrentHashMap<>();
        try { return each(move, (world,chunk,points)->{
            if(preflight) for(Entity entity:chunk.getEntities()) {
                var box=entity.getBoundingBox();
                boolean source=overlap(move.source(),box),target=overlap(move.target(),box);
                if(!source && !target) continue;
                if(craft==null || !craft.furnitureEntity(entity)) throw new IllegalArgumentException("move-entity-obstruction");
                if(!source || !contains(move.source(),box) || craft.furnitureBounds(entity).stream().anyMatch(b->!contains(move.source(),b))) throw new IllegalArgumentException("move-furniture-partial");
                var image=craft.snapshotFurniture(entity);
                if(image!=null) furniture.put(image.original(),image);
            }
        }, (world,p)->{
            Block block=world.getBlockAt(p.x(),p.y(),p.z());
            boolean inside=move.source().contains(p.x(),p.y(),p.z());
            if(preflight && MoveRules.collision(move.source(),p.x(),p.y(),p.z(),!block.getType().isAir()))
                throw new IllegalArgumentException("move-collision:"+p.x()+","+p.y()+","+p.z());
            if(preflight && inside) validateBlock(block,move.source());
            BlockImage image=blocks.capture(block);
            if(preflight && inside && image.customState().length>0) blocks.preview(image,move.quarterTurns());
            if(!image.air()) images.put(p,image);
        }).thenApply(ignored->new MoveSnapshot(move.source(),move.target(),images,new ArrayList<>(furniture.values())));
        } catch(Throwable error) {return CompletableFuture.failedFuture(error);}
    }
    /** 写整个并集的最终结果；源区清空与目标放置不会互相覆盖。重放写入具有幂等性。 */
    public CompletableFuture<Void> write(MoveJob job,MoveSnapshot snapshot,boolean rollback,IntConsumer progress) {
        MoveTransform move=job.transform();var processed=new java.util.concurrent.atomic.AtomicInteger();
        return each(move,(w,c,p)->{},(world,p)->{
            BlockImage image=snapshot.finalImage(move,p,rollback);
            int turns=!rollback && move.target().contains(p.x(),p.y(),p.z())?move.quarterTurns():0;
            blocks.restore(world.getBlockAt(p.x(),p.y(),p.z()),image,turns);
            int count=processed.incrementAndGet();if(count%512==0) progress.accept(count);
        }).thenCompose(ignored->moveFurniture(job,snapshot,rollback));
    }
    private CompletableFuture<Void> moveFurniture(MoveJob job,MoveSnapshot snapshot,boolean rollback) {
        CompletableFuture<Void> result=CompletableFuture.completedFuture(null);
        for(var saved:snapshot.furniture()) result=result.thenCompose(ignored->{
            if(craft==null) return CompletableFuture.failedFuture(new IllegalStateException("恢复家具需要 CraftEngine"));
            Entity entity=Bukkit.getEntity(saved.original());
            if(entity==null) return CompletableFuture.failedFuture(new IllegalStateException("家具实体缺失，保留工程快照："+saved.original()));
            CompletableFuture<Void> done=new CompletableFuture<>();
            entity.getScheduler().run(plugin,task->{
                Location target=furnitureLocation(job.transform(),saved,rollback);
                craft.moveFurniture(entity,target).whenComplete((ok,error)->{
                    if(error!=null) done.completeExceptionally(error);
                    else if(!Boolean.TRUE.equals(ok)) done.completeExceptionally(new IllegalStateException("家具移动被拒绝"));
                    else done.complete(null);
                });
            },()->done.completeExceptionally(new IllegalStateException("家具在施工中移除")));
            return done;
        });
        furnitureWrites.put(job.id(),job.transform());
        return result.whenComplete((ignored,error)->furnitureWrites.remove(job.id()));
    }
    static Location furnitureLocation(MoveTransform m,MoveSnapshot.Furniture f,boolean rollback) {
        World world=Bukkit.getWorld(m.source().world());
        if(rollback) return new Location(world,f.x(),f.y(),f.z(),f.yaw(),f.pitch());
        double x=f.x()-m.source().minX(),z=f.z()-m.source().minZ();
        double tx=x,tz=z;
        switch(m.quarterTurns()) {
            case 1->{tx=m.source().depth()-z;tz=x;}
            case 2->{tx=m.source().width()-x;tz=m.source().depth()-z;}
            case 3->{tx=z;tz=m.source().width()-x;}
        }
        return new Location(world,m.targetX()+tx,m.targetY()+f.y()-m.source().minY(),m.targetZ()+tz,f.yaw()+m.quarterTurns()*90,f.pitch());
    }
    public CompletableFuture<Integer> ground(MoveTransform move) {
        World world=world(move);
        CompletableFuture<Integer> result=new CompletableFuture<>();
        world.getChunkAtAsync(move.targetX()>>4,move.targetZ()>>4).thenAccept(chunk->
                Bukkit.getRegionScheduler().execute(plugin,world,chunk.getX(),chunk.getZ(),()->{
                    try {result.complete(world.getHighestBlockYAt(move.targetX(),move.targetZ())+1);}
                    catch(Throwable error) {result.completeExceptionally(error);}
                })).exceptionally(error->{result.completeExceptionally(error);return null;});
        return result;
    }
    @FunctionalInterface private interface ChunkStart {void accept(World world,Chunk chunk,List<MoveTransform.Point> points);}
    @FunctionalInterface private interface Cell {void accept(World world,MoveTransform.Point point);}
    private CompletableFuture<Void> each(MoveTransform move,ChunkStart start,Cell action) {
        World world=world(move);Map<ChunkKey,List<MoveTransform.Point>> groups=cells(move);
        // 顺序处理区块，避免大工程同时向多个区域灌入全部工作。
        CompletableFuture<Void> sequence=CompletableFuture.completedFuture(null);
        for(var entry:groups.entrySet()) sequence=sequence.thenCompose(ignored->{
            CompletableFuture<Void> done=new CompletableFuture<>();ChunkKey key=entry.getKey();
            world.getChunkAtAsync(key.x(),key.z()).thenAccept(chunk->{
                int[] index={0};boolean[] begun={false};
                Bukkit.getRegionScheduler().runAtFixedRate(plugin,world,key.x(),key.z(),task->{
                    try {
                        if(!begun[0]) {start.accept(world,chunk,entry.getValue());begun[0]=true;}
                        int end=Math.min(index[0]+Math.clamp(plugin.getConfig().getInt("relocation.blocks-per-tick",256),16,2048),entry.getValue().size());
                        while(index[0]<end) action.accept(world,entry.getValue().get(index[0]++));
                        if(index[0]>=entry.getValue().size()) {task.cancel();done.complete(null);}
                    }catch(Throwable error) {task.cancel();done.completeExceptionally(error);}
                },1,1);
            }).exceptionally(error->{done.completeExceptionally(error);return null;});
            return done;
        });
        return sequence;
    }
    public static Map<ChunkKey,List<MoveTransform.Point>> cells(MoveTransform move) {
        Map<ChunkKey,List<MoveTransform.Point>> groups=new LinkedHashMap<>();
        BlockBox source=move.source(),target=move.target();
        for(BlockBox box:List.of(source,target)) for(int x=box.minX();x<=box.maxX();x++)
            for(int z=box.minZ();z<=box.maxZ();z++) for(int y=box.minY();y<=box.maxY();y++) {
                if(box==target && source.contains(x,y,z)) continue;
                var p=new MoveTransform.Point(x,y,z);ChunkKey key=new ChunkKey(box.world(),x>>4,z>>4);
                groups.computeIfAbsent(key,ignored->new ArrayList<>()).add(p);
            }
        return groups;
    }
    private World world(MoveTransform move) {
        World world=Bukkit.getWorld(move.source().world());if(world==null) throw new IllegalArgumentException("move-world-unavailable");
        for(BlockBox box:List.of(move.source(),move.target())) {
            if(box.minY()<world.getMinHeight() || box.maxY()>=world.getMaxHeight()) throw new IllegalArgumentException("move-height-invalid");
            if(!world.getWorldBorder().isInside(new Location(world,box.minX(),box.minY(),box.minZ()))
                    || !world.getWorldBorder().isInside(new Location(world,box.maxX()+.99,box.maxY(),box.maxZ()+.99))) throw new IllegalArgumentException("move-border-invalid");
        }
        return world;
    }
    private void validateBlock(Block block,BlockBox source) {
        String type=block.getType().name();
        if(type.contains("PORTAL") || type.equals("MOVING_PISTON") || type.equals("PISTON_HEAD")) throw new IllegalArgumentException("move-unsupported-block:"+type);
        var data=block.getBlockData();
        if(data instanceof Bisected b && (type.endsWith("DOOR") || type.endsWith("TALL_GRASS") || type.endsWith("SUNFLOWER")
                || type.endsWith("LILAC") || type.endsWith("ROSE_BUSH") || type.endsWith("PEONY"))) {
            int otherY=block.getY()+(b.getHalf()==Bisected.Half.BOTTOM?1:-1);
            if(!source.contains(block.getX(),otherY,block.getZ())) throw new IllegalArgumentException("move-multiblock-partial");
        }
        if(data instanceof Bed bed) {
            BlockFace face=bed.getPart()==Bed.Part.FOOT?bed.getFacing():bed.getFacing().getOppositeFace();
            if(!source.contains(block.getX()+face.getModX(),block.getY(),block.getZ()+face.getModZ())) throw new IllegalArgumentException("move-multiblock-partial");
        }
        if(data instanceof Chest chest && chest.getType()!=Chest.Type.SINGLE) {
            BlockFace face=chest.getFacing();int dx=-face.getModZ(),dz=face.getModX();
            if(chest.getType()==Chest.Type.RIGHT) {dx=-dx;dz=-dz;}
            if(!source.contains(block.getX()+dx,block.getY(),block.getZ()+dz)) throw new IllegalArgumentException("move-multiblock-partial");
        }
    }
    private static boolean overlap(BlockBox b,org.bukkit.util.BoundingBox e) {
        return e.getMaxX()>b.minX() && e.getMinX()<b.maxX()+1 && e.getMaxY()>b.minY() && e.getMinY()<b.maxY()+1
                && e.getMaxZ()>b.minZ() && e.getMinZ()<b.maxZ()+1;
    }
    private static boolean contains(BlockBox b,org.bukkit.util.BoundingBox e) {
        return e.getMinX()>=b.minX() && e.getMaxX()<=b.maxX()+1 && e.getMinY()>=b.minY() && e.getMaxY()<=b.maxY()+1
                && e.getMinZ()>=b.minZ() && e.getMaxZ()<=b.maxZ()+1;
    }
}
