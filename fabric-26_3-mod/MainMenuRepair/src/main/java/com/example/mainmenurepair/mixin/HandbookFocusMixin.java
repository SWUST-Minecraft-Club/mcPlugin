package com.example.mainmenurepair.mixin;

import net.minecraft.client.gui.components.FocusableTextWidget;
import com.example.mainmenurepair.util.HandbookDialogLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 手册仍可用鼠标和键盘交互；只抑制整幅图片正文的焦点轮廓。 */
@Mixin(FocusableTextWidget.class)
public abstract class HandbookFocusMixin {
    @Redirect(method = "extractWidgetRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/FocusableTextWidget;isFocused()Z"), require = 0)
    private boolean handbookRepair$focusOutline(FocusableTextWidget widget) {
        return !HandbookDialogLayout.isBook(widget.getMessage()) && widget.isFocused();
    }
}
