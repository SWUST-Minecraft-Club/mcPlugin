package com.example.town.relocation;

import java.util.Arrays;
import java.util.Objects;

/** 单个方块的不可变快照；原版方块实体使用 1×1×1 Structure NBT，CE 状态单独保存。 */
public record BlockImage(String blockData,byte[] tileNbt,byte[] customState) {
    public static final BlockImage AIR=new BlockImage("minecraft:air",new byte[0],new byte[0]);
    public BlockImage {
        Objects.requireNonNull(blockData);
        tileNbt=tileNbt.clone();customState=customState.clone();
    }
    @Override public byte[] tileNbt() {return tileNbt.clone();}
    @Override public byte[] customState() {return customState.clone();}
    public boolean air() {return blockData.equals("minecraft:air") || blockData.equals("minecraft:cave_air") || blockData.equals("minecraft:void_air");}
    @Override public boolean equals(Object other) {
        return other instanceof BlockImage b && blockData.equals(b.blockData) && Arrays.equals(tileNbt,b.tileNbt) && Arrays.equals(customState,b.customState);
    }
    @Override public int hashCode() {return 31*(31*blockData.hashCode()+Arrays.hashCode(tileNbt))+Arrays.hashCode(customState);}
}
