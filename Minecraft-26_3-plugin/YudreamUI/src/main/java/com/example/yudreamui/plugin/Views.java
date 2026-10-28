package com.example.yudreamui.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Set;

/** Server-owned presentation; art is replaceable independently of the client adapter. */
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
        return "<html ui-density=\"2\"><head><title>" + escape(title) + "</title><style>" + """
            body{font-size:14px;color:#dbe5e1;background-color:#101b2380;}
            h1{font-size:25px;font-weight:normal;margin:0;}h2{font-size:18px;font-weight:normal;margin:0 0 8px;}
            h3{font-size:15px;font-weight:normal;margin:0 0 6px;}p{line-height:1.55;margin:0 0 7px;}
            .shell{width:94%;max-width:1440px;height:92%;max-height:960px;display:flex;flex-direction:column;padding:0;background-color:#17282df5;border:1px #6c8b8370;}
            .heading{display:flex;align-items:center;justify-content:space-between;flex-shrink:0;padding:17px 22px;border-bottom:1px #526a6050;background-color:#1c3035;}
            .heading-copy{display:flex;align-items:center;gap:20px;}.brand{font-size:11px;letter-spacing:2px;color:#91b9ac;}
            .heading .mc-button{min-width:40px;width:40px;height:34px;padding:4px;font-size:18px;}
            .body{flex:1;min-height:0;overflow-y:auto;overflow-x:hidden;padding:18px 22px;}
            .workspace{display:flex;flex:1;min-height:0;}.rail{width:154px;flex-shrink:0;padding:20px 13px;background-color:#14252a;border-right:1px #526a6050;}
            .rail-title{font-size:10px;color:#86a49c;letter-spacing:2px;margin:0 8px 14px;}
            .rail .mc-tab{display:block;width:100%;height:auto;min-height:40px;text-align:left;margin-bottom:6px;padding:10px 12px;}
            .rail-note{font-size:12px;color:#8fa69f;line-height:1.7;margin:24px 8px 0;}
            .section-head{display:flex;align-items:center;justify-content:space-between;margin:18px 0 10px;}.section-head h2{margin:0;}
            .eyebrow{display:block;font-size:10px;letter-spacing:2px;color:#9cc8b6;margin-bottom:8px;}
            .muted{font-size:12px;color:#9caea9;}.accent{color:#b9ddbd;}.gold{color:#edcb8f;}
            .row{gap:10px;}.grid{display:flex;flex-wrap:wrap;gap:10px;align-items:stretch;}
            .tile{width:31.8%;min-width:165px;flex-grow:1;padding:14px;word-break:break-word;}
            .mc-card{background-color:#22373b;border:1px #78988b38;padding:14px;margin:0;}
            .mc-button,.mc-tab{font-size:13px;font-weight:normal;min-width:72px;height:34px;padding:7px 12px;background-color:#2b4447;color:#e8eeea;border:1px #82a29350;}
            .mc-button:hover,.mc-tab:hover{background-color:#426359;border-color:#bbd9ba;color:#fff;}
            .mc-button:active,.mc-tab:active{background-color:#172c30;}.mc-button:focus,.mc-tab:focus{border-color:#f1d5a3;}
            .mc-button:disabled{background-color:#243338;color:#708580;border-color:#41534c;}
            .primary,.mc-tab.selected{background-color:#aacfb1;color:#15342b;border-color:#c7e1be;}.primary:hover{background-color:#c6dfbe;color:#102b24;}
            .mc-slot{width:42px;height:42px;margin:0;background-color:#13292d;border:1px #aacfb140;flex-shrink:0;}
            .mc-badge{font-size:10px;padding:3px 7px;background-color:#29473e;color:#b9ddbd;}
            .mc-input,textarea,select{height:35px;font-size:14px;background-color:#10262b;border:1px #6b9387;padding:6px 9px;}
            .mc-slider{height:24px;margin:14px 0;}slidertrack{height:6px;background-color:#142b30;margin-top:8px;}sliderbar{width:14px;height:22px;background-color:#b9ddbd;border:1px #e2efd6;}
            .mc-tabs{gap:5px;margin-bottom:14px;flex-wrap:wrap;}.status{flex-shrink:0;padding:10px 22px;border-top:1px #78988b38;color:#b2c7bd;font-size:12px;}
            .stat{width:48%;flex-grow:1;}.rule{border-top:1px #78988b38;margin:14px 0;}
            .hero{display:flex;position:relative;overflow:hidden;background-color:#24413e;border:1px #7e9e8c50;min-height:160px;}
            .hero-copy{width:44%;padding:22px;flex-shrink:0;}.hero-copy h2{font-size:25px;color:#eff4df;margin-bottom:12px;}.hero-art{width:56%;height:auto;}
            .data-line{display:flex;justify-content:space-between;gap:10px;padding:8px 0;border-bottom:1px #78988b25;font-size:12px;}
            .number{font-size:27px;color:#e4d4af;}.aside{width:228px;flex-shrink:0;margin-left:18px;}.aside .mc-card{margin-bottom:12px;}
            scrollbarvertical{width:5px;background-color:#172a2e;}scrollbarvertical sliderbar{width:5px;background-color:#7fa899;border:0;}
            @media (max-width:1000px){.rail{width:122px;padding:14px 8px;}.aside{width:190px;margin-left:12px;}.body{padding:14px;}.hero-copy{padding:17px;}.tile{min-width:150px;}.heading{padding:13px 16px;}}
            @media (max-width:760px){.shell{width:98%;height:97%;}.rail{display:none;}.aside{display:none;}.heading{padding:10px 14px;}.brand{display:none;}h1{font-size:21px;}.hero-copy h2{font-size:21px;}.hero-copy{padding:14px;}.tile{width:47%;}.body{padding:12px;}}
            @media (max-height:530px){.heading{padding-top:9px;padding-bottom:9px;}.hero{min-height:120px;}.hero-copy{padding:12px;}.section-head{margin-top:12px;}}
            """ + (body.contains("builtin:atlas-village")?".hero-art{decorator:image(\"builtin:atlas-village\" cover);}":"") + css + "</style></head><body>" + body + "</body></html>";
    }
    static String heading(String title) {
        return "<header class=\"heading\"><div class=\"heading-copy\"><span class=\"brand\">YUDREAM / 方块生活</span><h1>" + escape(title)
            + "</h1></div><mc-button id=\"close\" action=\"ui:close\" aria-label=\"关闭\">×</mc-button></header>";
    }
    static String art(String name,String css) {
        return "<img class=\""+css+"\" src=\"builtin:atlas-"+name+"\" alt=\"\"/>";
    }
    static String hero(String eyebrow,String title,String copy) {
        return "<section class=\"hero\"><div class=\"hero-copy\"><span class=\"eyebrow\">"+escape(eyebrow)+"</span><h2>"+escape(title)+"</h2><p class=\"muted\">"+escape(copy)+"</p></div><div class=\"hero-art\">"+art("village","hidden")+"</div></section>";
    }
    static YudreamUiApi.Document document(String title, String html, JsonObject state, Set<String> actions) {
        return new YudreamUiApi.Document(title, html, new JsonArray(), state, actions);
    }
}
