package com.example.mainmenu.api;

/**
 * 功能项最终落在菜单里的位置（由主菜单在注册时计算，供 /cd debug 与文档说明用）。
 *
 * @param id                  功能项 id
 * @param slot                最终槽位（0~53）
 * @param page                所在页（从 1 开始；自定义位置不在功能区，固定为 0）
 * @param customPosition      是否按插件自定义位置放置
 * @param movedToFunctionArea 自定义位置冲突被改排到功能区（悬停文本会追加提示）
 */
public record FeaturePlacement(
        String id,
        int slot,
        int page,
        boolean customPosition,
        boolean movedToFunctionArea
) {
}
