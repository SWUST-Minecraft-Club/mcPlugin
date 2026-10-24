package com.example.town.arcui;

/**
 * 界面度量常量。canvas 设计像素 480×270，ppb 固定 42.7（ArcMenu 官方默认；
 * designing 技能实测：其他 ppb 值会导致鼠标光标不可见，务必保持）。
 * 文字行高 / 锚点偏移是经验值，测试服用 /town admin uical 截图校准后在此统一调整。
 * 渲染参考 docs/exp/arcmenu.md 的实测规则（z 透视、文本锚点）。
 */
public final class UiMetrics {

    private UiMetrics() {
    }

    /** canvas 宽（设计像素）。 */
    public static final double CANVAS_W = 480;
    /** canvas 高（设计像素）。 */
    public static final double CANVAS_H = 270;
    /** 每 block 的设计像素：必须 42.7（与 ArcMenu 光标渲染绑定）。 */
    public static final double PXB = 42.7;
    /** 屏幕与玩家的设计距离（block），与 designing 技能标定条件一致。 */
    public static final double DESIGN_DISTANCE = 2.0;

    /** z 透视系数：离平面越近视觉越大，scale(z) ≈ 1 + Z_K×z（designing 技能实测）。 */
    public static final double Z_SCALE_K = 0.0115;

    /** TextDisplay 在 scale=1 时的一行文字高度（block）；联调校准点。 */
    public static final double LINE_BLOCKS = 0.10;
    /** 文字垂直锚点补偿（设计像素）：正值往下移；联调校准点。 */
    public static final double TEXT_Y_NUDGE = 2.0;

    /** 设计像素 → 世界 block。 */
    public static double px(double pixels) {
        return pixels / PXB;
    }

    /** 指定像素字号对应的 TextDisplay 缩放。 */
    public static float textScale(double fontPx) {
        return (float) (px(fontPx) / LINE_BLOCKS);
    }
}
