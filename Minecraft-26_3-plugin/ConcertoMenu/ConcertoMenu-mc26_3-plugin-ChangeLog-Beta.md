# ConcertoMenu ChangeLog（测试版）

版本命名与产物见 `./docs/dev/版本与产物规范.md`。
格式：`[新版本] 时间` 或 `[上一版本 -> 新版本] 时间`，下面列本次改动（新增 / 修复 / 调整）。

---

[Beta_0_1_0] 2026.10.20 21:00:00
1.新增：主菜单功能项「音乐播放器」（唱片机图标）—— 点击向客户端发送 `concerto:menu` 通道的 `open` 指令，
   装了 Concerto 客户端模组（2.2.0+）的玩家会直接打开 Concerto 主界面；悬停写明需要安装模组
2.新增：独立通道 `concerto:menu`（不占用 Concerto 的 `concerto:main` 协议、不改其协议版本），
   负载为 `VarInt 长度 + UTF-8` 的 `open`，与客户端 `StreamCodec.readUtf` 对齐；没装模组的客户端忽略该负载
3.配套：Concerto 客户端 2.2.0 新增 `MenuPayload` 接收器，收到 `open` 时执行与按 `I` 相同的
   `setScreen(new ConcertoIndexScreen(null))`
