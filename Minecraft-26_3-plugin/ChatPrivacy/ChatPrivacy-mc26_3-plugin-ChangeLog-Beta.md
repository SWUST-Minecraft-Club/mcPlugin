# ChatPrivacy ChangeLog（测试版）

版本命名与产物见 `./docs/dev/版本与产物规范.md`。
格式：`[新版本] 时间` 或 `[上一版本 -> 新版本] 时间`，下面列本次改动（新增 / 修复 / 调整）。

---

[Beta_0_1_0] 2026.10.25 10:20:13
1.新增聊天防举报插件：监听 Paper 的 `AsyncChatEvent`，把玩家公开聊天改写为无签名系统消息后转发，
  客户端无法把它作为聊天举报证据
2.新增配置项：总开关 `enabled`、仅改写基岩版玩家 `bedrock-only`、控制台保留聊天 `log-chat`、
  检查 `enforce-secure-profile`（`warn-on-secure-profile`）
3.新增 `/chatprivacy status|reload` 与权限 `chatprivacy.admin`
4.调整：按 Folia 区域化调度，玩家消息通过 `player.getScheduler()` 发送；尊重其它插件对事件的取消（禁言等）
5.说明：第一版不包含 NCR 客户端「安全服务器」绿标（需要 NMS 注入 ping 响应），不影响聊天无法被举报
