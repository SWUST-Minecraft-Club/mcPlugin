package com.example.town.relocation;

import com.example.town.model.BlockBox;
import com.example.town.store.AtomicFiles;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** 改世界前完整保存源区与目标区；原址与目标重叠时只保存一份原始单元。 */
public record MoveSnapshot(BlockBox source,BlockBox target,Map<MoveTransform.Point,BlockImage> blocks,List<Furniture> furniture) {
    public record Furniture(UUID original,String id,byte[] data,double x,double y,double z,float yaw,float pitch) {
        public Furniture {
            if(original==null || id==null || id.isBlank() || data==null || data.length>4*1024*1024
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch))
                throw new IllegalArgumentException("家具快照无效");
            data=data.clone();
        }
        @Override public byte[] data() {return data.clone();}
        @Override public boolean equals(Object other) {
            return other instanceof Furniture f && original.equals(f.original) && id.equals(f.id) && Arrays.equals(data,f.data)
                    && x==f.x && y==f.y && z==f.z && yaw==f.yaw && pitch==f.pitch;
        }
        @Override public int hashCode() {return Objects.hash(original,id,Arrays.hashCode(data),x,y,z,yaw,pitch);}
    }
    public MoveSnapshot {
        if(!source.world().equals(target.world()) || source.volume()>262144 || target.volume()>262144) throw new IllegalArgumentException("快照选区无效");
        blocks=Map.copyOf(blocks);furniture=furniture.stream().sorted(Comparator.comparing(f->f.original().toString())).toList();
        if(blocks.size()>source.volume()+target.volume() || furniture.size()>4096) throw new IllegalArgumentException("快照过大");
        long bytes=0;
        for(BlockImage image:blocks.values()) bytes=Math.addExact(bytes,image.tileNbt().length+image.customState().length+(long)image.blockData().length()*3+32);
        for(Furniture image:furniture) bytes=Math.addExact(bytes,image.data().length+256L);
        if(bytes>128*1024*1024) throw new IllegalArgumentException("快照数据超过 128MiB");
        for(var entry:blocks.entrySet()) {
            var p=entry.getKey();if(!source.contains(p.x(),p.y(),p.z()) && !target.contains(p.x(),p.y(),p.z())) throw new IllegalArgumentException("快照包含选区外方块");
        }
    }
    public BlockImage at(int x,int y,int z) {return blocks.getOrDefault(new MoveTransform.Point(x,y,z),BlockImage.AIR);}
    public BlockImage finalImage(MoveTransform move,MoveTransform.Point point,boolean rollback) {
        if(!source.equals(move.source()) || !target.equals(move.target())) throw new IllegalArgumentException("工程与快照选区不一致");
        if(rollback) return at(point.x(),point.y(),point.z());
        if(move.target().contains(point.x(),point.y(),point.z())) {
            var original=move.inverse(point.x(),point.y(),point.z());return at(original.x(),original.y(),original.z());
        }
        return BlockImage.AIR;
    }
    public void save(Path file) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(0x544D5331);box(out,source);box(out,target);
            out.writeInt(blocks.size());
            for(var e:blocks.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<MoveTransform.Point,BlockImage> e)->e.getKey().x())
                    .thenComparingInt(e->e.getKey().y()).thenComparingInt(e->e.getKey().z())).toList()) {
                var p=e.getKey();out.writeInt(p.x());out.writeInt(p.y());out.writeInt(p.z());out.writeUTF(e.getValue().blockData());
                blob(out,e.getValue().tileNbt());blob(out,e.getValue().customState());
            }
            out.writeInt(furniture.size());
            for(Furniture f:furniture) {
                out.writeUTF(f.original().toString());out.writeUTF(f.id());blob(out,f.data());
                out.writeDouble(f.x());out.writeDouble(f.y());out.writeDouble(f.z());out.writeFloat(f.yaw());out.writeFloat(f.pitch());
            }
        }
        AtomicFiles.write(file,bytes.toByteArray());
    }
    public static MoveSnapshot load(Path file) throws IOException {
        try(DataInputStream in=new DataInputStream(new java.io.FilterInputStream(new GZIPInputStream(Files.newInputStream(file))) {
            private long remaining=160L*1024*1024;
            @Override public int read() throws IOException {if(--remaining<0) throw new IOException("快照解压数据过大");return super.read();}
            @Override public int read(byte[] b,int offset,int length) throws IOException {
                if(length>remaining) throw new IOException("快照解压数据过大");int n=in.read(b,offset,length);if(n>0) remaining-=n;return n;
            }
        })) {
            if(in.readInt()!=0x544D5331) throw new IOException("迁移快照格式不正确");
            BlockBox source=box(in),target=box(in);Map<MoveTransform.Point,BlockImage> blocks=new HashMap<>();
            int count=limit(in.readInt(),524288);
            for(int i=0;i<count;i++) {
                var p=new MoveTransform.Point(in.readInt(),in.readInt(),in.readInt());
                if(blocks.putIfAbsent(p,new BlockImage(in.readUTF(),blob(in),blob(in)))!=null) throw new IOException("重复快照坐标");
            }
            int furnitureCount=limit(in.readInt(),4096);List<Furniture> furniture=new ArrayList<>();
            for(int i=0;i<furnitureCount;i++) furniture.add(new Furniture(UUID.fromString(in.readUTF()),in.readUTF(),blob(in),
                    in.readDouble(),in.readDouble(),in.readDouble(),in.readFloat(),in.readFloat()));
            if(in.read()!=-1) throw new IOException("迁移快照存在多余数据");
            return new MoveSnapshot(source,target,blocks,furniture);
        }catch(RuntimeException error) {throw new IOException("迁移快照损坏",error);}
    }
    private static void box(DataOutputStream out,BlockBox b) throws IOException {
        out.writeUTF(b.world());out.writeInt(b.minX());out.writeInt(b.minY());out.writeInt(b.minZ());out.writeInt(b.maxX());out.writeInt(b.maxY());out.writeInt(b.maxZ());
    }
    private static BlockBox box(DataInputStream in) throws IOException {return new BlockBox(in.readUTF(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt());}
    private static void blob(DataOutputStream out,byte[] b) throws IOException {out.writeInt(b.length);out.write(b);}
    private static byte[] blob(DataInputStream in) throws IOException {int size=limit(in.readInt(),4*1024*1024);byte[] b=in.readNBytes(size);if(b.length!=size) throw new EOFException();return b;}
    private static int limit(int value,int max) throws IOException {if(value<0 || value>max) throw new IOException("迁移快照长度无效");return value;}
}
