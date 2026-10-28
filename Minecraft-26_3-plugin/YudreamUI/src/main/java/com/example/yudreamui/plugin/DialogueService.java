package com.example.yudreamui.plugin;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Server-owned RPG branching; the same choices work in inventory and Dialog fallback. */
final class DialogueService {
    record Choice(String label,String next,String command,boolean shop,boolean close,String permission) {}
    record Node(String text,List<Choice> choices) {}
    record Conversation(String title,String speaker,Material portrait,String start,Map<String,Node> nodes) {}
    private final YudreamUiPlugin plugin;
    private final Map<String,Conversation> conversations;
    DialogueService(YudreamUiPlugin plugin) {
        this.plugin=plugin;var yaml=YamlConfiguration.loadConfiguration(plugin.getDataPath().resolve("dialogues.yml").toFile());
        var all=yaml.getConfigurationSection("dialogues");var definitions=new HashMap<String,Conversation>();
        if(all!=null)for(String id:all.getKeys(false)) {
            var d=all.getConfigurationSection(id);var nodes=new LinkedHashMap<String,Node>();var section=d.getConfigurationSection("nodes");
            if(section==null)throw new IllegalArgumentException("Dialogue nodes missing");
            for(String name:section.getKeys(false)) {
                var n=section.getConfigurationSection(name);var choices=new ArrayList<Choice>();
                for(var c:n.getMapList("choices")) {
                    String next=Objects.toString(c.get("next"),""),command=Objects.toString(c.get("command"),"");
                    boolean shop=Boolean.TRUE.equals(c.get("shop")),close=Boolean.TRUE.equals(c.get("close"));
                    if((next.isBlank()?0:1)+(command.isBlank()?0:1)+(shop?1:0)+(close?1:0)!=1)throw new IllegalArgumentException("Choice needs exactly one destination");
                    choices.add(new Choice(Objects.toString(c.get("label"),"继续"),next,command,shop,close,Objects.toString(c.get("permission"),"")));
                }
                if(choices.isEmpty()||choices.size()>12)throw new IllegalArgumentException("Dialogue needs 1-12 choices per node");
                nodes.put(name,new Node(n.getString("text",""),List.copyOf(choices)));
            }
            String start=d.getString("start","");if(!nodes.containsKey(start)||nodes.size()>128)throw new IllegalArgumentException("Invalid dialogue start");
            for(var node:nodes.values())for(var choice:node.choices)if(!choice.next.isBlank()&&!nodes.containsKey(choice.next))throw new IllegalArgumentException("Missing dialogue destination");
            Material portrait=Material.matchMaterial(d.getString("portrait","COMPASS"));
            if(portrait==null||portrait.isAir())portrait=Material.COMPASS;
            definitions.put(id,new Conversation(d.getString("title",id),d.getString("speaker",id),portrait,start,Map.copyOf(nodes)));
        }
        conversations=Map.copyOf(definitions);
    }
    void open(Player player,String id) {
        var conversation=conversations.get(id);if(conversation==null)throw new IllegalArgumentException("Unknown dialogue: "+id);
        show(player,conversation,conversation.start,new ArrayList<>());
    }
    private void show(Player player,Conversation conversation,String id,List<String> history) {
        var node=conversation.nodes.get(id);var visible=node.choices.stream().filter(c->c.permission.isBlank()||player.hasPermission(c.permission)).toList();
        var state=new JsonObject();state.addProperty("speaker",conversation.speaker);state.addProperty("dialogue",node.text.replace("{player}",player.getName()));
        var body=new StringBuilder("<section id=\"conversation\" class=\"dialogue\"><div class=\"character\">").append(Views.art("guide","standing"));
        body.append("</div><section id=\"dialogue-panel\" class=\"dialogue-panel\"><header class=\"dialogue-heading\"><div class=\"row\"><mc-slot item=\"minecraft:")
            .append(conversation.portrait.name().toLowerCase(Locale.ROOT)).append("\"/><h2 bind=\"speaker\"></h2><span class=\"muted dialogue-status\">正在交谈</span></div><div class=\"row\"><mc-button id=\"history-toggle\" action=\"toggle:history\" aria-label=\"对话记录\">对话记录</mc-button><mc-button id=\"close\" action=\"ui:close\" aria-label=\"关闭\">×</mc-button></div></header>")
            .append("<main class=\"dialogue-body\"><section class=\"speech\"><span class=\"eyebrow\">旅途中的相遇</span><p id=\"dialogue-text\" bind=\"dialogue\"></p></section><section class=\"choices\">");
        var actions=new HashSet<String>();for(int i=0;i<visible.size();i++) {
            var choice=visible.get(i);actions.add("server:choice"+i);
            body.append("<mc-button id=\"choice").append(i).append("\" action=\"server:choice").append(i).append("\" aria-label=\"").append(Views.escape(choice.label))
                .append("\" fallback-icon=\"PAPER\">").append(i+1).append(". ").append(Views.escape(choice.label)).append("</mc-button>");
        }
        body.append("</section></main><div id=\"history\" class=\"hidden\"><h3>对话记录</h3>");
        if(history.isEmpty())body.append("<p class=\"muted\">你们的交谈刚刚开始。</p>");
        for(String line:history)body.append("<p class=\"muted\">").append(Views.escape(line)).append("</p>");
        body.append("</div></section></section>");
        String css="""
            body{background-color:transparent;align-items:flex-end;}
            .dialogue{width:92%;max-width:1180px;height:240px;max-height:42%;margin-bottom:24px;display:flex;align-items:flex-end;gap:12px;}
            .character{width:160px;height:100%;position:relative;flex-shrink:0;overflow:hidden;}.standing{position:absolute;width:100%;height:auto;bottom:0;left:0;}
            .dialogue-panel{position:relative;flex:1;min-width:0;height:100%;display:flex;flex-direction:column;background-color:#17282dea;border:1px #94b9a97a;}
            .dialogue-heading{display:flex;align-items:center;justify-content:space-between;flex-shrink:0;gap:8px;padding:10px 16px;border-bottom:1px #78988b40;}
            .dialogue-heading h2{font-size:18px;color:#e6dbb9;margin:0;}.dialogue-heading .mc-slot{width:26px;height:26px;}.dialogue-heading .mc-button{height:29px;padding:4px 9px;font-size:12px;}
            #close{min-width:28px;width:28px;padding:2px;font-size:18px;}#history-toggle{background-color:transparent;}
            .dialogue-body{display:flex;flex:1;min-height:0;overflow:hidden;padding:15px 18px;gap:20px;}
            .speech{flex:1;min-width:0;overflow-y:auto;padding-right:14px;border-right:1px #78988b40;}#dialogue-text{font-size:16px;line-height:1.7;white-space:pre-wrap;}
            .choices{width:37%;min-width:210px;display:flex;flex-direction:column;gap:7px;overflow-y:auto;}
            .choices .mc-button{width:100%;height:auto;min-height:35px;flex-shrink:0;text-align:left;padding:8px 12px;}
            #history{position:absolute;top:51px;bottom:0;left:0;right:0;padding:15px 18px;background-color:#17282dfc;overflow-y:auto;z-index:2;}#history p{font-size:14px;margin-bottom:10px;}
            @media(max-width:900px){.dialogue{width:96%;margin-bottom:14px;gap:8px;}.character{width:128px;}.dialogue-heading{padding:8px 11px;}.dialogue-heading h2{font-size:16px;}.dialogue-status{display:none;}.dialogue-body{padding:12px;gap:12px;}#dialogue-text{font-size:14px;}.choices{width:39%;min-width:180px;}.choices .mc-button{padding:7px 9px;font-size:12px;min-height:32px;}#history{top:47px;padding:12px;}}
            @media(max-width:620px){.character{display:none;}.dialogue{margin-bottom:10px;}.dialogue-heading .mc-slot{display:none;}.dialogue-heading h2{font-size:14px;}.choices{min-width:150px;}.speech{padding-right:8px;}.eyebrow{display:none;}}
            """;
        plugin.open(player,Views.document(conversation.title,Views.page(conversation.title,css,body.toString()),state,actions),(p,a)->{
            if(!a.name().startsWith("server:choice"))return;int selected=Integer.parseInt(a.name().substring("server:choice".length()));
            if(selected>=visible.size())return;var choice=visible.get(selected);
            if(!choice.permission.isBlank()&&!p.hasPermission(choice.permission))return;
            var updated=new ArrayList<>(history);updated.add(node.text.replace("{player}",p.getName()));updated.add("你："+choice.label);
            while(updated.size()>12)updated.removeFirst();
            if(!choice.next.isBlank())show(p,conversation,choice.next,updated);
            else {plugin.close(p);if(choice.shop)plugin.openShop(p);else if(!choice.command.isBlank())p.performCommand(choice.command);}
        });
    }
}
