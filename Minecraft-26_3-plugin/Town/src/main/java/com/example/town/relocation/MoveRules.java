package com.example.town.relocation;

import com.example.town.model.*;
import java.util.*;

public final class MoveRules {
    private MoveRules() { }
    /** 返回当前方案需要的同意人；目标或地块版本变动后必须重新计算。 */
    public static Set<UUID> validate(TownSnapshot town,UUID actor,MoveTransform move,String linkedPlot,
                                      boolean hallMove,boolean diagonal,long now) {
        if(town.state()!=TownState.ACTIVE || !town.hallSet()) fail("hall-not-set");
        if(!town.claims().containsAll(move.source().footprint().chunks(65536))
                || !town.claims().containsAll(move.target().footprint().chunks(65536))) fail("move-outside-town");
        boolean sourceHall=move.source().world().equals(town.hallWorld()) && move.source().contains(town.hallX(),town.hallY(),town.hallZ());
        boolean targetHall=move.source().world().equals(town.hallWorld()) && move.target().contains(town.hallX(),town.hallY(),town.hallZ());
        if(hallMove) {
            if(!town.mayor().equals(actor)) fail("hall-must-mayor");
            if(!sourceHall) fail("move-hall-selection");
            var hall=move.apply(town.hallX(),town.hallY(),town.hallZ());
            if(!ClaimTopology.connected(town.claims(),new ChunkKey(town.hallWorld(),hall.x()>>4,hall.z()>>4),diagonal)) fail("unclaim-disconnected");
        }else if(sourceHall || targetHall) fail("move-core-requires-service");
        Role role=town.members().get(actor);boolean manager=role!=null && role.canManage();
        Set<UUID> consents=new HashSet<>();Plot linked=null;
        if(linkedPlot!=null && !linkedPlot.isBlank()) {
            linked=town.plots().get(linkedPlot);if(linked==null) fail("plot-not-found");
            if(!move.source().footprint().contains(linked.bounds())) fail("move-plot-partial");
            PlotBounds target=move.plot(linked.bounds());
            if(linked.kind()!=PlotKind.PUBLIC && (hallMove || target.contains(town.hallWorld(),town.hallX(),town.hallZ()))) fail("plot-hall-conflict");
            for(Plot other:town.plots().values()) if(!other.id().equals(linked.id())
                    && (other.bounds().overlaps(target) || other.bounds().overlaps(move.source().footprint())
                    || other.bounds().overlaps(move.target().footprint()))) fail("plot-overlap");
            if(!linked.owner().equals(actor) && linked.kind()!=PlotKind.PUBLIC) consents.add(linked.owner());
            if(linked.lease()!=null && linked.lease().active(now) && !linked.lease().tenant().equals(actor)) consents.add(linked.lease().tenant());
            if(!manager) consents.add(town.mayor());
            if(linked.kind()==PlotKind.PUBLIC && !manager) fail("plot-manager-required");
        }
        if(linked==null) for(PlotBounds footprint:List.of(move.source().footprint(),move.target().footprint())) {
            long covered=0;
            for(Plot at:town.plots().values()) if(at.bounds().overlaps(footprint)) {
                if(!at.canUse(actor,now) && !(at.kind()==PlotKind.PUBLIC && manager)) fail("move-no-permission");
                long width=(long)Math.min(at.bounds().maxX(),footprint.maxX())-Math.max(at.bounds().minX(),footprint.minX())+1;
                long depth=(long)Math.min(at.bounds().maxZ(),footprint.maxZ())-Math.max(at.bounds().minZ(),footprint.minZ())+1;
                covered+=width*depth;
            }
            if(!manager && covered<footprint.area()) fail("move-no-permission");
        }
        return Set.copyOf(consents);
    }
    /** 必须先保存整份快照，再清源区；这样重叠部分不会被误当成外部障碍。 */
    public static boolean collision(BlockBox source,int x,int y,int z,boolean occupied) {
        return occupied && !source.contains(x,y,z);
    }
    private static void fail(String key) {throw new IllegalArgumentException(key);}
}
