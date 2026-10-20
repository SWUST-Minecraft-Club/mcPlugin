package com.example.concertomenu;

import com.example.mainmenu.api.MainMenuApi;
import com.example.mainmenu.api.MenuFeatureRegistry;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ConcertoMenu：把 Concerto 客户端菜单挂进 MainMenu（唱片机图标）。
 *
 * <p>点击时向客户端发 {@code concerto:menu} 通道的 {@code open} 指令（VarInt 长度 + UTF-8，
 * 与 Concerto 客户端 {@code StreamCodec} 的 readUtf 对齐）；Concerto 客户端（2.2.0 起）收到后
 * 打开自己的主界面。没装模组的客户端会忽略这个自定义负载，点击无副作用。
 */
public final class ConcertoMenuPlugin extends JavaPlugin {

    /** 服务端 → 客户端的界面指令通道（独立于 Concerto 自己的 concerto:main 协议）。 */
    static final String CHANNEL = "concerto:menu";
    static final String COMMAND_OPEN = "open";

    private ConcertoMenuFeature feature;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        this.feature = new ConcertoMenuFeature(this, getConfig().getInt("order", 30));

        if (MainMenuApi.isAvailable()) {
            MainMenuApi.registry().register(this.feature);
            getLogger().info("已注册主菜单功能：Concerto 音乐界面（唱片机图标）。");
        } else {
            getLogger().warning("MainMenu 未启用：ConcertoMenu 未注册（主菜单装好后重启即可）。");
        }
    }

    @Override
    public void onDisable() {
        final MenuFeatureRegistry registry = MainMenuApi.registryOrNull();
        if (registry != null) {
            try {
                registry.unregister("concertomenu:menu");
            } catch (RuntimeException ignored) {
                // MainMenu 可能已经在关闭
            }
        }

        try {
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
        } catch (RuntimeException ignored) {
            // 通道可能已被服务端回收
        }
    }

    /** 让该玩家的客户端打开 Concerto 主界面。 */
    void openMenu(final Player player) {
        player.sendPluginMessage(this, CHANNEL, encode(COMMAND_OPEN));
    }

    /** 与 {@code FriendlyByteBuf.writeUtf} 一致的编码：VarInt 长度 + UTF-8 字节。 */
    private static byte[] encode(final String text) {
        final byte[] raw = text.getBytes(StandardCharsets.UTF_8);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int value = raw.length;
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
        out.write(raw, 0, raw.length);
        return out.toByteArray();
    }
}
