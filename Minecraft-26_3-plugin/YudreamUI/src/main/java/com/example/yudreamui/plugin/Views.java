package com.example.yudreamui.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Set;

/** Small server-side templates. No browser or external assets are needed. */
final class Views {
    private Views() {}
    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
    static String plain(String text) {
        return text.replaceAll("(?i)(?:&|§)#?[0-9a-f]{6}|(?:&|§)[0-9a-fk-or]", "")
                .replaceAll("<[^>]+>", "").strip();
    }
    static String page(String title, String css, String body) {
        return "<html><head><title>" + escape(title) + "</title><style>" + """
            .shell {width:94%;max-width:820px;height:92%;max-height:600px;display:flex;flex-direction:column;}
            .heading {display:flex;align-items:center;justify-content:space-between;flex-shrink:0;margin-bottom:8px;}
            .body {flex:1;min-height:0;overflow-y:auto;padding-right:5px;}
            .grid {display:flex;flex-wrap:wrap;gap:8px;align-items:stretch;}
            .tile {width:31.5%;min-width:140px;flex-grow:1;padding:9px;word-break:break-word;}
            .tile .mc-button {width:100%;height:auto;min-height:25px;margin-top:6px;}
            .status {flex-shrink:0;margin-top:6px;font-size:10px;color:#4d5544;}
            .stat {width:48%;flex-grow:1;margin-bottom:4px;}
            @media (max-width:520px) {.shell{width:98%;height:97%;padding:7px;}.tile{width:47%;min-width:110px;padding:6px;}h1{font-size:14px;}.heading{margin-bottom:5px;}}
            @media (max-width:330px) {.tile{width:100%;}}
            """ + css + "</style></head><body>" + body + "</body></html>";
    }
    static String heading(String title) {
        return "<header class=\"heading\"><h1>" + escape(title) + "</h1><mc-button id=\"close\" action=\"ui:close\" aria-label=\"关闭\">关闭</mc-button></header>";
    }
    static YudreamUiApi.Document document(String title, String html, JsonObject state, Set<String> actions) {
        return new YudreamUiApi.Document(title, html, new JsonArray(), state, actions);
    }
}
