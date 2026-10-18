package com.example.mainmenu.internal;

/**
 * 功能项的管理状态（由主菜单的管理界面修改，持久化在 plugins/MainMenu/features.yml）。
 *
 * <p>各项为空表示"用功能插件自己声明的值"：
 * <ul>
 *     <li>{@link #hidden}：隐藏的功能不显示，也不占用它的位置（所以原来位置冲突的插件可以正常显示）</li>
 *     <li>{@link #order}：覆盖功能区的排序值</li>
 *     <li>{@link #position}：覆盖位置（固定位置功能插件的槽位）</li>
 * </ul>
 */
public final class FeatureState {

    private boolean hidden;
    private Integer order;
    private boolean function;
    private Integer position;

    public boolean hidden() {
        return hidden;
    }

    public void hidden(final boolean hidden) {
        this.hidden = hidden;
    }

    public Integer order() {
        return order;
    }

    public void order(final Integer order) {
        this.order = order;
    }

    /**
     * 是否被管理员强制改回非固定位置功能插件。
     *
     * <p>有些功能插件自带 {@code preferredSlot}，光清掉位置覆盖不够 —— 需要这个标记来忽略它声明的位置。
     */
    public boolean forcedFunction() {
        return function;
    }

    public void forcedFunction(final boolean function) {
        this.function = function;
    }

    /**
     * 覆盖的位置（固定位置功能插件的槽位）；null 表示用插件声明的 preferredSlot。
     *
     * <p>改回非固定位置功能插件时<b>不清掉</b>这个值（只靠 {@link #forcedFunction} 忽略它），
     * 这样再用拉杆改回固定位置时能回到原来的位置。
     */
    public Integer position() {
        return position;
    }

    public void position(final Integer position) {
        this.position = position;
    }

    /** 是否没有任何覆盖项（可以不用写进配置文件） */
    public boolean isEmpty() {
        return !hidden && order == null && !function && position == null;
    }
}
