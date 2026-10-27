package com.example.yudreamui.mc26_3;
import com.example.yudreamui.protocol.*;
import com.example.yudreamui.runtime.*;
import com.google.gson.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class YudreamUiClient implements ClientModInitializer {
    private static final org.slf4j.Logger LOG=LoggerFactory.getLogger("YudreamUI");
    final Minecraft client=Minecraft.getInstance();
    private final Wire.Decoder decoder=new Wire.Decoder();
    private final ExecutorService io=Executors.newFixedThreadPool(2,r->{var t=new Thread(r,"yudream-ui-assets");t.setDaemon(true);return t;});
    private final Path root=FabricLoader.getInstance().getConfigDir().resolve("yudreamui");
    private final AssetStore assets=new AssetStore(root.resolve("cache"),Boolean.getBoolean("yudreamui.allowLocalHttp"));
    NativeEngine engine;
    McRenderer renderer;
    UiScreen screen;
    String session="",pendingSession="";
    long pendingRevision;
    long revision,generation,sequence,ticks;
    private boolean welcomed;
    private String lastError="";
    private long fontBytes;
    private final Set<String> fonts=new HashSet<>();
    private final Deque<Long> timings=new ArrayDeque<>();
    private final String theme=new String(NativeEngine.readResource("/builtin/minecraft.rcss"),StandardCharsets.UTF_8);
    public void onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().register(UiPayload.TYPE,UiPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UiPayload.TYPE,UiPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(UiPayload.TYPE,(payload,context)->context.client().execute(()->receive(payload.bytes())));
        ClientPlayConnectionEvents.JOIN.register((handler,sender,mc)->{welcomed=false;ticks=0;hello();});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->reset());
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            ticks++;
            if(mc.player!=null&&!welcomed&&ticks%40==0) hello();
            if(renderer!=null) renderer.advance();
            if(ticks%20==0&&engine!=null) diagnostics();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc->{reset();io.shutdownNow();});
    }
    private void hello() {
        var message=Wire.message("hello");message.addProperty("runtime","rmlui-6.3");message.addProperty("builtin","mc-1");
        var features=new JsonArray();for(String feature:List.of("rml:6.3","builtin:mc-1","fallback:1","assets:sha256"))features.add(feature);message.add("features",features);send(message);
    }
    void send(JsonObject message) {
        if(client.getConnection()!=null&&ClientPlayNetworking.canSend(UiPayload.TYPE)) for(byte[] frame:Wire.encode(message)) ClientPlayNetworking.send(new UiPayload(frame));
    }
    private void receive(byte[] bytes) {
        try {
            var message=decoder.accept(bytes);if(message==null)return;
            switch(message.get("kind").getAsString()) {
                case "probe" -> hello();
                case "welcome" -> welcomed=true;
                case "open" -> prepare(message);
                case "patch" -> {if(matches(message)&&engine!=null)engine.patch(message.getAsJsonObject("state"));}
                case "cancel" -> {
                    String id=message.get("session").getAsString();long r=message.get("revision").getAsLong();
                    if(id.equals(pendingSession)&&r==pendingRevision){generation++;pendingSession="";}
                    if(id.equals(session)&&r>=revision&&screen!=null)screen.onClose();
                }
                case "close" -> {if(matches(message)&&screen!=null)screen.onClose();}
                default -> throw new IllegalArgumentException("Unknown UI message");
            }
        } catch(Exception e) {error(e);}
    }
    private boolean matches(JsonObject m) {return m.get("session").getAsString().equals(session)&&m.get("revision").getAsLong()==revision;}
    private void prepare(JsonObject message) {
        String nextSession=message.get("session").getAsString();long nextRevision=message.get("revision").getAsLong();
        if(nextSession.equals(session)&&nextRevision<=revision)return;
        if(nextSession.equals(pendingSession)&&nextRevision<=pendingRevision)return;
        final long token=++generation;
        pendingSession=nextSession;pendingRevision=nextRevision;
        String html=message.get("html").getAsString();
        var manifest=message.getAsJsonArray("assets");
        assets.prepare(manifest,io).thenApply(prepared->{
            String source=HtmlCompiler.compile(html,theme);
            for(var entry:prepared.names().entrySet()) {
                source=source.replace("asset:"+entry.getKey()+"\"","asset:"+entry.getValue()+"\"");
                source=source.replace("asset-font-"+entry.getKey(),entry.getKey()+"-"+entry.getValue());
            }
            return new Prepared(source,prepared);
        }).whenComplete((prepared,failure)->client.execute(()->{
            if(token!=generation||client.player==null)return;
            if(failure!=null){error(failure);sendFailure(nextSession,nextRevision);return;}
            try {
                ensureEngine();
                if(!session.equals(nextSession))engine.hide();
                if(fonts.size()+prepared.assets.fonts().keySet().stream().filter(f->!fonts.contains(f)).count()>32)throw new IllegalStateException("Too many font revisions in this connection");
                prepared.assets.images().forEach(engine::image);
                prepared.assets.fonts().forEach((name,data)->{if(!fonts.contains(name)){
                    if(fontBytes+data.length>32L*1024*1024)throw new IllegalStateException("UI font memory budget exceeded");
                    engine.font(data,name);fonts.add(name);fontBytes+=data.length;
                }});
                int w=client.getWindow().getGuiScaledWidth(),h=client.getWindow().getGuiScaledHeight();
                engine.open(prepared.rml,w,h);engine.patch(message.getAsJsonObject("state"));
                session=nextSession;revision=nextRevision;pendingSession="";lastError="";
                if(screen==null||client.gui.screen()!=screen) {screen=new UiScreen(this,message.get("title").getAsString());client.gui.setScreen(screen);}
                screen.title(message.get("title").getAsString());
                screen.sync();
                var ready=Wire.message("ready");ready.addProperty("session",session);ready.addProperty("revision",revision);send(ready);
                diagnostics();
            }catch(Exception | LinkageError e){error(e);sendFailure(nextSession,nextRevision);}
        }));
    }
    private void sendFailure(String s,long r){var m=Wire.message("error");m.addProperty("session",s);m.addProperty("revision",r);send(m);}
    private void ensureEngine(){if(engine==null){renderer=new McRenderer();engine=new NativeEngine(renderer,LOG::warn);}}
    void events() {
        for(var event:engine.drainEvents()) {
            if(event.action().equals("clipboard:set")){client.keyboardHandler.setClipboard(event.value());continue;}
            if(event.action().equals("ui:close")){screen.onClose();continue;}
            if(!event.action().startsWith("server:"))continue;
            var m=Wire.message("action");m.addProperty("session",session);m.addProperty("revision",revision);
            m.addProperty("sequence",++sequence);m.addProperty("action",event.action());m.addProperty("id",event.id());
            var form=new JsonObject();
            for(var element:engine.inspect()) {var e=element.getAsJsonObject();if(e.get("tag").getAsString().equals("input")||e.get("tag").getAsString().equals("textarea"))form.add(e.get("id").getAsString(),e.get("type").getAsString().equals("checkbox")?e.get("checked"):e.get("value"));}
            m.add("form",form);send(m);
        }
        if(screen!=null)screen.sync();
    }
    void closed(UiScreen closed) {
        if(screen!=closed)return;
        var message=Wire.message("closed");message.addProperty("session",session);message.addProperty("revision",revision);send(message);
        generation++;engine.hide();screen=null;session="";revision=0;diagnostics();
    }
    void frameTiming(long nanos){timings.addLast(nanos);if(timings.size()>600)timings.removeFirst();}
    private void diagnostics() {
        if(!Boolean.getBoolean("yudreamui.diagnostics")||engine==null)return;
        var value=new JsonObject();value.addProperty("session",session);value.addProperty("revision",revision);
        value.addProperty("assetRequests",assets.requests.get());value.addProperty("assetDownloads",assets.downloads.get());value.addProperty("cacheHits",assets.cacheHits.get());
        value.addProperty("lastError",lastError);value.addProperty("frames",engine.frames);value.addProperty("nativeFrameNanos",engine.lastFrameNanos);value.addProperty("maxNativeFrameNanos",engine.maxFrameNanos);
        var times=timings.stream().mapToLong(Long::longValue).sorted().toArray();
        if(times.length>0){value.addProperty("extractP50Nanos",times[times.length/2]);value.addProperty("extractP95Nanos",times[(int)((times.length-1)*.95)]);}
        value.add("elements",engine.inspect());
        String json=value.toString();io.execute(()->{Path temp=null;try{Files.createDirectories(root);temp=Files.createTempFile(root,"diagnostics-",".tmp");Files.writeString(temp,json);Files.move(temp,root.resolve("diagnostics.json"),StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){LOG.debug("UI diagnostics unavailable",e);}finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(Exception ignored){}}});
    }
    private void error(Throwable failure){lastError=failure.toString();LOG.error("UI update failed",failure);if(client.player!=null)client.gui.hud.setOverlayMessage(Component.literal("界面更新失败，当前界面仍可使用。"),false);}
    private void reset(){generation++;decoder.clear();if(engine!=null){engine.close();engine=null;}if(renderer!=null){renderer.close();renderer=null;}screen=null;session="";revision=0;fonts.clear();fontBytes=0;timings.clear();welcomed=false;}
    private record Prepared(String rml,AssetStore.Prepared assets){}
}
