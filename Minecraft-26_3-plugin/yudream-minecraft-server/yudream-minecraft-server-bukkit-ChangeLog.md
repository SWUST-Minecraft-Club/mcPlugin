# yudream-minecraft-server ChangeLog（正式版）

格式：`[正式版x_x_x] yyyy.mm.dd` 或 `[正式版x_x_x -> y_y_y] yyyy.mm.dd`，下面列本次改动。
测试期的逐版本改动见同目录的 `yudream-minecraft-server-bukkit-ChangeLog-Beta.md`。

---

[正式版2_0_0] 2026.10.19
1.新增本地在线时长统计：口径与 YuDream Admin 后端逐条对齐（已结算区间 + 当前未结算区间，
   AFK 单独累计、不从在线时长里扣），后端不可用或未配置时也照常记账；落盘在插件数据目录的 `playtime.yml`
2.新增待发上报落盘与补发：进服 / 退服 / AFK 起止先写 `pending-reports.jsonl`，
   后端恢复后按原始时间戳按序补发（join / quit / AFK 幂等，不会重复计时），进程重启不丢
3.新增本地台账定期与后端对齐（`local-playtime.sync-from-backend`）：后端有记录时以后端为准，
   已结算累计 + 当前在线区间 + 当前挂机区间一起采用；本地领先（上报还在补发）时保留本地那一段
4.修复玩家列表接口链路：改走管理端 `/admin/servers/{id}/players`（需 `plugin:minecraft-server:manage`），
   任意非 2xx 回退用户端路径并记住可用路径；响应信封 `data` 解包；长整型字符串兼容；
   `*At(now)` 口径还原为已结算值（避免当前这段被算两遍）
5.新增命令：`/yudreammc playtime [玩家]`（本地 + 后端侧数字对照）、`/yudreammc syncplaytime`（立即对齐一次）；
   `status` 增加插件版本、本地统计开关、对齐间隔与最近一次对齐结果
6.调整：源码从官方 1.6.2 产物的反编译基线纳入本仓，改为 Gradle 工程构建
   （编译目标 spigot-api 1.13.2、Java 8 字节码；产物沿用 `yudream-minecraft-server-bukkit-<版本>.jar` 命名）
7.说明：下游（代理）模式不做后端对齐（凭证在代理侧，本机拉不到列表）；
   聊天 / 死亡 / 成就这类消息型事件保持原来的尽力而为语义
