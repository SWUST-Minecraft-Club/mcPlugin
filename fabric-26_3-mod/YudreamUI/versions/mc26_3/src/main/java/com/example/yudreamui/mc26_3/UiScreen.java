package com.example.yudreamui.mc26_3;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import java.util.*;

final class UiScreen extends Screen {
    private final YudreamUiClient owner;
    private final Map<String,AbstractWidget> proxies=new LinkedHashMap<>();
    private JsonArray elements=new JsonArray();
    private boolean closing;
    private IMEPreeditOverlay composition;
    private AbstractWidget compositionTarget;
    private Component currentTitle;
    UiScreen(YudreamUiClient owner,String title){super(Component.literal(title));this.owner=owner;currentTitle=Component.literal(title);}
    void title(String value){currentTitle=Component.literal(value);}
    @Override public Component getTitle(){return currentTitle;}
    @Override protected void init(){clearWidgets();proxies.clear();sync();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean isInGameUi(){return true;}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mx,int my,float delta){
        long start=System.nanoTime();
        owner.renderer.render(graphics,owner.engine.frame(width,height,mx,my),mx,my);
        sync();
        if(getFocused() instanceof EditBox box&&box.visible) {
            int x=box.getX()+6,y=box.getY()+4;
            if(composition!=null&&compositionTarget==box){composition.updateInputPosition(x,y);graphics.setPreeditOverlay(composition);}
            else {composition=null;compositionTarget=null;minecraft.textInputManager().setTextInputArea(x,y,x+1,y+11);}
        }else {composition=null;compositionTarget=null;}
        owner.frameTiming(System.nanoTime()-start);
    }
    void sync(){
        if(owner.engine==null)return;
        elements=owner.engine.inspect();
        var present=new HashSet<String>();AbstractWidget focused=null;
        for(var element:elements){var e=element.getAsJsonObject();String id=e.get("id").getAsString(),tag=e.get("tag").getAsString();
            if(!Set.of("button","input","textarea","select").contains(tag))continue;
            present.add(id);boolean text=tag.equals("textarea")||(tag.equals("input")&&!Set.of("checkbox","range","radio").contains(e.get("type").getAsString()));
            var proxy=proxies.get(id);
            if(proxy==null){proxy=text?new EditBox(font,0,0,10,10,Component.literal(e.get("label").getAsString())):Button.builder(Component.literal(e.get("label").getAsString()),b->{}).bounds(0,0,10,10).build();proxies.put(id,proxy);addWidget(proxy);}
            proxy.setMessage(Component.literal(e.get("label").getAsString()));
            proxy.setX(e.get("clipX").getAsInt());proxy.setY(e.get("clipY").getAsInt());proxy.setWidth(Math.max(1,e.get("clipWidth").getAsInt()));proxy.setHeight(Math.max(1,e.get("clipHeight").getAsInt()));
            proxy.visible=e.get("visible").getAsBoolean();proxy.active=proxy.visible&&!e.get("disabled").getAsBoolean();
            if(proxy instanceof EditBox box&&!box.getValue().equals(e.get("value").getAsString()))box.setValue(e.get("value").getAsString());
            if(e.get("focused").getAsBoolean())focused=proxy;
        }
        proxies.entrySet().removeIf(entry->{if(present.contains(entry.getKey()))return false;removeWidget(entry.getValue());return true;});
        setFocused(focused);
    }
    private int button(int value){return value==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT?0:value==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT?1:2;}
    private int modifiers(InputWithModifiers event){return(event.hasShiftDown()?1:0)|(event.hasControlDown()?2:0)|(event.hasAltDown()?4:0);}
    @Override public void mouseMoved(double x,double y){owner.engine.mouse(0,(float)x,(float)y,0,0);}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick){owner.engine.mouse(1,(float)e.x(),(float)e.y(),button(e.button()),modifiers(e));owner.events();return true;}
    @Override public boolean mouseReleased(MouseButtonEvent e){owner.engine.mouse(2,(float)e.x(),(float)e.y(),button(e.button()),modifiers(e));owner.events();return true;}
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){owner.engine.mouse(0,(float)e.x(),(float)e.y(),button(e.button()),modifiers(e));owner.events();return true;}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){owner.engine.scroll((float)x,(float)y,(float)vertical);owner.events();return true;}
    @Override public boolean keyPressed(KeyEvent e){owner.engine.clipboard(minecraft.keyboardHandler.getClipboard());owner.engine.key(e.key(),modifiers(e),true);owner.events();return true;}
    @Override public boolean keyReleased(KeyEvent e){owner.engine.key(e.key(),modifiers(e),false);owner.events();return true;}
    @Override public boolean charTyped(CharacterEvent e){composition=null;compositionTarget=null;owner.engine.type(e.codepointAsString());owner.events();return true;}
    @Override public boolean preeditUpdated(PreeditEvent event){
        if(!(getFocused() instanceof EditBox box))return false;
        compositionTarget=box;composition=event==null?null:new IMEPreeditOverlay(event,font,10);return true;
    }
    @Override public void onClose(){if(closing)return;closing=true;owner.closed(this);minecraft.gui.setScreen(null);}
    @Override public void removed(){if(!closing){closing=true;owner.closed(this);}}
}
