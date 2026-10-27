package com.example.yudreamui.plugin;

import com.example.yudreamui.protocol.FallbackPlan;
import com.google.gson.*;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.*;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

/** Native backends and adapter registry. A close or superseding view invalidates every callback. */
final class FallbackMenus implements Listener {
    private final YudreamUiPlugin plugin;
    private final Map<String,FallbackProvider> providers = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID,View> views = new java.util.concurrent.ConcurrentHashMap<>();
    FallbackMenus(YudreamUiPlugin plugin) { this.plugin = plugin; }
    void register(FallbackProvider provider) {
        if (!provider.id().matches("[a-z][a-z0-9_-]{0,31}") || Set.of("inventory","dialog").contains(provider.id())) throw new IllegalArgumentException("Invalid provider ID");
        if (providers.putIfAbsent(provider.id(),provider) != null) throw new IllegalArgumentException("Duplicate fallback provider");
    }
    String open(Player player,FallbackProvider.Context context,String backend) {
        close(player);
        var view = new View(player,context,backend); views.put(player.getUniqueId(),view);
        if (providers.containsKey(backend)) {
            try { view.external = Objects.requireNonNull(providers.get(backend).open(player,context)); return backend; }
            catch (RuntimeException | LinkageError error) { recover(view,error); }
        }
        if(!Set.of("inventory","dialog").contains(view.backend))view.backend="inventory";
        root(view);
        return view.backend;
    }
    String patch(Player player) {
        var view=views.get(player.getUniqueId());if(view==null)return null;
        if(!view.form)try {if(view.external!=null)view.external.update();else if(view.inventory!=null)paint(view);else root(view);}
        catch(RuntimeException | LinkageError error){recover(view,error);root(view);}
        return view.backend;
    }
    private void recover(View view,Throwable error) {
        plugin.getLogger().warning("Fallback adapter unavailable ("+view.backend+"): "+error.getClass().getSimpleName());
        var external=view.external;view.external=null;view.backend="inventory";
        if(external!=null)try{external.close();}catch(RuntimeException | LinkageError ignored){}
    }
    void close(Player player) {
        var view=views.remove(player.getUniqueId());if(view==null)return;view.closing=true;
        if(view.external!=null)try{view.external.close();}catch(RuntimeException | LinkageError error){plugin.getLogger().warning("Fallback adapter close failed: "+error.getClass().getSimpleName());}
        else if(view.inventory!=null&&player.getOpenInventory().getTopInventory()==view.inventory)player.closeInventory();
        else if(view.backend.equals("dialog")||view.form)player.closeDialog();
    }
    void forget(Player player) { close(player); }
    void edit(Player player,String id) { var view=views.get(player.getUniqueId());if(view!=null)form(view,view.context.plan().button(id)); }
    private boolean current(View view) {return !view.closing&&views.get(view.player.getUniqueId())==view&&view.context.active();}
    private void later(View view,Runnable action) {view.player.getScheduler().run(plugin,t->{if(current(view))action.run();},null);}
    private void root(View view) {
        if(!current(view))return;view.form=false;
        if(view.external!=null){try{view.external.update();return;}catch(RuntimeException | LinkageError error){recover(view,error);}}
        if(view.backend.equals("dialog")){dialog(view);return;}
        if(view.inventory==null)view.inventory=Bukkit.createInventory(new Holder(view),36,Component.text(view.context.plan().title()));
        paint(view);view.player.openInventory(view.inventory);
    }
    private void paint(View view) {
        var inventory=view.inventory;inventory.clear();view.controls.clear();
        String body=view.context.plan().renderBody(view.context.state());
        inventory.setItem(4,item(Material.BOOK,view.context.plan().title(),List.of(body.split("\\n"))));
        var buttons=view.context.plan().buttons();int start=view.page*18;
        for(int i=start;i<Math.min(start+18,buttons.size());i++) {
            var b=buttons.get(i);int slot=9+i-start;Material icon=Material.matchMaterial(b.icon());if(icon==null||icon.isAir())icon=Material.PAPER;
            inventory.setItem(slot,item(icon,b.label(),List.of(b.disabled()?"当前不可用":b.inputs().isEmpty()?"点击操作":"点击填写并确认")));view.controls.put(slot,b.id());
        }
        inventory.setItem(31,item(Material.BARRIER,"关闭",List.of()));
        if(view.page>0)inventory.setItem(27,item(Material.ARROW,"上一页",List.of()));
        if(start+18<buttons.size())inventory.setItem(35,item(Material.ARROW,"下一页",List.of()));
    }
    private ItemStack item(Material material,String name,List<String> lore) {
        var item=new ItemStack(material);var meta=item.getItemMeta();meta.displayName(Component.text(name));meta.lore(lore.stream().map(Component::text).toList());item.setItemMeta(meta);return item;
    }
    private void click(View view,FallbackPlan.Button button) {
        if(!current(view)||button.disabled())return;
        if(!button.inputs().isEmpty()){form(view,button);return;}
        later(view,()->view.context.invoke(button.id(),new JsonObject()));
    }
    private ActionButton button(View view,String text,Runnable action) {
        return ActionButton.builder(Component.text(text)).width(150).action(DialogAction.customClick((response,audience)->{
            if(audience instanceof Player player&&player.getUniqueId().equals(view.player.getUniqueId()))later(view,action);
        },ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build())).build();
    }
    private void dialog(View view) {
        var actions=new ArrayList<ActionButton>();
        for(var b:view.context.plan().buttons())if(!b.disabled())actions.add(button(view,b.label(),()->click(view,b)));
        var exit=button(view,"关闭",view.context::close);
        var dialog=Dialog.create(builder->builder.empty().base(DialogBase.builder(Component.text(view.context.plan().title())).pause(false)
            .canCloseWithEscape(true).afterAction(DialogBase.DialogAfterAction.CLOSE)
            .body(List.of(DialogBody.plainMessage(Component.text(view.context.plan().renderBody(view.context.state())),310))).build())
            .type(DialogType.multiAction(actions).columns(2).exitAction(exit).build()));
        view.player.showDialog(dialog);
    }
    private void form(View view,FallbackPlan.Button control) {
        if(!current(view))return;
        view.form=true;view.transition=true;
        if(view.external!=null)view.external.suspend();
        if(view.inventory!=null&&view.player.getOpenInventory().getTopInventory()==view.inventory)view.player.closeInventory();
        var inputs=new ArrayList<DialogInput>();
        for(String id:control.inputs()) {
            var f=view.context.plan().field(id);JsonElement previous=view.draft.get(id);String initial=previous==null?f.initial():previous.getAsString();
            inputs.add(switch(f.type()) {
                case "checkbox"->DialogInput.bool(id,Component.text(f.label())).initial(Boolean.parseBoolean(initial)).build();
                case "range"->DialogInput.numberRange(id,Component.text(f.label()),f.min(),f.max()).width(310)
                    .initial(initial.isBlank()?f.min():Float.parseFloat(initial)).step(f.step()).build();
                default->DialogInput.text(id,Component.text(f.label())).width(310).initial(initial).maxLength(f.maxLength()).build();
            });
        }
        var submit=ActionButton.builder(Component.text("确认")).width(150).action(DialogAction.customClick((response,audience)->{
            if(!(audience instanceof Player player)||!player.getUniqueId().equals(view.player.getUniqueId()))return;
            var data=new JsonObject();
            for(String id:control.inputs()) {
                var f=view.context.plan().field(id);
                switch(f.type()) {
                    case "checkbox"->data.addProperty(id,Boolean.TRUE.equals(response.getBoolean(id)));
                    case "range"->data.addProperty(id,Objects.requireNonNullElse(response.getFloat(id),f.min()));
                    default->data.addProperty(id,Objects.requireNonNullElse(response.getText(id),""));
                }
            }
            later(view,()->{view.draft=data.deepCopy();view.context.invoke(control.id(),data);later(view,()->root(view));});
        },ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build())).build();
        var cancel=button(view,"取消并返回",()->root(view));
        var dialog=Dialog.create(builder->builder.empty().base(DialogBase.builder(Component.text(control.label())).pause(false)
            .canCloseWithEscape(true).afterAction(DialogBase.DialogAfterAction.CLOSE).inputs(inputs).build())
            .type(DialogType.multiAction(List.of(submit)).exitAction(cancel).columns(2).build()));
        later(view,()->{view.transition=false;view.player.showDialog(dialog);});
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if(!(event.getInventory().getHolder() instanceof Holder holder))return;
        event.setCancelled(true);var view=holder.view;
        if(!current(view)||!event.getWhoClicked().getUniqueId().equals(view.player.getUniqueId()))return;
        int slot=event.getRawSlot();if(slot<0||slot>=36)return;
        if(slot==31){later(view,view.context::close);return;}
        if(slot==27&&view.page>0){view.page--;paint(view);return;}
        if(slot==35&&(view.page+1)*18<view.context.plan().buttons().size()){view.page++;paint(view);return;}
        String id=view.controls.get(slot);if(id!=null)click(view,view.context.plan().button(id));
    }
    @EventHandler public void drag(InventoryDragEvent event) {if(event.getInventory().getHolder() instanceof Holder)event.setCancelled(true);}
    @EventHandler public void closed(InventoryCloseEvent event) {
        if(event.getInventory().getHolder() instanceof Holder holder){var view=holder.view;if(current(view)&&!view.transition)later(view,view.context::close);}
    }
    private static final class Holder implements InventoryHolder {
        final View view;Holder(View view){this.view=view;}
        @Override public Inventory getInventory(){return view.inventory;}
    }
    private static final class View {
        final Player player;final FallbackProvider.Context context;String backend;boolean closing,transition,form;int page;
        Inventory inventory;FallbackProvider.Handle external;JsonObject draft=new JsonObject();final Map<Integer,String> controls=new java.util.concurrent.ConcurrentHashMap<>();
        View(Player player,FallbackProvider.Context context,String backend){this.player=player;this.context=context;this.backend=backend;}
    }
}
