package com.example.town.relocation;

import com.example.town.model.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 施工与验收期间保留源区和目标区。重启时先从日志恢复锁，再注册任何玩家入口。 */
public final class AreaLocks {
    public record Reservation(String id,String town,BlockBox source,BlockBox target) { }
    private final Map<String,Reservation> reservations=new ConcurrentHashMap<>();
    public synchronized boolean acquire(Reservation next) {
        Reservation current=reservations.get(next.id());
        if(current!=null) return current.equals(next);
        for(Reservation old:reservations.values()) if(old.source().overlaps(next.source()) || old.source().overlaps(next.target())
                || old.target().overlaps(next.source()) || old.target().overlaps(next.target())) return false;
        reservations.put(next.id(),next);return true;
    }
    public synchronized void release(String id) {reservations.remove(id);}
    public boolean townLocked(String town) {return reservations.values().stream().anyMatch(r->r.town().equals(town));}
    public boolean locked(String world,int x,int y,int z) {
        return reservations.values().stream().anyMatch(r->r.source().world().equals(world)
                && (r.source().contains(x,y,z) || r.target().contains(x,y,z)));
    }
    public boolean intersects(PlotBounds bounds) {
        return reservations.values().stream().anyMatch(r->r.source().footprint().overlaps(bounds) || r.target().footprint().overlaps(bounds));
    }
    public List<Reservation> all() {return List.copyOf(reservations.values());}
}
