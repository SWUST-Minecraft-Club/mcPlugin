package com.example.town.model;

/** 建筑选区使用三维闭区间，与覆盖全部高度的地块边界分开。 */
public record BlockBox(String world,int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
    public BlockBox {
        new PlotBounds(world,minX,minZ,maxX,maxZ);
        if (minY>maxY) throw new IllegalArgumentException("selection-invalid");
    }
    public PlotBounds footprint() { return new PlotBounds(world,minX,minZ,maxX,maxZ); }
    public long volume() { return Math.multiplyExact(footprint().area(),(long)maxY-minY+1); }
    public int width() { return Math.toIntExact((long)maxX-minX+1); }
    public int height() { return Math.toIntExact((long)maxY-minY+1); }
    public int depth() { return Math.toIntExact((long)maxZ-minZ+1); }
    public boolean contains(int x,int y,int z) { return footprint().contains(world,x,z) && y>=minY && y<=maxY; }
    public boolean overlaps(BlockBox other) { return footprint().overlaps(other.footprint()) && minY<=other.maxY && maxY>=other.minY; }
    public BlockBox translate(int dx,int dy,int dz) {
        return new BlockBox(world,Math.addExact(minX,dx),Math.addExact(minY,dy),Math.addExact(minZ,dz),
                Math.addExact(maxX,dx),Math.addExact(maxY,dy),Math.addExact(maxZ,dz));
    }
}
