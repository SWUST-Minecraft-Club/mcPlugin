package com.example.joinannounce;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * 26.3 的 dialog 弹窗（notice 类型：可选徽章图标 + 多行正文 + 一个关闭按钮）。
 *
 * <p>用运行时 {@link Dialog#create} 构造，不需要 bootstrapper / 数据包，也不需要把 dialog
 * 注册进注册表；{@link Player#showDialog} 只发给指定玩家。
 *
 * <p>徽章图标：把徽章画进 64x64 皮肤「面部」的自定义头颅——皮肤贴图走 textures 属性里的
 * URL（{@code announce.dialog.icon.texture-url}），由玩家客户端自行下载；服务端只下发属性。
 * 关闭按钮的点击回调按「动作实例」注册，这里缓存同一个 {@link ActionButton} 反复使用，
 * 避免每次进服都新建一条长效回调。
 */
final class AnnouncementDialog {

    private static final String BADGE_ID = "joinannounce:badge";
    private static final String BADGE_NAME = "SWUSTMG";

    private ActionButton closeButton;
    private Component closeButtonLabel;

    void show(final Player player, final Component title, final Component buttonLabel,
              final List<Component> bodyLines, final AnnouncementSettings.DialogSettings settings) {
        final List<DialogBody> body = new ArrayList<>();
        final DialogBody icon = iconBody(settings);
        if (icon != null) {
            body.add(icon);
        }
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

    /** 徽章图标：按 icon.source 决定走资源包贴图还是皮肤头颅；未启用时返回 null */
    private DialogBody iconBody(final AnnouncementSettings.DialogSettings settings) {
        if (!settings.iconEnabled()) {
            return null;
        }
        final ItemStack item = switch (settings.iconSource()) {
            case PACK -> new ItemStack(Material.STRUCTURE_VOID);
            case HEAD -> headItem(settings);
        };
        return DialogBody.item(item)
                .showDecorations(false)
                .showTooltip(false)
                .width(settings.iconSize())
                .height(settings.iconSize())
                .build();
    }

    /**
     * pack 来源：资源包把 STRUCTURE_VOID 的模型覆盖成徽章贴图（见
     * {@code assets/pack/assets/minecraft/items/structure_void.json}），没装资源包时是没有贴图的物品。
     */
    private ItemStack headItem(final AnnouncementSettings.DialogSettings settings) {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta meta = head.getItemMeta();
        if (!(meta instanceof final SkullMeta skull)) {
            return head;
        }
        final UUID profileId = UUID.nameUUIDFromBytes(BADGE_ID.getBytes(StandardCharsets.UTF_8));
        final String textures = "{\"timestamp\":0,\"profileId\":\"" + profileId.toString().replace("-", "")
                + "\",\"profileName\":\"" + BADGE_NAME + "\",\"isPublic\":true,"
                + "\"textures\":{\"SKIN\":{\"url\":\"" + settings.iconUrl() + "\"}}}";
        final PlayerProfile profile = Bukkit.createProfile(profileId, BADGE_NAME);
        profile.setProperty(new ProfileProperty("textures",
                Base64.getEncoder().encodeToString(textures.getBytes(StandardCharsets.UTF_8))));
        skull.setPlayerProfile(profile);
        head.setItemMeta(skull);
        return head;
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
