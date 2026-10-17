package com.example.mainmenu;

import com.example.mainmenu.command.CdCommand;
import io.papermc.paper.datapack.DatapackRegistrar;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import io.papermc.paper.dialog.DialogResponseView;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 引导类：在服务端启动早期完成两件必须在“注册表冻结之前”做的事。
 *
 * <ol>
 *     <li><b>让 ESC 菜单出现「主菜单」按钮</b>：注册一个 dialog（{@code mainmenu:main_menu}），
 *         并通过内置数据包把它加进 {@code minecraft:pause_screen_additions} 标签。
 *         客户端会在暂停界面为这个标签里的每个 dialog 生成一个按钮，按钮文字取 dialog 的
 *         {@code externalTitle}，点击后打开该 dialog。</li>
 *     <li><b>注册点击回调</b>：dialog 里的「打开主菜单」按钮使用自定义点击动作，
 *         点击后由服务端回调打开 6×9 箱子菜单（{@link com.example.mainmenu.menu.MenuService#open(Player)}）。</li>
 * </ol>
 *
 * <p>注意：引导阶段拿不到 config.yml，所以这里的文案是固定的。
 */
public final class MainMenuBootstrap implements PluginBootstrap {

    /** dialog 在注册表里的键 */
    public static final Key DIALOG_KEY = Key.key("mainmenu", "main_menu");

    /** 内置数据包名 */
    public static final String PACK_NAME = "mainmenu";

    /** 暂停界面按钮与 dialog 标题 */
    private static final Component TITLE = Component.text("主菜单").color(NamedTextColor.GOLD);

    @Override
    public void bootstrap(final BootstrapContext context) {
        final LifecycleEventManager<BootstrapContext> manager = context.getLifecycleManager();

        // 1) 注册内置数据包：pack/data/minecraft/tags/dialog/pause_screen_additions.json
        manager.registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            final URL resource = MainMenuBootstrap.class.getResource("/pack");
            if (resource == null) {
                context.getLogger().warn("未找到内置数据包（/pack），ESC 菜单按钮不可用");
                return;
            }
            try {
                final DatapackRegistrar registrar = event.registrar();
                registrar.discoverPack(resource.toURI(), PACK_NAME);
                context.getLogger().info("已注册内置数据包：" + PACK_NAME);
            } catch (final Exception exception) {
                context.getLogger().warn("注册内置数据包失败：" + exception.getMessage());
            }
        });

        // 2) 注册 dialog，并声明它使用 pause_screen_additions 标签
        manager.registerEventHandler(RegistryEvents.DIALOG.compose().newHandler(event -> {
            event.registry().register(
                    TypedKey.create(RegistryKey.DIALOG, DIALOG_KEY),
                    builder -> builder
                            .base(DialogBase.builder(TITLE)
                                    .externalTitle(TITLE)
                                    .canCloseWithEscape(true)
                                    .pause(false)
                                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                                    .body(List.of(DialogBody.plainMessage(
                                            Component.text("点击下方按钮打开主菜单（也可以直接输入 /cd）。"), 200)))
                                    .build())
                            .type(DialogType.multiAction(List.of(openButton()))
                                    .exitAction(null)
                                    .columns(1)
                                    .build()));
            event.getOrCreateTag(DialogTagKeys.PAUSE_SCREEN_ADDITIONS);
        }));
    }

    private static ActionButton openButton() {
        return ActionButton.builder(Component.text("打开主菜单"))
                .tooltip(Component.text("打开 6×9 功能菜单"))
                .width(200)
                // 注意：ClickCallback 默认只能使用 1 次、默认寿命 12 小时。
                // 不显式放开的话，ESC 菜单按钮就只有第一次点得开。
                .action(DialogAction.customClick(
                        MainMenuBootstrap::handleOpenClick,
                        ClickCallback.Options.builder()
                                .uses(ClickCallback.UNLIMITED_USES)
                                .lifetime(Duration.ofDays(365L * 100L))
                                .build()))
                .build();
    }

    /** dialog 按钮被点击：在主线程打开菜单（延迟 1 tick，让客户端的 dialog 先关闭，避免界面被顶掉） */
    static void handleOpenClick(final DialogResponseView response, final Audience audience) {
        if (!(audience instanceof Player player)) {
            return;
        }
        final MainMenuPlugin plugin = MainMenuPlugin.instance();
        if (plugin == null) {
            return;
        }
        plugin.getLogger().info("收到 dialog 按钮点击（玩家 " + player.getName() + "），准备打开主菜单");
        if (Bukkit.isPrimaryThread()) {
            CdCommand.openNow(plugin, player);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> CdCommand.openNow(plugin, player));
        }
    }

    /** 供调试输出使用 */
    public static TypedKey<io.papermc.paper.dialog.Dialog> dialogKey() {
        return TypedKey.create(RegistryKey.DIALOG, DIALOG_KEY);
    }
}
