package com.example.yudreamui.plugin;

import java.lang.reflect.*;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

/** Optional, isolated bridge for CoderKuo/TrMenu 3.12.x. No NMS or TrMenu classes leak into the UI protocol. */
final class TrMenuBridge implements FallbackProvider {
    private final YudreamUiPlugin plugin;
    private final java.util.Map<java.util.UUID,Bridge> current=new java.util.concurrent.ConcurrentHashMap<>();
    private final org.bukkit.event.Listener listener=new org.bukkit.event.Listener(){};
    private boolean listening;
    TrMenuBridge(YudreamUiPlugin plugin){this.plugin=plugin;}
    @Override public String id(){return "trmenu";}
    @Override public Handle open(Player player,Context context) {
        // The bundled TrMenu YAML is a showcase template, not a generic projection of every plan.
        // Reject other views so shop/dialogue actions remain usable through inventory recovery.
        var buttons=context.plan().buttons();
        if(!buttons.stream().map(b->b.id()).collect(java.util.stream.Collectors.toSet()).equals(java.util.Set.of("buy","save","slow"))
            ||buttons.stream().anyMatch(b->!b.action().equals("server:"+b.id())))throw new IllegalStateException("TrMenu template does not cover this semantic view");
        var trmenu=plugin.getServer().getPluginManager().getPlugin("TrMenu");
        if(trmenu==null||!trmenu.isEnabled())throw new IllegalStateException("TrMenu is not enabled");
        try {
            var loader=trmenu.getClass().getClassLoader();
            String prefix;
            try{loader.loadClass("me.arasple.mc.trmenu.api.TrMenuAPI");prefix="me.arasple.mc.trmenu";}
            catch(ClassNotFoundException absent){prefix="trplugins.menu";}
            var api=loader.loadClass(prefix+".api.TrMenuAPI");
            listen(loader,prefix);
            Object menu=api.getMethod("getMenuById",String.class).invoke(api.getField("INSTANCE").get(null),plugin.getConfig().getString("fallback.trmenu-id","yudreamui"));
            if(menu==null)throw new IllegalStateException("Configured TrMenu menu is missing");
            var reason=loader.loadClass(prefix+".api.event.MenuOpenEvent$Reason");
            Object unknown=reason.getField("UNKNOWN").get(null);
            var open=menu.getClass().getMethod("open",Player.class,Integer.class,reason,Consumer.class);
            var handle=new Bridge(player,context,menu,unknown,open);handle.show();current.put(player.getUniqueId(),handle);return handle;
        }catch(ReflectiveOperationException error){throw new IllegalStateException("TrMenu API mismatch",error);}
    }
    @SuppressWarnings("unchecked")
    private synchronized void listen(ClassLoader loader,String prefix)throws ReflectiveOperationException {
        if(listening)return;
        var eventClass=(Class<? extends org.bukkit.event.Event>)loader.loadClass(prefix+".api.event.MenuCloseEvent");
        plugin.getServer().getPluginManager().registerEvent(eventClass,listener,org.bukkit.event.EventPriority.MONITOR,(ignored,event)->{
            try{
                Object session=eventClass.getMethod("getSession").invoke(event);
                var player=(Player)session.getClass().getMethod("getViewer").invoke(session);var bridge=current.get(player.getUniqueId());
                if(bridge!=null&&bridge.session==session&&!bridge.closing&&!bridge.suspended)
                    player.getScheduler().run(plugin,t->{if(current.get(player.getUniqueId())==bridge&&bridge.context.active()&&!bridge.suspended)bridge.context.close();},null);
            }catch(ReflectiveOperationException error){throw new org.bukkit.event.EventException(error);}
        },plugin,true);
        listening=true;
    }
    private final class Bridge implements Handle {
        private final Player player;private final Context context;private final Object menu,reason;private final Method open;
        private Object session;private boolean closing,suspended;
        Bridge(Player player,Context context,Object menu,Object reason,Method open){this.player=player;this.context=context;this.menu=menu;this.reason=reason;this.open=open;}
        private String[] arguments(){var state=context.state();return new String[]{context.ticket(),state.has("balance")?state.get("balance").getAsString():"",state.has("status")?state.get("status").getAsString():""};}
        private void show(){
            try {
                Consumer<Object> setup=s->{try{session=s;s.getClass().getMethod("setArguments",String[].class).invoke(s,(Object)arguments());}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}};
                open.invoke(menu,player,null,reason,setup);
                if(session==null)throw new IllegalStateException("TrMenu rejected opening this menu");
                suspended=false;
            }catch(ReflectiveOperationException error){throw new IllegalStateException("TrMenu open failed",error);}
        }
        @Override public void update(){
            if(closing||!context.active())return;
            try {
                if(suspended){show();return;}
                session.getClass().getMethod("setArguments",String[].class).invoke(session,(Object)arguments());
                menu.getClass().getMethod("page",Player.class,int.class,String.class).invoke(menu,player,0,null);
            }catch(ReflectiveOperationException error){throw new IllegalStateException("TrMenu update failed",error);}
        }
        @Override public void suspend(){suspended=true;shut();player.closeInventory();player.closeDialog();}
        private void shut(){if(session!=null)try{session.getClass().getMethod("close",boolean.class,boolean.class).invoke(session,true,false);session.getClass().getMethod("shut").invoke(session);}catch(ReflectiveOperationException e){plugin.getLogger().warning("TrMenu close failed: "+e.getClass().getSimpleName());}}
        @Override public void close(){closing=true;current.remove(player.getUniqueId(),this);shut();player.closeInventory();player.closeDialog();}
    }
}
