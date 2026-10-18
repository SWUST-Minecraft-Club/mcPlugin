# RealTime ChangeLog（正式版）

格式：`[正式版x_x_x] yyyy.mm.dd` 或 `[正式版x_x_x -> y_y_y] yyyy.mm.dd`，下面列本次改动。
测试期的逐版本改动见同目录的 `RealTime-mc26_3-plugin-ChangeLog-Beta.md`。

---

[正式版1_0_0] 2026.10.18
1.新增主菜单第 1 行第 2 格的“当前时间”显示（时区与格式可配，默认东 8 区）
2.新增配置项：显示物品与名称、悬停文本模板（`{time}` 占位）、时间格式、时区偏移、固定位提示语
3.新增：作为独立功能插件接入 MainMenu（`preferredSlot(1)` 占位），时间靠菜单周期性重绘保持实时
