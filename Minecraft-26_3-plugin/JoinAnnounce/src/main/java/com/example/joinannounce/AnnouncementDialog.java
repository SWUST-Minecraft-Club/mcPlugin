package com.example.joinannounce;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;

/**
 * 26.3 的 dialog 弹窗（标题 + 多行正文 + 主按钮，可选第二个「外部链接」按钮）。
 *
 * <p>用运行时 {@link Dialog#create} 构造，不需要 bootstrapper / 数据包，也不需要把 dialog
 * 注册进注册表；{@link Player#showDialog} 只发给指定玩家。
 *
 * <p>只有一个按钮时用 notice 类型；配了更新日志链接时用 multiAction（2 列）并排显示，
 * 「开始冒险」按钮按 {@code button-width} 更宽，链接按钮按 {@code link-width} 更窄。
 *
 * <p>主按钮的点击回调按「动作实例」注册，所以这里缓存同一个 {@link ActionButton} 反复使用，
 * 避免每次进服都新建一个长效回调（无限次使用 + 36500 天寿命，新建就会多留一条记录）。
 * 链接按钮走静态点击事件（客户端直接开浏览器），不注册回调。
 */
final class AnnouncementDialog {

    private ActionButton primaryButton;
    private Component primaryLabel;
    private int primaryWidth;

    void show(final Player player, final Component title, final Component buttonLabel,
              final Component linkLabel, final List<Component> bodyLines,
              final AnnouncementSettings.DialogSettings settings) {
        final List<DialogBody> body = new ArrayList<>(bodyLines.size());
        for (final Component line : bodyLines) {
            body.add(DialogBody.plainMessage(line, settings.width()));
        }

        final ActionButton primary = primaryButton(buttonLabel, settings.buttonWidth());
        final ActionButton link = linkButton(linkLabel, settings);

        final Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .externalTitle(title)
                        .canCloseWithEscape(settings.canCloseWithEscape())
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(body)
                        .build())
                .type(link == null
                        ? DialogType.notice(primary)
                        : DialogType.multiAction(List.of(primary, link), null, 2)));

        player.showDialog(dialog);
    }

    /** 主按钮：空回调，点击后界面由 afterAction(CLOSE) 关闭 */
    private synchronized ActionButton primaryButton(final Component label, final int width) {
        if (this.primaryButton == null || !label.equals(this.primaryLabel) || this.primaryWidth != width) {
            this.primaryLabel = label;
            this.primaryWidth = width;
            this.primaryButton = ActionButton.builder(label)
                    .width(width)
                    .action(DialogAction.customClick(
                            (response, audience) -> {
                            },
                            ClickCallback.Options.builder()
                                    .uses(ClickCallback.UNLIMITED_USES)
                                    .lifetime(Duration.ofDays(365L * 100L))
                                    .build()))
                    .build();
        }
        return this.primaryButton;
    }

    /** 「查看完整更新日志」：静态点击事件，客户端直接在浏览器打开链接 */
    private ActionButton linkButton(
            final Component label, final AnnouncementSettings.DialogSettings settings) {
        if (settings.linkUrl() == null || label == null) {
            return null;
        }
        return ActionButton.builder(label)
                .width(settings.linkWidth())
                .tooltip(Component.text("在浏览器中打开更新日志"))
                .action(DialogAction.staticAction(ClickEvent.openUrl(settings.linkUrl().toString())))
                .build();
    }
}
