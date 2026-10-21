# Wallet

主菜单里的钱包功能，同时给后续插件提供钱包 API。**货币、余额、流水全部以 YuDream 官网为准**
（接口前缀 `/api/plugins/yudream-wallet`），本插件不在本地保存余额，只做短时缓存用于菜单显示。

## 菜单里长什么样

主菜单里的**绿宝石**图标（按查看者渲染）：

| 玩家 | 操作 | 效果 |
| --- | --- | --- |
| 普通玩家 | 悬停 | 逐行显示本服启用货币的余额（按货币的 `scale` 格式化；未绑定官网账号显示「未绑定网站账号」） |
| 普通玩家 | 左键 | 打开**流水页**：第 1~5 行是流水（纸图标，按时间倒序，**只显示本服启用的币种**），第 6 行 **47 上一页 / 49 返回主菜单 / 51 下一页** |
| 管理员（`wallet.admin`） | 左键 | 打开**玩家货币页**：列出在本服有在线时长记录的玩家（读 `plugins/YudreamMinecraftServer/playtime.yml`）及各自余额 |
| 管理员（`wallet.admin`） | 右键 | 打开**货币类型页**：黄绿色羊毛 = 本服启用且官网启用，红色羊毛 = 未启用/官网停用；**点击羊毛切换本服启用状态**（写 `plugins/Wallet/assets.yml`） |

本服没有任何启用货币时，图标显示**暂无货币**，点击只提示不打开界面。

## 配置

```yaml
base-url: "https://www.swustmc.cn"   # 官网地址，不带结尾斜杠
api-key: ""                          # 需要 plugin:yudream-wallet:manage 权限
assets: []                           # 本服启用的**初始**列表；留空 = 官网所有 enabled 的货币
                                    # 管理员在游戏内切换后写 plugins/Wallet/assets.yml，后者优先
assets-cache-seconds: 60
balance-cache-seconds: 30
request-timeout-ms: 8000
match-mode: auto                     # auto / uuid / name（玩家 → 站内用户的映射方式）
history-page-size: 45
admin-page-size: 45
timezone: "Asia/Shanghai"            # 流水时间显示时区
```

> **API Key 权限**：余额查询、流水、加/减货币都在钱包的管理端接口下，需要
> `plugin:yudream-wallet:manage`（只读货币种类需要 `view`）。

> **返回主菜单**：界面第 6 行第 5 格的屏障会重新打开主菜单。优先用 MainMenu `Beta_1_2_0` 起的
> `MainMenuApi.open(player)`；如果服务端还是旧版 MainMenu，会自动退回执行 `/cd`。

## 管理员指令

```text
/walletin <玩家名> <币种编码> <数量>
/walletout <玩家名> <币种编码> <数量>
```

- 只对**在线玩家**生效；玩家名不区分大小写。
- 币种必须存在于 Wallet 官网且 `enabled=true`；数量必须大于 0，并按该资产的 `scale` 校验小数位。
- 权限：`wallet.adjust`（默认 OP）；`wallet.admin` 只控制菜单里的管理界面。
- 操作直接调用 Wallet 的 `credit` / `debit` 写回官网，官网流水即结果；
  `businessNo` 为 `wallet-admin-in-<uuid>` / `wallet-admin-out-<uuid>`，官网来源显示为 `ADMIN`，
  备注包含执行命令的管理员名称。
- 成功时回复本次变动和官网返回的余额；失败时回复 Wallet/官网返回的错误信息。

## 给后续插件用（依赖 + API）

```groovy
// build.gradle
compileOnly project(':Wallet')
```

```yaml
# paper-plugin.yml：保证 Wallet 先加载（不需要菜单功能时可按软依赖处理）
dependencies:
  server:
    Wallet:
      load: BEFORE
      required: true
```

```java
// 查余额
WalletApi.wallet()
        .balance(player.getUniqueId(), player.getName(), "coin")
        .thenAccept(balance -> player.sendMessage(Component.text("余额：" + balance.balance())));

// 加/减货币：直接写官网，官网流水就是上报结果；businessNo 是幂等键（重试传同一个值不会重复记账）
WalletApi.wallet().credit(player.getUniqueId(), player.getName(), "coin",
        new BigDecimal("100"), "order-20261020-001", "商店购买")
        .thenAccept(tx -> player.sendMessage(Component.text("入账：" + tx.amount())));
```

- `assets()` / `balance()` / `credit()` / `debit()` / `transactions()` 全部返回 `CompletableFuture`，不要在服务端主线程阻塞等待。
- **所有货币操作都必须走这里的 `credit` / `debit`**：它们直接调用官网接口，本地没有绕过官网的写路径。
- `cachedAssets()` / `cachedBalance()` 只读缓存、不发请求，适合渲染路径。

## 面包屑

- 流水查询需要站内 `userId`：先用 `game/balance` 让官网按 UUID/名字映射出 `userId`，再查 `/admin/transactions`。
- 官网响应默认带一层 `{code,message,data}` 信封、长整型/BigDecimal 可能序列化成字符串 —— HTTP 客户端两种都兼容。
- 玩家未绑定官网账号时接口会报「玩家未绑定网站用户」，菜单显示「未绑定网站账号」而不是 0。
