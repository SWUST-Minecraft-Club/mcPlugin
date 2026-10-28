package com.example.yudreamui.mc26_3;

import com.example.yudreamui.protocol.TerrainSpec;
import com.example.yudreamui.runtime.NativeEngine;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import java.util.*;

/** Client-thread surface sampling. Never requests chunks or performs server/world IO. */
final class LocalTerrain {
    private static final int MAX_MAPS=4, MAX_TILES=512;
    private final NativeEngine.Textures textures;
    private final LinkedHashMap<TerrainSpec,Surface> maps=new LinkedHashMap<>(8,.75f,true);
    private final LinkedHashMap<Long,Tile> tiles=new LinkedHashMap<>(128,.75f,true);
    private final BlockPos.MutableBlockPos position=new BlockPos.MutableBlockPos();
    private ClientLevel level;
    private long tick, nextTexture=-1, samples, uploads, maxSampleNanos;

    LocalTerrain(NativeEngine.Textures textures){this.textures=textures;}
    long texture(TerrainSpec spec){
        resetWorld();
        var surface=maps.get(spec);
        if(surface==null){
            if(maps.size()>=MAX_MAPS){var first=maps.entrySet().iterator();textures.retire(first.next().getValue().texture);first.remove();}
            surface=new Surface(spec,nextTexture--);maps.put(spec,surface);upload(surface);
        }
        surface.lastSeen=tick;
        return surface.texture;
    }
    private void resetWorld(){
        var current=Minecraft.getInstance().level;
        if(current==level)return;
        for(var surface:maps.values())textures.retire(surface.texture);
        maps.clear();tiles.clear();level=current;
    }
    void advance(){
        tick++;resetWorld();
        maps.entrySet().removeIf(e->{if(tick-e.getValue().lastSeen<=40)return false;textures.retire(e.getValue().texture);return true;});
        if(level==null)return;
        long start=System.nanoTime();int sampled=0;
        var order=new ArrayList<>(maps.entrySet());
        for(int i=0;i<order.size();i++){
            var entry=order.get((int)((tick+i)%order.size()));
            var spec=entry.getKey();var surface=entry.getValue();
            if(tick-surface.lastSeen>2||!spec.dimension().equals(level.dimension().identifier().toString()))continue;
            int total=spec.chunks()*spec.chunks();
            for(int checked=0;checked<total&&sampled<4&&System.nanoTime()-start<2_000_000;checked++){
                int cell=surface.cursor++%total,x=spec.minChunkX()+cell%spec.chunks(),z=spec.minChunkZ()+cell/spec.chunks();
                long key=key(x,z);var tile=tiles.get(key);
                if(tile!=null&&tick-tile.sampled<(tile.loaded?40:10))continue;
                if(tile==null){tile=new Tile();tiles.put(key,tile);}
                long before=System.nanoTime();
                // false is essential: unloaded coordinates return null; no network request or chunk creation.
                var chunk=level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false);
                if(chunk==null){tile.loaded=false;tile.sampled=tick;}
                else sample(tile,chunk,x,z);
                maxSampleNanos=Math.max(maxSampleNanos,System.nanoTime()-before);samples++;sampled++;
            }
            boolean changed=false;int loaded=0;
            for(int cell=0;cell<total;cell++){
                var tile=tiles.get(key(spec.minChunkX()+cell%spec.chunks(),spec.minChunkZ()+cell/spec.chunks()));
                if(tile!=null&&tile.loaded)loaded++;
                for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                    int px=cell%spec.chunks()*16+x,pz=cell/spec.chunks()*16+z,index=pz*spec.pixels()+px;
                    int color=tile!=null&&tile.loaded?tile.colors[z*16+x]:unknown(px,pz);
                    if(surface.colors[index]!=color){surface.colors[index]=color;changed=true;}
                }
            }
            surface.loaded=loaded;
            var player=Minecraft.getInstance().player;
            int markerX=player==null?-1:(int)Math.floor(player.getX())-spec.minChunkX()*16;
            int markerZ=player==null?-1:(int)Math.floor(player.getZ())-spec.minChunkZ()*16;
            if(markerX!=surface.markerX||markerZ!=surface.markerZ){surface.markerX=markerX;surface.markerZ=markerZ;changed=true;}
            if(changed)upload(surface);
        }
        while(tiles.size()>MAX_TILES){var first=tiles.entrySet().iterator();first.next();first.remove();}
    }
    private void sample(Tile tile,LevelChunk chunk,int cx,int cz){
        int minY=level.getMinY();
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            int y=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);
            position.set(cx*16+x,y,cz*16+z);
            var state=chunk.getBlockState(position);var color=state.getMapColor(level,position);
            for(int depth=0;depth<64&&y>minY&&(state.isAir()||color==MapColor.NONE);depth++){
                position.setY(--y);state=chunk.getBlockState(position);color=state.getMapColor(level,position);
            }
            int rgb=color.calculateARGBColor(MapColor.Brightness.NORMAL);
            if(color==MapColor.GRASS)rgb=0xff000000|BiomeColors.getAverageGrassColor(level,position);
            else if(state.is(BlockTags.LEAVES))rgb=0xff000000|BiomeColors.getAverageFoliageColor(level,position);
            else if(color==MapColor.WATER)rgb=0xff000000|BiomeColors.getAverageWaterColor(level,position);
            int north=z==0?y:tile.heights[(z-1)*16+x];
            float brightness=Math.clamp(1f+(y-north)*.045f,.72f,1.18f);
            if(color==MapColor.WATER){
                int depth=0;
                while(depth<8&&position.getY()>minY&&!chunk.getBlockState(position).getFluidState().isEmpty()){position.move(0,-1,0);depth++;}
                brightness*=1f-depth*.035f;
            }
            tile.colors[z*16+x]=color==MapColor.NONE?unknown(x,z):shade(rgb,brightness);
            tile.heights[z*16+x]=y;
        }
        tile.loaded=true;tile.sampled=tick;
    }
    private void upload(Surface surface){
        int size=surface.spec.pixels();byte[] rgba=new byte[size*size*4];
        for(int z=0;z<size;z++)for(int x=0;x<size;x++){
            int color=surface.colors[z*size+x],dx=Math.abs(x-surface.markerX),dz=Math.abs(z-surface.markerZ);
            if(surface.markerX>=0&&surface.markerX<size&&surface.markerZ>=0&&surface.markerZ<size){
                if(dx+dz<=2)color=dx+dz<=1?0xfffff1c4:0xff14252a;
            }
            int i=(z*size+x)*4;rgba[i]=(byte)(color>>16);rgba[i+1]=(byte)(color>>8);rgba[i+2]=(byte)color;rgba[i+3]=(byte)255;
        }
        textures.create(surface.texture,new NativeEngine.Image(rgba,size,size));uploads++;
    }
    JsonObject diagnostics(){
        var out=new JsonObject();out.addProperty("samples",samples);out.addProperty("uploads",uploads);out.addProperty("cachedChunks",tiles.size());out.addProperty("maxChunkSampleNanos",maxSampleNanos);
        var views=new JsonArray();
        for(var entry:maps.entrySet()){
            var s=entry.getValue();var spec=entry.getKey();var v=new JsonObject();
            v.addProperty("dimension",spec.dimension());v.addProperty("chunkX",spec.chunkX());v.addProperty("chunkZ",spec.chunkZ());v.addProperty("chunks",spec.chunks());
            v.addProperty("loaded",s.loaded);v.addProperty("missing",spec.chunks()*spec.chunks()-s.loaded);v.addProperty("colorHash",Arrays.hashCode(s.colors));v.addProperty("uniqueColors",Arrays.stream(s.colors).distinct().count());views.add(v);
        }
        out.add("views",views);return out;
    }
    private static long key(int x,int z){return((long)x<<32)|(z&0xffffffffL);}
    private static int unknown(int x,int z){return((x/4+z/4)&1)==0?0xff35454b:0xff29383f;}
    private static int shade(int rgb,float brightness){return 0xff000000|(Math.clamp(Math.round(((rgb>>16)&255)*brightness),0,255)<<16)|(Math.clamp(Math.round(((rgb>>8)&255)*brightness),0,255)<<8)|Math.clamp(Math.round((rgb&255)*brightness),0,255);}
    private static final class Tile{final int[] colors=new int[256],heights=new int[256];boolean loaded;long sampled;}
    private static final class Surface{
        final TerrainSpec spec;final long texture;final int[] colors;long lastSeen;int cursor,loaded,markerX=-1,markerZ=-1;
        Surface(TerrainSpec spec,long texture){this.spec=spec;this.texture=texture;colors=new int[spec.pixels()*spec.pixels()];for(int z=0;z<spec.pixels();z++)for(int x=0;x<spec.pixels();x++)colors[z*spec.pixels()+x]=unknown(x,z);}
    }
}
