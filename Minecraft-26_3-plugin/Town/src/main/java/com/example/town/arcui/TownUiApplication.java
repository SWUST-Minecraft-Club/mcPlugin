package com.example.town.arcui;

import com.fentai.arcmenu.api.ArcMenuApplication;
import com.fentai.arcmenu.api.ArcMenuApplicationContext;
import com.fentai.arcmenu.api.ArcMenuApplicationSession;

/** Town 界面应用：每次 openApplication 产出一个会话。 */
public final class TownUiApplication implements ArcMenuApplication {

    private final com.example.town.TownPlugin plugin;

    public TownUiApplication(com.example.town.TownPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ArcMenuApplicationSession open(ArcMenuApplicationContext ctx) {
        return new UiSession(plugin, ctx);
    }
}
