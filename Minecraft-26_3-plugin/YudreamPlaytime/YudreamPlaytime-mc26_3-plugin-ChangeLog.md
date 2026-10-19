# YudreamPlaytime ChangeLog（正式版）

格式：`[正式版x_x_x] yyyy.mm.dd` 或 `[正式版x_x_x -> y_y_y] yyyy.mm.dd`，下面列本次改动。
测试期的逐版本改动见同目录的 `YudreamPlaytime-mc26_3-plugin-ChangeLog-Beta.md`。

---

[正式版1_0_0] 2026.10.19
1.新增主菜单功能项「我的在线时长」：图标是查看者自己的头（`PLAYER_HEAD` + 所有者 = 查看者），
   悬停显示玩家名、在线时长、挂机时长（`hh小时mm分钟`），点击无效，默认排在功能区（`order: 10`）
2.新增本地时长台账读取：读同机 `plugins/YudreamMinecraftServer/playtime.yml`，按文件修改时间缓存；
   口径与 YuDream Admin 后端一致（含当前未结算区间），后端不可用时照样显示；没有记录时显示 `0分钟`
3.新增 `config.yml`：`stats-file`（自定义台账路径）、`cache-seconds`（缓存秒数）、`order`（功能区排序）
4.依赖：MainMenu `Beta_1_1_0` 起（按查看者渲染）；MainMenu 缺失时插件正常加载但不注册功能
