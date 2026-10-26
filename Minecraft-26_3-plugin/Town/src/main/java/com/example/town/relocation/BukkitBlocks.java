package com.example.town.relocation;

import com.example.town.TownPlugin;
import com.example.town.hook.CraftEngineHook;
import java.io.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.block.*;
import org.bukkit.block.structure.*;
import org.bukkit.util.BlockVector;

/** 原版状态只通过 Bukkit Structure API 保存，容器、告示牌和 PDC 随方块实体 NBT 一并保留。 */
public final class BukkitBlocks {
    private final CraftEngineHook craft;
    private final org.bukkit.structure.StructureManager structures;
    public BukkitBlocks(CraftEngineHook craft) {this.craft=craft;structures=Bukkit.getStructureManager();}
    public BlockImage capture(Block block) {
        byte[] custom=craft==null?new byte[0]:craft.snapshotBlock(block);
        byte[] tile=new byte[0];
        if(block.getState(false) instanceof TileState) {
            var structure=structures.createStructure();structure.fill(block.getLocation(),new BlockVector(1,1,1),false);
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try {structures.saveStructure(out,structure);}catch(IOException error){throw new UncheckedIOException(error);}
            tile=out.toByteArray();
        }
        return new BlockImage(block.getBlockData().getAsString(),tile,custom);
    }
    public void restore(Block block,BlockImage image,int turns) {
        StructureRotation rotation=rotation(turns);
        if(image.customState().length>0 && craft==null) throw new IllegalStateException("恢复此建筑需要 CraftEngine");
        if(craft!=null) craft.removeBlock(block);
        if(image.customState().length>0) {craft.restoreBlock(block,image.customState(),turns);return;}
        if(image.tileNbt().length>0) {
            try {
                var structure=structures.loadStructure(new ByteArrayInputStream(image.tileNbt()));
                structure.place(block.getLocation(),false,rotation,Mirror.NONE,0,1.0f,new Random(0));
            }catch(IOException error) {throw new UncheckedIOException(error);}
        }else {
            var data=Bukkit.createBlockData(image.blockData());data.rotate(rotation);block.setBlockData(data,false);
        }
    }
    public org.bukkit.block.data.BlockData preview(BlockImage image,int turns) {
        if(image.customState().length>0 && craft!=null) return craft.previewBlock(image.customState(),turns);
        var data=Bukkit.createBlockData(image.blockData());data.rotate(rotation(turns));return data;
    }
    public static StructureRotation rotation(int turns) {
        return switch(Math.floorMod(turns,4)) {
            case 1->StructureRotation.CLOCKWISE_90;case 2->StructureRotation.CLOCKWISE_180;
            case 3->StructureRotation.COUNTERCLOCKWISE_90;default->StructureRotation.NONE;
        };
    }
}
