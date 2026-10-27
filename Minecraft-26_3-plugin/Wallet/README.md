# Wallet

`Beta_0_6_1`，Folia 26.3 / Java 25。本地持久多币种钱包，向 Vault 提供一个可配置的主币种；保留 WalletApi、原钱包界面和旧 YuDream 官网后端。

## 配置与升级

旧版官网配置没有 `backend` 时，新版本默认选择 `local`。**继续使用官网余额时，显式添加 `backend: website`**，保留原 `base-url`、`api-key` 和 `assets.yml`，无需添加本地 `currencies` / `vault` 配置。官网后端不注册 Vault 经济服务。

切换本地钱包时按下方示例定义币种，并按「旧 YuDream 官网一次性迁入」处理旧余额。`assets.yml` 的 `enabled` 列表只控制本服币种开关，不定义本地币种；原来使用 `gold` 等 code 的币种应保留原 code 和精度，不能直接改名为默认 `coin`。

`Beta_0_6_1` 修复旧配置缺少 `currencies` 时受 Bukkit 默认节影响、加载出空币种列表并报「币种不存在或未启用：coin」的问题。显式定义的自定义币种、空币种节和禁用状态保持原样，不会自动改币种或迁入余额。

停服并备份原 Wallet 配置和数据；替换旧 JAR，同一插件只保留一个版本。在 `plugins/Wallet/config.yml` 合并以下内容，保留原 `base-url` / `api-key`：

```yaml
backend: local
currencies:
  coin:
    name: 金币
    symbol: 金币
    scale: 2
    enabled: true
    transfer-enabled: true
  gem:
    name: 宝石
    symbol: 宝石
    scale: 0
    enabled: true
    transfer-enabled: true
vault:
  enabled: true
  currency: coin
test-mode:
  enabled: false
```

- `currencies` 决定本地币种；code 不区分大小写，长度 1～32，仅字母、数字、下划线、横线。小数位 `scale` 为 0～6。新账户余额为 0。
- **迁入前将旧官网币种 code、scale 原样对应配置**；`coin`、`gem` 是示例，不会自动换算或重命名旧币种。
- 标准 Vault Economy 一次提供一个主币种，`vault.currency` 从已启用币种中选择。其它币种使用 WalletApi 和钱包界面；世界参数共用全局 UUID 账户。
- 安装适配 Folia 的 Vault，并重启；日志应出现 `Wallet 已向 Vault 注册主币种`。已有其它 Vault 经济提供者时拒绝启用 Wallet，避免同时使用两套余额。
- `assets: []` 初始启用所有后端币种；游戏内管理员启停写入 `plugins/Wallet/assets.yml` 并优先生效，停用 Vault 主币种会暂停 Vault 资金操作。
- `backend: website` 保留旧官网读写，不注册本地 Vault 服务。`test-mode.enabled: true` 仍使用隔离测试账本，不注册生产 Vault 服务。
- 修改币种选择、精度和后端后需重启。已有余额的 code / scale 应保持稳定，切换 Vault 主币种前先核对 Town 等消费插件的待处理交易。
- Town 使用 Vault 时，将 `plugins/Town/config.yml` 的 `economy.provider` 设为 `vault` 并重启；旧 Wallet 来源待处理交易沿用 Town 原有来源隔离，不自动重放到新账本。

## 旧 YuDream 官网一次性迁入

权限 `wallet.migrate`，默认 OP，控制台可执行：

```text
/wallet migration-status
/wallet migrate-site
/wallet migration-status
```

1. 保留原官网 `base-url`、`api-key`（需要 `plugin:yudream-wallet:manage`）；配置本地全部待迁入币种及相同 `scale`，关闭测试模式并重启。
2. 迁入期间暂停旧官网钱包的消费/入账及其它仍写官网的游戏服，保持读取期间余额稳定。当前旧接口没有全站同一时点快照。
3. 执行 `migrate-site`：读取本服已知玩家（Bukkit 离线玩家，加本服在线时长台账中的 UUID），查询每个启用配置币种的官网余额。
4. 全部校验通过后，**追加**旧余额到各 UUID 本地账户，并在同一账本提交中保存完成标记。成功后重启、换配置、重复命令均拒绝再次迁入。

迁入边界：

- 仅覆盖本服已知 UUID；没有在本服留下记录的网站用户不在范围内。明确未绑定网站的玩家会跳过并报告。
- 官网只读取余额，不扣除旧款，不持续同步；旧历史流水留在官网，本地记录 `IMPORT` 流水。
- 网络失败、缺失/负数余额、精度不一致、重复网站身份映射到不同 UUID、返回 UUID 不一致等都会整批中止；校验失败没有部分入账，也不消耗成功迁入机会。
- 迁入期间本地资金写入暂停；正常完成或读取失败后恢复。落盘结果不确定时停止资金操作，检查磁盘并重启恢复后再查看 `migration-status`。
- 完成标记与余额都在 `plugins/Wallet/wallet-ledger.wal`，不得单独删除/回滚账本来重新导入；删除该文件也会删除本地余额与流水。停服备份整个 Wallet 数据目录。

## 日常使用

```text
/wallet balance
/walletin <在线玩家名> <币种编码> <数量>
/walletout <在线玩家名> <币种编码> <数量>
```

`wallet.adjust` 默认 OP；币种必须启用，金额为正数且符合精度。Vault 余额采用 double 接口，账本内部用 BigDecimal，并限制余额精度为 15 位，超限拒绝记账。
Vault 玩家对象接口支持离线 UUID；旧字符串名称接口仅接受服务端缓存中的玩家名，不阻塞查询 Mojang 或猜测 UUID；不提供 Vault 银行账户。

主页“我的资产”显示 Vault 主币种余额；“钱包与流水”进入原钱包功能。普通玩家左键查看个人流水；管理员左键查看玩家货币、右键管理币种启停。玩家货币列表沿用本服在线时长台账。

## 给后续插件用

依赖 `compileOnly project(':Wallet')`，在 `paper-plugin.yml` 声明 Wallet `load: BEFORE`。通过 `WalletApi.wallet()` 获取当前后端：

```java
WalletApi.wallet().credit(player.getUniqueId(), player.getName(), "coin",
        new BigDecimal("100.00"), "shop-order-20261026-001", "订单入账");
```

`assets` / `balance` / `credit` / `debit` / `transactions` 返回 CompletableFuture，不在区域线程阻塞等待。重试同一业务必须使用相同 `businessNo`；玩家、币种、方向或金额不一致时拒绝。`account:`、`site-import:` 为内部保留前缀。
本地模式 Vault 和 WalletApi 共用余额及流水；仅在落盘强制提交后返回成功。`cachedAssets` / `cachedBalance` 直接读内存，不发网络请求。

账本为带长度和 CRC 的追加日志，单批上限 64 MiB；启动时仅裁去未写完整的末尾记录，完整记录校验失败则停止加载，不重置余额。流水完整保留，文件会随交易增长；不建议在线编辑或截断。

构建：`./gradlew :Wallet:build`。一次性迁入和 Folia/Vault 联调范围见 [验证记录](./VALIDATION.md)。
