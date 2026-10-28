package com.example.yudreamui.protocol;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class ProtocolTest {
 @Test void terrainComponentBoundsCoordinatesAndKeepsServerActions(){
  String html="<html><head/><body><mc-terrain id='map' dimension='minecraft:overworld' chunk-x='-2' chunk-z='3' chunks='7'/><mc-button id='claim' action='server:claim'>购买区块</mc-button></body></html>";
  String compiled=HtmlCompiler.compile(html,"");assertTrue(compiled.contains("mcterrain"));assertTrue(HtmlCompiler.compile(compiled,"").contains("chunk-x=\"-2\""));
  var spec=new TerrainSpec("minecraft:overworld",-2,3,7);assertEquals(-5,spec.minChunkX());assertEquals(0,spec.minChunkZ());assertEquals(112,spec.pixels());
  var fallback=FallbackPlan.fromHtml(html,"领土");assertEquals("server:claim",fallback.buttons().getFirst().action());
  for(String invalid:List.of(html.replace("chunks='7'","chunks='16'"),html.replace("chunks='7'","chunks='0'"),html.replace("chunk-x='-2'","chunk-x='2147483647'"),html.replace("minecraft:overworld","https://world?remote")))assertThrows(IllegalArgumentException.class,()->HtmlCompiler.compile(invalid,""));
  String many="<html><head/><body>"+"<mc-terrain dimension='minecraft:overworld' chunk-x='0' chunk-z='0' chunks='7'/>".repeat(5)+"</body></html>";
  assertThrows(IllegalArgumentException.class,()->HtmlCompiler.compile(many,""));
 }
 @Test void fragmentedUnicodeReordersAndDeduplicates(){var m=Wire.message("open");m.addProperty("html","界面🐱".repeat(20000));var frames=new ArrayList<>(Wire.encode(m));Collections.reverse(frames);var d=new Wire.Decoder();assertNull(d.accept(frames.getFirst()));assertNull(d.accept(frames.getFirst()));com.google.gson.JsonObject result=null;for(int i=1;i<frames.size();i++)result=d.accept(frames.get(i));assertEquals(m,result);}
 @Test void invalidFramingAndVersionFail(){var d=new Wire.Decoder();assertThrows(IllegalArgumentException.class,()->d.accept(new byte[20]));var m=Wire.message("open");m.addProperty("protocol",99);assertThrows(IllegalArgumentException.class,()->d.accept(Wire.encode(m).getFirst()));assertThrows(IllegalArgumentException.class,()->d.accept(Arrays.copyOf(Wire.encode(Wire.message("hi")).getFirst(),21)));}
 @Test void compilerExpandsBuiltinsAndPreservesResponsiveCss(){var out=HtmlCompiler.compile("<html><head><style>@media (max-width:500px){.x{width:90%;}}</style></head><body><mc-button id='buy' action='server:buy'>购买</mc-button><mc-checkbox id='check'/></body></html>",".mc-button {color:#fff;}");assertTrue(out.contains("class=\"mc-button\""));assertTrue(out.contains("@media"));assertTrue(out.contains("type=\"checkbox\""));assertFalse(out.contains("<mc-button"));}
 @Test void compilerRejectsExecutableAndExternalContent(){for(String content:List.of("<script>alert(1)</script>","<button onclick='x()'>x</button>","<img src='https://example.org/a.png'/>","<style>@import 'bad';</style>","<div style='background:url(file:/etc/passwd)'/>"))assertThrows(IllegalArgumentException.class,()->HtmlCompiler.compile("<html><head/><body>"+content+"</body></html>",""));assertThrows(IllegalArgumentException.class,()->HtmlCompiler.compile("<!DOCTYPE html SYSTEM 'file:/etc/passwd'><html><head/><body/></html>",""));}
}
