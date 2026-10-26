package com.example.town.relocation;

import com.example.town.model.BlockBox;
import com.example.town.store.AtomicFiles;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** 每笔工程独立原子日志；损坏日志会阻止启动，避免丢失施工锁。只在 IO 线程写入。 */
public final class MoveJobs {
    private final Path directory;
    private final Map<String,MoveJob> jobs=new LinkedHashMap<>();
    public MoveJobs(Path directory) throws IOException {
        this.directory=directory;Files.createDirectories(directory);
        try(var paths=Files.list(directory)) {
            for(Path path:paths.filter(p->p.getFileName().toString().endsWith(".job")).sorted().toList()) {
                MoveJob job=read(path);
                if(!path.getFileName().toString().equals(job.id()+".job") || jobs.putIfAbsent(job.id(),job)!=null)
                    throw new IOException("迁移日志标识冲突");
            }
        }
    }
    public synchronized List<MoveJob> all() {return List.copyOf(jobs.values());}
    public synchronized MoveJob get(String id) {return jobs.get(id);}
    public Path snapshot(String id) {UUID.fromString(id);return directory.resolve(id+".snapshot");}
    public synchronized void save(MoveJob job) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)) {
            out.writeInt(0x544D4A31);out.writeUTF(job.id());out.writeUTF(job.town());out.writeUTF(job.actor().toString());out.writeUTF(job.actorName());
            var move=job.transform();box(out,move.source());out.writeInt(move.targetX());out.writeInt(move.targetY());out.writeInt(move.targetZ());out.writeInt(move.quarterTurns());
            out.writeUTF(job.plot());out.writeLong(job.plotRevision());out.writeBoolean(job.hall());
            if(job.hall()) {out.writeInt(job.hallBefore().x());out.writeInt(job.hallBefore().y());out.writeInt(job.hallBefore().z());}
            out.writeFloat(job.yaw());out.writeFloat(job.pitch());out.writeUTF(job.currency());out.writeLong(job.cents());out.writeLong(job.createdAt());
            out.writeUTF(job.stage().name());out.writeUTF(job.error());
        }
        AtomicFiles.write(directory.resolve(job.id()+".job"),bytes.toByteArray());jobs.put(job.id(),job);
    }
    private MoveJob read(Path file) throws IOException {
        if(Files.size(file)>131072) throw new IOException("迁移日志过大");
        try(DataInputStream in=new DataInputStream(Files.newInputStream(file))) {
            if(in.readInt()!=0x544D4A31) throw new IOException("迁移日志格式错误");
            String id=in.readUTF(),town=in.readUTF();UUID actor=UUID.fromString(in.readUTF());String name=in.readUTF();
            MoveTransform move=new MoveTransform(box(in),in.readInt(),in.readInt(),in.readInt(),in.readInt());
            String plot=in.readUTF();long revision=in.readLong();boolean hall=in.readBoolean();
            MoveTransform.Point point=hall?new MoveTransform.Point(in.readInt(),in.readInt(),in.readInt()):null;
            MoveJob result=new MoveJob(id,town,actor,name,move,plot,revision,hall,point,in.readFloat(),in.readFloat(),in.readUTF(),in.readLong(),in.readLong(),MoveJob.Stage.valueOf(in.readUTF()),in.readUTF());
            if(in.read()!=-1) throw new IOException("迁移日志有多余数据");return result;
        }catch(RuntimeException error) {throw new IOException("迁移日志损坏："+file.getFileName(),error);}
    }
    private static void box(DataOutputStream out,BlockBox b) throws IOException {
        out.writeUTF(b.world());out.writeInt(b.minX());out.writeInt(b.minY());out.writeInt(b.minZ());out.writeInt(b.maxX());out.writeInt(b.maxY());out.writeInt(b.maxZ());
    }
    private static BlockBox box(DataInputStream in) throws IOException {return new BlockBox(in.readUTF(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt());}
}
