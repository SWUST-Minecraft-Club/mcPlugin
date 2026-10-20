# Wallet ChangeLog（测试版）

版本命名与产物见 `./docs/dev/版本与产物规范.md`。
格式：`[新版本] 时间` 或 `[上一版本 -> 新版本] 时间`，下面列本次改动（新增 / 修复 / 调整）。

---

[Beta_0_1_0] 2026.10.20 11:00:00
1.新增：主菜单钱包功能（绿宝石）—— 普通玩家悬停逐行显示本服启用货币的余额、点击打开流水页；
   管理员左键打开「玩家货币」页、右键打开「货币种类」页；本服没有启用货币时图标显示「暂无货币」
2.新增：流水页（纸图标）—— 第 1~5 行按时间倒序显示流水（类型/金额/币种/时间/余额/备注/业务号），
   第 6 行 47 上一页、49 返回主菜单、51 下一页；货币种类页用黄绿/红色羊毛表示本服是否启用；
   玩家货币页列出本服有在线时长记录的玩家（读 `plugins/YudreamMinecraftServer/playtime.yml`）及余额
3.新增：`WalletApi` / `WalletService` 给后续插件依赖 —— `assets / balance / credit / debit / transactions`，
   全部返回 `CompletableFuture`；所有加减货币直接调用官网接口（官网流水即上报结果），本地不保存余额，
   只提供 `cachedAssets / cachedBalance` 供渲染路径读取
4.调整：货币与流水全部走官网 `/api/plugins/yudream-wallet`；兼容平台响应信封（`data`）与
   长整型/BigDecimal 的字符串序列化；流水查询先用 `game/balance` 解析站内 `userId` 再查管理端流水
5.说明：依赖 MainMenu 的按查看者渲染与 `MainMenuApi.open(player)`（`Beta_1_1_0` / `Beta_1_2_0` 起）；
   API Key 需要 `plugin:yudream-wallet:manage` 权限；未绑定官网账号的玩家显示「未绑定网站账号」
