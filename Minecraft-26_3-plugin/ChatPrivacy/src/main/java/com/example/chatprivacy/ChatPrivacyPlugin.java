package com.example.chatprivacy;

import com.example.chatprivacy.command.ChatPrivacyCommand;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.concurrent.atomic.LongAdder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ChatPrivacy：把玩家公开聊天改写为无签名系统消息，使客户端无法把它作为聊天举报证据。
 *
 * <p>核心思路：在 {@link io.papermc.paper.event.player.AsyncChatEvent} 取消原版广播，
 * 再用 Paper 的 {@link io.papermc.paper.chat.ChatRenderer} 按查看者渲染，最后以系统消息发给各玩家。
 * 系统消息不携带聊天签名，因此无法被客户端举报；原始签名聊天包不会转发给任何客户端。
 */
public final class ChatPrivacyPlugin extends JavaPlugin {

    /** 配置快照在 reload 时整体替换，跨区域线程读取，必须 volatile */
    private volatile ChatPrivacySettings settings;

    private final LongAdder rewrittenMessages = new LongAdder();
    private final LongAdder signedMessages = new LongAdder();
    private final LongAdder unsignedMessages = new LongAdder();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.settings = ChatPrivacySettings.load(getConfig());

        getServer().getPluginManager().registerEvents(new ChatRewriteListener(this), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(ChatPrivacyCommand.create(this), "聊天隐私管理（status / reload）"));

        SecureProfileSupport.logStatus(this, this.settings);

        getComponentLogger().info(Component.text(
                this.settings.enabled()
                        ? "已启用：玩家公开聊天将以无签名系统消息转发（无法被举报）"
                        : "已启用：聊天防举报当前为关闭状态（原版签名聊天）",
                this.settings.enabled() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
    }

    /** 重新读取 config.yml，并重新检查 enforce-secure-profile */
    public void reloadSettings() {
        reloadConfig();
        this.settings = ChatPrivacySettings.load(getConfig());
        SecureProfileSupport.logStatus(this, this.settings);
    }

    public ChatPrivacySettings settings() {
        return this.settings;
    }

    /** 由聊天监听器调用，记录本次启动改写的消息数量 */
    void recordRewritten(final boolean signed) {
        this.rewrittenMessages.increment();
        if (signed) {
            this.signedMessages.increment();
        } else {
            this.unsignedMessages.increment();
        }
    }

    public ChatStats stats() {
        return new ChatStats(
                this.rewrittenMessages.sum(),
                this.signedMessages.sum(),
                this.unsignedMessages.sum());
    }

    /** /chatprivacy status 的输出 */
    public void sendStatus(final CommandSender sender) {
        final ChatPrivacySettings current = this.settings;
        final ChatStats currentStats = this.stats();

        sender.sendMessage(current.prefix().append(Component.text("聊天防举报状态", NamedTextColor.AQUA)));
        sender.sendMessage(line("功能",
                current.enabled() ? "已开启（聊天不可举报）" : "已关闭（原版签名聊天）",
                current.enabled() ? NamedTextColor.GREEN : NamedTextColor.RED));
        sender.sendMessage(line("仅改写基岩版玩家", current.bedrockOnly() ? "是" : "否", NamedTextColor.WHITE));
        sender.sendMessage(line("控制台保留聊天", current.logChat() ? "是" : "否", NamedTextColor.WHITE));

        switch (SecureProfileSupport.read(this)) {
            case TRUE -> sender.sendMessage(line("enforce-secure-profile",
                    "true（建议改为 false 后重启，否则未签名客户端可能无法进入）", NamedTextColor.YELLOW));
            case FALSE -> sender.sendMessage(line("enforce-secure-profile", "false", NamedTextColor.GREEN));
            case UNKNOWN -> sender.sendMessage(line("enforce-secure-profile",
                    "未读取到（跳过检查）", NamedTextColor.GRAY));
        }

        sender.sendMessage(line("本次启动已改写",
                currentStats.rewritten() + " 条（签名 " + currentStats.signed()
                        + " / 未签名 " + currentStats.unsigned() + "）",
                NamedTextColor.WHITE));
        sender.sendMessage(line("NCR 安全标记",
                "第一版不支持（需要 NMS；不影响聊天无法被举报）", NamedTextColor.GRAY));
    }

    private static Component line(final String key, final String value, final NamedTextColor color) {
        return Component.text("  " + key + "：", NamedTextColor.GRAY)
                .append(Component.text(value, color));
    }

    /** 本次启动的改写统计 */
    public record ChatStats(long rewritten, long signed, long unsigned) {
    }
}
