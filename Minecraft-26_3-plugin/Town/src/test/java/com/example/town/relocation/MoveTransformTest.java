package com.example.town.relocation;

import static org.junit.jupiter.api.Assertions.*;
import com.example.town.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MoveTransformTest {
    @Test void allQuarterTurnsAreBijectionsForNegativeCoordinatesAndNonSquareBuildings() {
        BlockBox source=new BlockBox("world",-19,-40,-5,-16,-38,1);
        for(int turn=0;turn<4;turn++) {
            var transform=new MoveTransform(source,-13,2,-3,turn);Set<MoveTransform.Point> points=new HashSet<>();
            for(int x=source.minX();x<=source.maxX();x++) for(int y=source.minY();y<=source.maxY();y++) for(int z=source.minZ();z<=source.maxZ();z++) {
                var next=transform.apply(x,y,z);assertTrue(transform.target().contains(next.x(),next.y(),next.z()));
                assertEquals(new MoveTransform.Point(x,y,z),transform.inverse(next.x(),next.y(),next.z()));assertTrue(points.add(next));
            }
            assertEquals(source.volume(),points.size());assertEquals(source.volume(),transform.target().volume());
            assertEquals(turn%2==0?4:7,transform.target().width());
        }
    }
    @Test void overlappingMoveDoesNotTreatSourceBlocksAsForeignCollisionAndPlotUsesSameTransform() {
        BlockBox source=new BlockBox("world",0,0,0,4,3,9);
        var transform=new MoveTransform(source,1,0,1,1);
        assertTrue(source.overlaps(transform.target()));
        assertEquals(new PlotBounds("world",9,1,10,2),transform.plot(new PlotBounds("world",0,0,1,1)));
        assertThrows(IllegalArgumentException.class,()->transform.plot(new PlotBounds("world",0,0,5,1)));
        assertEquals(transform,transform.rotate().rotate().rotate().rotate());
    }
}
