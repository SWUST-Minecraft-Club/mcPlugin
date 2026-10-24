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
import org.bukkit.entity.Player;

/**
 * 26.3 的 dialog 弹窗（notice 类型：标题 + 多行正文 + 一个关闭按钮）。
 *
 * <p>用运行时 {@link Dialog#create} 构造，不需要 bootstrapper / 数据包，也不需要把 dialog
 * 注册进注册表；{@link Player#showDialog} 只发给指定玩家。
 *
 * <p>关闭按钮的点击回调按「动作实例」注册，所以这里缓存同一个 {@link ActionButton} 反复使用，
 * 避免每次进服都新建一个长效回调（无限次使用 + 36500 天寿命，新建就会多留一条记录）。
 */
final class AnnouncementDialog {

    private ActionButton closeButton;
    private Component closeButtonLabel;

    void show(final Player player, final Component title, final Component buttonLabel,
              final List<Component> bodyLines, final AnnouncementSettings.DialogSettings settings) {
        final List<DialogBody> body = new ArrayList<>(bodyLines.size());
        for (final Component line : bodyLines) {
            body.add(DialogBody.plainMessage(line, settings.width()));
        }

        final Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .externalTitle(title)
                        .canCloseWithEscape(settings.canCloseWithEscape())
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(body)
                        .build())
                .type(DialogType.notice(button(buttonLabel))));

        player.showDialog(dialog);
    }

    /** 空回调：按钮点击本身不做服务端操作，界面由 afterAction(CLOSE) 关闭 */
    private synchronized ActionButton button(final Component label) {
        if (this.closeButton == null || !label.equals(this.closeButtonLabel)) {
            this.closeButtonLabel = label;
            this.closeButton = ActionButton.builder(label)
                    .width(200)
                    .action(DialogAction.customClick(
                            (response, audience) -> {
                            },
                            ClickCallback.Options.builder()
                                    .uses(ClickCallback.UNLIMITED_USES)
                                    .lifetime(Duration.ofDays(365L * 100L))
                                    .build()))
                    .build();
        }
        return this.closeButton;
    }
}
