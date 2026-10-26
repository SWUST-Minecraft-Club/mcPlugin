package com.example.town.relocation;

import static org.junit.jupiter.api.Assertions.*;
import com.example.town.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MoveRecoveryTest {
    @TempDir Path directory;
    @Test void everyDurableStageRecoversTheSamePaymentAndReservation() throws Exception {
        MoveTransform transform=new MoveTransform(new BlockBox("world",0,60,0,2,62,1),1,60,0,1);
        MoveJob original=new MoveJob(UUID.randomUUID().toString(),"t",UUID.randomUUID(),"玩家",transform,"p",7,true,
                new MoveTransform.Point(1,61,0),90,0,"coin",135,42,MoveJob.Stage.PREPARED,"");
        MoveJobs jobs=new MoveJobs(directory);
        for(MoveJob.Stage stage:MoveJob.Stage.values()) {
            MoveJob saved=original.with(stage,"等待恢复");jobs.save(saved);
            MoveJob loaded=new MoveJobs(directory).get(saved.id());assertEquals(saved,loaded);
            assertEquals(original.payment().debitKey(),loaded.payment().debitKey());
            assertEquals(transform.target(),loaded.transform().target());
        }
    }
    @Test void corruptJournalStopsLoadingInsteadOfDiscardingTheLock() throws Exception {
        Files.write(directory.resolve(UUID.randomUUID()+".job"),new byte[]{1,2,3});
        assertThrows(java.io.IOException.class,()->new MoveJobs(directory));
    }
    @Test void overlappingMovePreservesContainersAndUndoRestoresBothAreas() {
        var a=new MoveTransform.Point(-1,60,0);var b=new MoveTransform.Point(0,60,0);var c=new MoveTransform.Point(1,60,0);
        var chest=new BlockImage("minecraft:chest[facing=north,type=single,waterlogged=false]",new byte[]{1,2,3,4},new byte[0]);
        var stone=new BlockImage("minecraft:stone",new byte[0],new byte[0]);
        var transform=new MoveTransform(new BlockBox("world",-1,60,0,0,60,0),0,60,0,0);
        var snapshot=new MoveSnapshot(transform.source(),transform.target(),Map.of(a,chest,b,stone),List.of());
        assertEquals(BlockImage.AIR,snapshot.finalImage(transform,a,false));
        assertEquals(chest,snapshot.finalImage(transform,b,false));
        assertEquals(stone,snapshot.finalImage(transform,c,false));
        assertEquals(chest,snapshot.finalImage(transform,a,true));
        assertEquals(stone,snapshot.finalImage(transform,b,true));
        assertEquals(BlockImage.AIR,snapshot.finalImage(transform,c,true));
        var cells=WorldMover.cells(transform).values().stream().flatMap(Collection::stream).toList();
        assertEquals(3,cells.size());assertEquals(3,new HashSet<>(cells).size());
    }
    @Test void constructionReservationsSurviveRevisionChecksAndRejectOverlaps() {
        var locks=new AreaLocks();var source=new BlockBox("world",0,60,0,2,62,2);var target=source.translate(4,0,0);
        var r=new AreaLocks.Reservation("a","town",source,target);assertTrue(locks.acquire(r));assertTrue(locks.acquire(r));
        assertFalse(locks.acquire(new AreaLocks.Reservation("a","town",source,source.translate(8,0,0))));
        assertFalse(locks.acquire(new AreaLocks.Reservation("b","town2",target,target.translate(4,0,0))));
        assertTrue(locks.intersects(new PlotBounds("world",4,0,6,2)));assertTrue(locks.locked("world",5,61,1));
        locks.release("a");assertTrue(locks.acquire(new AreaLocks.Reservation("b","town2",target,target.translate(4,0,0))));
    }
}
