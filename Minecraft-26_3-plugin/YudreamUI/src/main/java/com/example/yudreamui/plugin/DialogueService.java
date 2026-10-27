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
        var body=new StringBuilder("<mc-panel id=\"conversation\" class=\"shell dialogue\">").append(Views.heading(conversation.title));
        body.append("<main class=\"body\"><div class=\"row speech\"><div class=\"portrait\"><mc-slot item=\"minecraft:").append(conversation.portrait.name().toLowerCase(Locale.ROOT))
            .append("\"/><mc-badge>向导</mc-badge></div><div class=\"grow\"><h2 bind=\"speaker\"></h2><p id=\"dialogue-text\" bind=\"dialogue\"></p></div></div><div class=\"choices\">");
        var actions=new HashSet<String>();for(int i=0;i<visible.size();i++) {
            var choice=visible.get(i);actions.add("server:choice"+i);
            body.append("<mc-button id=\"choice").append(i).append("\" action=\"server:choice").append(i).append("\" aria-label=\"").append(Views.escape(choice.label))
                .append("\" fallback-icon=\"PAPER\">").append(i+1).append(". ").append(Views.escape(choice.label)).append("</mc-button>");
        }
        body.append("</div><mc-button id=\"history-toggle\" action=\"toggle:history\" aria-label=\"对话记录\">对话记录</mc-button><div id=\"history\" class=\"hidden\">");
        for(String line:history)body.append("<p class=\"muted\">").append(Views.escape(line)).append("</p>");
        body.append("</div></main></mc-panel>");
        String css="""
            body {background-color:#0004;align-items:flex-end;}
            .dialogue {height:76%;max-height:78%;max-width:760px;margin-bottom:4%;background-color:#ded3b7;border-top-color:#fff5d7;border-left-color:#fff5d7;}
            .speech {align-items:flex-start;margin-bottom:10px;}.portrait {width:62px;flex-shrink:0;display:flex;flex-direction:column;gap:5px;align-items:center;}
            .portrait .mc-slot {width:50px;height:50px;}#dialogue-text {font-size:12px;line-height:1.7;white-space:pre-wrap;}
            .choices {display:flex;flex-direction:column;gap:5px;margin-bottom:8px;}.choices .mc-button {width:100%;height:auto;min-height:27px;text-align:left;background-color:#546449;}
            @media (max-width:520px) {.dialogue{height:90%;max-height:90%;margin-bottom:2%;}.portrait{width:40px;}.portrait .mc-slot{width:34px;height:34px;}#dialogue-text{font-size:11px;}}
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
