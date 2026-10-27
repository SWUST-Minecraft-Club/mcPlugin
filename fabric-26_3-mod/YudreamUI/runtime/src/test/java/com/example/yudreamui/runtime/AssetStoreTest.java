package com.example.yudreamui.runtime;
import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.net.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
class AssetStoreTest {
 @TempDir Path directory;
 @Test void builtinPathDoesNoWork(){var store=new AssetStore(directory.resolve("unused"),false);var result=store.prepare(new JsonArray(),r->{throw new AssertionError("Unexpected async resource work");}).join();assertTrue(result.images().isEmpty());assertEquals(0,store.requests.get());assertFalse(Files.exists(directory.resolve("unused")));}
 @Test void contentAddressedDownloadCachesAndRejectsCorruption()throws Exception{
  var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,0x80ff0000);var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);byte[] data=bytes.toByteArray();
  var calls=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/image",ex->{calls.incrementAndGet();ex.sendResponseHeaders(200,data.length);try(var out=ex.getResponseBody()){out.write(data);}});server.start();
  try{var store=new AssetStore(directory,true);var a=new JsonObject();a.addProperty("id","banner");a.addProperty("kind","image");a.addProperty("hash",AssetStore.sha256(data));a.addProperty("url","http://127.0.0.1:"+server.getAddress().getPort()+"/image");var manifest=new JsonArray();manifest.add(a);
   var first=store.prepare(manifest,Runnable::run).join();assertEquals(1,calls.get());assertEquals(128,first.images().values().iterator().next().rgba()[0]&255);
   store.prepare(manifest,Runnable::run).join();assertEquals(1,calls.get());assertEquals(1,store.cacheHits.get());
   a.addProperty("hash","0".repeat(64));assertThrows(CompletionException.class,()->store.prepare(manifest,Runnable::run).join());assertFalse(Files.exists(directory.resolve("0".repeat(64))));
  }finally{server.stop(0);}
 }
}
