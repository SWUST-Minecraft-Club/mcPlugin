package com.example.town.relocation;

import com.example.town.model.*;

/** 以选区西北下角为基准，围绕整个建筑旋转；闭区间中的负坐标和长宽交换均明确处理。 */
public record MoveTransform(BlockBox source,int targetX,int targetY,int targetZ,int quarterTurns) {
    public record Point(int x,int y,int z) { }
    public MoveTransform {
        if(quarterTurns<0 || quarterTurns>3) throw new IllegalArgumentException("move-rotation-invalid");
        if(source.width()>512 || source.height()>512 || source.depth()>512) throw new IllegalArgumentException("move-too-large");
        int width=quarterTurns%2==0?source.width():source.depth();
        int depth=quarterTurns%2==0?source.depth():source.width();
        new BlockBox(source.world(),targetX,targetY,targetZ,Math.addExact(targetX,width-1),
                Math.addExact(targetY,source.height()-1),Math.addExact(targetZ,depth-1));
    }
    public BlockBox target() {
        int width=quarterTurns%2==0?source.width():source.depth();
        int depth=quarterTurns%2==0?source.depth():source.width();
        return new BlockBox(source.world(),targetX,targetY,targetZ,Math.addExact(targetX,width-1),
                Math.addExact(targetY,source.height()-1),Math.addExact(targetZ,depth-1));
    }
    public Point apply(int x,int y,int z) {
        if(!source.contains(x,y,z)) throw new IllegalArgumentException("move-outside-selection");
        int rx=x-source.minX(),rz=z-source.minZ();
        return switch(quarterTurns) {
            case 0 -> new Point(targetX+rx,targetY+y-source.minY(),targetZ+rz);
            case 1 -> new Point(targetX+source.depth()-1-rz,targetY+y-source.minY(),targetZ+rx);
            case 2 -> new Point(targetX+source.width()-1-rx,targetY+y-source.minY(),targetZ+source.depth()-1-rz);
            case 3 -> new Point(targetX+rz,targetY+y-source.minY(),targetZ+source.width()-1-rx);
            default -> throw new IllegalStateException();
        };
    }
    public Point inverse(int x,int y,int z) {
        BlockBox target=target();
        if(!target.contains(x,y,z)) throw new IllegalArgumentException("move-outside-selection");
        int rx=x-targetX,rz=z-targetZ;
        return switch(quarterTurns) {
            case 0 -> new Point(source.minX()+rx,source.minY()+y-targetY,source.minZ()+rz);
            case 1 -> new Point(source.minX()+rz,source.minY()+y-targetY,source.minZ()+source.depth()-1-rx);
            case 2 -> new Point(source.minX()+source.width()-1-rx,source.minY()+y-targetY,source.minZ()+source.depth()-1-rz);
            case 3 -> new Point(source.minX()+source.width()-1-rz,source.minY()+y-targetY,source.minZ()+rx);
            default -> throw new IllegalStateException();
        };
    }
    public PlotBounds plot(PlotBounds bounds) {
        if(!source.footprint().contains(bounds)) throw new IllegalArgumentException("move-plot-partial");
        Point a=apply(bounds.minX(),source.minY(),bounds.minZ()),b=apply(bounds.maxX(),source.minY(),bounds.maxZ());
        return PlotBounds.between(source.world(),a.x(),a.z(),b.x(),b.z());
    }
    public MoveTransform adjust(int x,int y,int z) {
        return new MoveTransform(source,Math.addExact(targetX,x),Math.addExact(targetY,y),Math.addExact(targetZ,z),quarterTurns);
    }
    public MoveTransform rotate() {return new MoveTransform(source,targetX,targetY,targetZ,(quarterTurns+1)%4);}
}
