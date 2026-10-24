package com.example.town.arcui;

import org.bukkit.Material;

/** 通用确认弹层：覆盖在当前界面上，确认后先回退再执行动作。 */
public final class ConfirmScreen extends Screen {

    private final String titleMini;
    private final Runnable onConfirm;

    public ConfirmScreen(UiSession ui, String titleMini, Runnable onConfirm) {
        super(ui);
        this.titleMini = titleMini;
        this.onConfirm = onConfirm;
    }

    @Override
    public void draw() {
        panel(60, 70, UiMetrics.CANVAS_W - 120, 130, Material.GRAY_CONCRETE);
        panel(60, 70, UiMetrics.CANVAS_W - 120, 4, Material.RED_CONCRETE);
        label(UiMetrics.CANVAS_W / 2, 110, 13, titleMini);
        label(UiMetrics.CANVAS_W / 2, 132, 9, "<dark_gray>该操作不可撤销，请确认。");
        button(UiMetrics.CANVAS_W / 2 - 130, 156, 120, 32, "<red>确认", 13, () -> {
            ui.back();
            onConfirm.run();
        });
        button(UiMetrics.CANVAS_W / 2 + 10, 156, 120, 32, "<gray>取消", 13, ui::back);
    }

    @Override
    public void onLeave() {
        // 取消 / 确认都只是回退，无额外清理
    }
}
