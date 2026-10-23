# YudreamPlaytime ChangeLog（测试版）

版本命名与产物见 `./docs/dev/版本与产物规范.md`。
格式：`[新版本] 时间` 或 `[上一版本 -> 新版本] 时间`，下面列本次改动（新增 / 修复 / 调整）。

---

[Beta_0_1_0] 2026.10.19 13:50:00
1.新增：主菜单功能项「我的在线时长」—— 图标为查看者自己的头，悬停显示玩家名、在线时长、AFK 时长
   （`hh小时mm分钟`），点击无效，默认排在功能区（`order: 10`）
2.新增：读取本机 yudream-minecraft-server 的本地时长台账（`plugins/YudreamMinecraftServer/playtime.yml`），
   按文件修改时间缓存；没有记录时显示 `0分钟`，不依赖后端是否在线
3.新增：`config.yml` 支持 `stats-file`（自定义台账路径）、`cache-seconds`（缓存秒数）、`order`（功能区排序）
4.说明：依赖 MainMenu 的「按查看者渲染」（MainMenu `Beta_1_1_0` 起支持）；MainMenu 缺失时插件正常加载但不注册功能

---

[Beta_0_1_0 -> 正式版1_0_0] 2026.10.19
测试期结束，整理为正式版 `1_0_0`（第一位 +1、后两位归 0、去掉 `Beta`），
正式版改动汇总见同目录的 `YudreamPlaytime-mc26_3-plugin-ChangeLog.md`。

---

[正式版1_0_0 -> Beta_1_1_0] 2026.10.22 23:45:00
1.新增：适配 Folia 26.3 —— `paper-plugin.yml` 声明 `folia-supported: true`，编译依赖改为 Folia API
2.修复：本地时长台账缓存加同步，多个区域线程同时渲染主菜单时不会并发读写同一份 HashMap
