package com.example.yudreamui.runtime;
import com.example.yudreamui.protocol.HtmlCompiler;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
class NativeEngineTest {
 @Test void realRmlUiLayoutInputAndHotReplacement(){
  var textures=new NativeEngine.Textures(){public void create(long id,NativeEngine.Image image){assertEquals(image.width()*image.height()*4,image.rgba().length);}public void retire(long id){}};
  try(var ui=new NativeEngine(textures,System.out::println)){
   String html="<html><head><style>.panel{width:80%;height:90%;}.narrow{width:100px;}@media(max-width:500px){.narrow{width:50px;}}</style></head><body><mc-panel id='panel' class='panel'><mc-button id='add' action='count:counter'>Add</mc-button><span id='counter'>0</span><mc-input id='text'/><div id='responsive' class='narrow'>x</div><p id='bound' bind='status'>old</p></mc-panel></body></html>";
   String theme=new String(NativeEngine.readResource("/builtin/minecraft.rcss"),StandardCharsets.UTF_8);
   String rml=HtmlCompiler.compile(html,theme);ui.open(rml,800,450);assertFalse(ui.frame(800,450,0,0).isEmpty());
   assertEquals(100,find(ui,"responsive").get("width").getAsInt());click(ui,"add");assertEquals("1",find(ui,"counter").get("value").getAsString());
   ui.key(40,0,true);ui.key(40,0,false);assertEquals("2",find(ui,"counter").get("value").getAsString(),"Enter activates once");
   click(ui,"text");ui.type("小镇🐱");assertEquals("小镇🐱",find(ui,"text").get("value").getAsString());
   ui.open(rml,400,360);assertEquals("小镇🐱",find(ui,"text").get("value").getAsString());assertEquals(50,find(ui,"responsive").get("width").getAsInt());
   var patch=new JsonObject();patch.addProperty("status","<untrusted & text>");ui.patch(patch);assertTrue(find(ui,"bound").get("value").getAsString().contains("&lt;"));
  }
 }
 @Test void modalBlocksPointerAndTrapsKeyboardUntilEscape(){
  var textures=new NativeEngine.Textures(){public void create(long id,NativeEngine.Image image){}public void retire(long id){}};
  try(var ui=new NativeEngine(textures,System.out::println)){
   String html="<html><head/><body><mc-button id='open' action='toggle:modal'>Help</mc-button><mc-modal id='modal' class='hidden'><mc-button id='dismiss' action='toggle:modal'>OK</mc-button></mc-modal></body></html>";
   String theme=new String(NativeEngine.readResource("/builtin/minecraft.rcss"),StandardCharsets.UTF_8);
   ui.open(HtmlCompiler.compile(html,theme),800,450);click(ui,"open");assertTrue(find(ui,"modal").get("visible").getAsBoolean());
   ui.key(43,0,true);ui.key(43,0,false);assertTrue(find(ui,"dismiss").get("focused").getAsBoolean());
   ui.key(41,0,true);assertFalse(find(ui,"modal").get("visible").getAsBoolean());assertTrue(find(ui,"open").get("focused").getAsBoolean());
   assertTrue(ui.drainEvents().stream().noneMatch(e->e.action().equals("ui:close")));
  }
 }
 @Test void hotReplacementRestoresScrollAfterHiddenTabLayout(){
  var textures=new NativeEngine.Textures(){public void create(long id,NativeEngine.Image image){}public void retire(long id){}};
  try(var ui=new NativeEngine(textures,System.out::println)){
   String html="<html><head><style>.page{width:300px;height:100px;overflow-y:auto;}.tall{height:1000px;}</style></head><body><div><mc-tab id='tab-a' action='tab:a'>A</mc-tab><mc-tab id='tab-b' action='tab:b'>B</mc-tab><div id='a' class='page tab-page'>A</div><div id='b' class='page tab-page hidden'><mc-checkbox id='check'/><div class='tall'>B</div></div></div></body></html>";
   String theme=new String(NativeEngine.readResource("/builtin/minecraft.rcss"),StandardCharsets.UTF_8),rml=HtmlCompiler.compile(html,theme);
   ui.open(rml,800,450);click(ui,"tab-b");click(ui,"check");var box=find(ui,"b");
   ui.scroll(box.get("x").getAsFloat()+100,box.get("y").getAsFloat()+50,-3);
   float offset=find(ui,"b").get("scrollTop").getAsFloat();assertTrue(offset>0);
   ui.open(rml,800,450);assertTrue(find(ui,"check").get("checked").getAsBoolean());
   assertEquals(offset,find(ui,"b").get("scrollTop").getAsFloat(),0.1f);
  }
 }
 static JsonObject find(NativeEngine ui,String id){for(var e:ui.inspect())if(e.getAsJsonObject().get("id").getAsString().equals(id))return e.getAsJsonObject();throw new AssertionError(id);}
 static void click(NativeEngine ui,String id){var e=find(ui,id);float x=e.get("x").getAsFloat()+e.get("width").getAsFloat()/2,y=e.get("y").getAsFloat()+e.get("height").getAsFloat()/2;ui.mouse(1,x,y,0,0);ui.mouse(2,x,y,0,0);}
}
