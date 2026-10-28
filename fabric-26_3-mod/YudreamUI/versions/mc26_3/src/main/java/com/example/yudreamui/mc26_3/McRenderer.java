package com.example.yudreamui.mc26_3;
import com.example.yudreamui.runtime.NativeEngine;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import java.lang.reflect.Field;
import java.util.*;

/** 26.3-specific render bridge. Triangle-to-degenerate-quad conversion uses vanilla's GUI pipeline. */
final class McRenderer implements NativeEngine.Textures, AutoCloseable {
    private static final Field STATE;
    static {
        try { STATE = GuiGraphicsExtractor.class.getDeclaredField("guiRenderState"); STATE.setAccessible(true); }
        catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    private final Map<Long, DynamicTexture> textures = new HashMap<>();
    private final List<Retired> retired = new ArrayList<>();
    final LocalTerrain terrain=new LocalTerrain(this);
    private long frame;
    McRenderer() { create(0, new NativeEngine.Image(new byte[]{-1,-1,-1,-1},1,1)); }
    @Override public void create(long id, NativeEngine.Image image) {
        var pixels = new NativeImage(image.width(), image.height(), false);
        var rgba = image.rgba();
        for (int y=0;y<image.height();y++) for(int x=0;x<image.width();x++) {
            int i=(y*image.width()+x)*4;
            pixels.setPixelABGR(x,y,((rgba[i+3]&255)<<24)|((rgba[i+2]&255)<<16)|((rgba[i+1]&255)<<8)|(rgba[i]&255));
        }
        var texture = new DynamicTexture(() -> "YudreamUI/"+id, pixels);
        texture.upload();
        var old=textures.put(id,texture); if(old!=null) retired.add(new Retired(old,frame+3));
    }
    @Override public void retire(long id) { var t=textures.remove(id); if(t!=null) retired.add(new Retired(t,frame+3)); }
    void advance() {
        frame++;
        terrain.advance();
        // Keep detached render-state texture references alive across extraction. GPU backends defer physical destruction.
        retired.removeIf(t -> { if(t.after>frame) return false; com.mojang.blaze3d.systems.RenderSystem.queueFencedTask(t.texture::close); return true; });
    }
    void render(GuiGraphicsExtractor graphics, List<NativeEngine.Command> draws,int mx,int my,float scale) {
        GuiRenderState state;
        try { state=(GuiRenderState)STATE.get(graphics); }
        catch(IllegalAccessException e) { throw new IllegalStateException(e); }
        Matrix3x2f pose=new Matrix3x2f(graphics.pose()).scale(scale);
        for(var command:draws) {
            if(command instanceof NativeEngine.Item item) {renderItem(graphics,item,mx,my,scale);continue;}
            boolean terrainMap=command instanceof NativeEngine.Terrain;
            var draw=terrainMap?terrainDraw((NativeEngine.Terrain)command):(NativeEngine.Draw)command;
            var texture=textures.get(draw.texture()); if(texture==null) texture=textures.get(0L);
            var data=draw.geometry(); var xyuv=data.xyuv();
            float[] positions=new float[xyuv.length/2];
            float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
            Matrix4f transform=draw.transform()==null?null:new Matrix4f().set(draw.transform());
            for(int i=0;i<data.colors().length;i++) {
                var point=new Vector4f(xyuv[i*4]+draw.x(),xyuv[i*4+1]+draw.y(),0,1);
                if(transform!=null) {transform.transform(point); if(point.w!=0) point.div(point.w);}
                var p=pose.transformPosition(point.x,point.y,new Vector2f()); positions[i*2]=p.x;positions[i*2+1]=p.y;
                minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);
            }
            if(data.colors().length==0) continue;
            var bounds=new ScreenRectangle((int)Math.floor(minX),(int)Math.floor(minY),(int)Math.ceil(maxX-minX)+1,(int)Math.ceil(maxY-minY)+1);
            ScreenRectangle clip=null;
            if(draw.clipWidth()>=0) {
                clip=new ScreenRectangle((int)Math.floor(draw.clipX()*scale),(int)Math.floor(draw.clipY()*scale),(int)Math.ceil(draw.clipWidth()*scale),(int)Math.ceil(draw.clipHeight()*scale));
                bounds=bounds.intersection(clip); if(bounds==null) continue;
            }
            var sampler=com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(terrainMap?com.mojang.renderpearl.api.textures.FilterMode.NEAREST:com.mojang.renderpearl.api.textures.FilterMode.LINEAR);
            state.addGuiElement(new Mesh(data,positions,TextureSetup.singleTexture(texture.getTextureView(),sampler),bounds,clip));
        }
    }
    private NativeEngine.Draw terrainDraw(NativeEngine.Terrain t){
        // Match vanilla GUI winding (top-left, bottom-left, bottom-right) to survive face culling.
        var geometry=new NativeEngine.Geometry(new float[]{0,0,0,0,t.width(),0,1,0,t.width(),t.height(),1,1,0,t.height(),0,1},new int[]{-1,-1,-1,-1},new int[]{0,2,1,0,3,2});
        return new NativeEngine.Draw(geometry,terrain.texture(t.spec()),t.x(),t.y(),t.clipX(),t.clipY(),t.clipWidth(),t.clipHeight(),null);
    }
    private void renderItem(GuiGraphicsExtractor graphics,NativeEngine.Item item,int mx,int my,float scale) {
        var id=net.minecraft.resources.Identifier.tryParse(item.identifier());if(id==null)return;
        var value=net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id);if(value.isEmpty())return;
        int size=Math.max(12,Math.round(Math.min(64,Math.min(item.width(),item.height())-8)*scale));
        int x=Math.round((item.x()+item.width()/2)*scale-size/2f),y=Math.round((item.y()+item.height()/2)*scale-size/2f);
        boolean clip=item.clipWidth()>=0;
        if(clip)graphics.enableScissor((int)(item.clipX()*scale),(int)(item.clipY()*scale),(int)((item.clipX()+item.clipWidth())*scale),(int)((item.clipY()+item.clipHeight())*scale));
        graphics.pose().pushMatrix();graphics.pose().translate(x,y);graphics.pose().scale(size/16f);
        graphics.fakeItem(new net.minecraft.world.item.ItemStack(value.get()),0,0);
        graphics.pose().popMatrix();
        boolean hovered=mx>=x&&my>=y&&mx<x+size&&my<y+size&&graphics.containsPointInScissor(mx,my);
        if(clip)graphics.disableScissor();
        if(hovered)graphics.setTooltipForNextFrame(net.minecraft.network.chat.Component.translatable(value.get().getDescriptionId()),mx,my);
    }
    private record Mesh(NativeEngine.Geometry data,float[] positions,TextureSetup textureSetup,ScreenRectangle bounds,ScreenRectangle scissorArea) implements GuiElementRenderState {
        public RenderPipeline pipeline() {return RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA;}
        public void buildVertices(VertexConsumer out) {
            int[] indices=data.indices();
            for(int i=0;i<indices.length;i+=3) {vertex(out,indices[i]);vertex(out,indices[i+1]);vertex(out,indices[i+2]);vertex(out,indices[i+2]);}
        }
        private void vertex(VertexConsumer out,int i) {out.addVertex(positions[i*2],positions[i*2+1],0).setUv(data.xyuv()[i*4+2],data.xyuv()[i*4+3]).setColor(data.colors()[i]);}
    }
    private record Retired(DynamicTexture texture,long after) {}
    @Override public void close() {textures.values().forEach(t->com.mojang.blaze3d.systems.RenderSystem.queueFencedTask(t::close));textures.clear();retired.forEach(t->com.mojang.blaze3d.systems.RenderSystem.queueFencedTask(t.texture::close));retired.clear();}
}
