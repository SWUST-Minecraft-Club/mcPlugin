package com.example.mainmenurepair.util;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;

/** 仅识别带手册字体的整页正文；普通 Dialog 的布局、按钮和焦点均保持原生行为。 */
public final class HandbookDialogLayout {
    private HandbookDialogLayout() { }

    public static boolean isBook(Component message) {
        return message.visit((style, value) -> {
            if (style.getFont() instanceof FontDescription.Resource font && font.id().getNamespace().equals("handbookcore"))
                return java.util.Optional.of(true);
            return java.util.Optional.empty();
        }, Style.EMPTY).orElse(false);
    }

    public static void center(Screen screen) {
        if (!(screen instanceof DialogScreen<?>)) return;
        try {
            Field field = DialogScreen.class.getDeclaredField("bodyScroll");
            field.setAccessible(true);
            ScrollableLayout body = (ScrollableLayout) field.get(screen);
            if (body == null) return;
            AtomicBoolean book = new AtomicBoolean();
            body.visitWidgets(widget -> { if (containsBook(widget)) book.set(true); });
            if (!book.get()) return;
            body.setX((screen.width - body.getWidth()) / 2);
            body.setY(Math.max(4, (screen.height - body.getHeight()) / 2));
            // 书页内已有合上按钮与 ESC；移除多余的原生灰色页脚按钮。
            for (var child : screen.children()) if (child instanceof Button button) {
                button.visible = false;
                button.active = false;
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            StatusLog.append("手册居中未应用：" + error.getClass().getSimpleName());
        }
    }
    private static boolean containsBook(GuiEventListener child) {
        if (child instanceof FocusableTextWidget text && isBook(text.getMessage())) return true;
        if (child instanceof ContainerEventHandler container)
            for (GuiEventListener nested : container.children()) if (containsBook(nested)) return true;
        return false;
    }
}
