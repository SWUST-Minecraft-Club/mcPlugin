package com.example.town.relocation;

import static org.junit.jupiter.api.Assertions.*;
import com.example.town.model.BlockBox;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MoveSnapshotTest {
    @TempDir Path directory;
    @Test void durableOverlapSnapshotKeepsTileCustomBlockAndFurnitureDataWithoutAliasing() throws Exception {
        BlockBox box=new BlockBox("world",-4,60,0,4,70,9);
        byte[] contents={1,3,5};BlockImage chest=new BlockImage("minecraft:chest[facing=north,type=single,waterlogged=false]",contents,new byte[]{6,8});
        contents[0]=9;assertEquals(1,chest.tileNbt()[0]);
        byte[] returned=chest.tileNbt();returned[0]=10;assertEquals(1,chest.tileNbt()[0]);
        var snapshot=new MoveSnapshot(box,box.translate(2,0,0),Map.of(new MoveTransform.Point(-1,60,2),chest),
                List.of(new MoveSnapshot.Furniture(UUID.randomUUID(),"town:bench",new byte[]{11,12},0.5,60,3.5,90,0)));
        Path file=directory.resolve("blocks.nbt.gz");snapshot.save(file);
        assertEquals(snapshot,MoveSnapshot.load(file));assertEquals(BlockImage.AIR,snapshot.at(2,70,9));
        byte[] bytes=Files.readAllBytes(file);Files.write(file,Arrays.copyOf(bytes,bytes.length-5));
        assertThrows(java.io.IOException.class,()->MoveSnapshot.load(file));
    }
}
