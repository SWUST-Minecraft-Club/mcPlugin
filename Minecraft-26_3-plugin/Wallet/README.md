# Wallet

`Beta_0_7_0`，Folia 26.3 / Java 25。本地持久多币种与官网混合钱包，按币种配置来源；向 Vault 提供一个本地主币种，提供 WalletApi、背包界面与管理指令。

## 配置与升级

旧版官网配置没有 `backend` 时，新版本默认选择 `local`。**继续使用官网余额时，显式添加 `backend: website`**，保留原 `base-url`、`api-key` 和 `assets.yml`，无需添加本地 `currencies` / `vault` 配置。官网后端不注册 Vault 经济服务。

切换本地钱包时按下方示例定义币种，并按「旧 YuDream 官网一次性迁入」处理旧余额。`assets.yml` 的 `enabled` 列表只控制本服币种开关，不定义本地币种；原来使用 `gold` 等 code 的币种应保留原 code 和精度，不能直接改名为默认 `coin`。

`Beta_0_6_1` 修复旧配置缺少 `currencies` 时受 Bukkit 默认节影响、加载出空币种列表并报「币种不存在或未启用：coin」的问题。显式定义的自定义币种、空币种节和禁用状态保持原样，不会自动改币种或迁入余额。

`Beta_0_6_2` 修复官网返回 UUID 与本服账户不一致时阻断整批迁入的问题：该玩家全部待迁入币种跳过，服务端日志输出玩家名、本服 UUID、官网玩家名 / UUID / userId 和币种，其他通过校验的玩家继续迁入。完成回复分别统计未绑定和 UUID 不一致的人数；跳过账户需另行核对补款，成功后不会自动补迁。

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

## 同时使用本地和官网币种

使用 `backend: hybrid`，在 `currencies` 下逐个配置 `source`。完整无密钥模板见 [双来源配置](./deploy/Wallet-hybrid.yml)：

```yaml
backend: hybrid
currencies:
  gold:
    source: local
    name: 星空币
    symbol: 星空币
    scale: 0
    enabled: true
  emerald:
    source: website
    website-code: EMERALD
    name: 星空晶
    symbol: 星空晶
    scale: 0
    enabled: true
vault:
  enabled: true
  currency: gold
test-mode:
  enabled: false
```

- 本服 code、名称、符号和来源均可配置；`website-code` 可映射不同的官网 code，缺省与本服 code 相同。同一官网币种只能映射一次，官网必须启用且 `scale` 一致。
- hybrid 只显示 `currencies` 中声明的币种；官网币种实时操作官网，本地币种使用现有 `wallet-ledger.wal`，本地操作不查询官网余额。官网故障不影响本地余额或 Vault，也不会将官网资金写到本地。
- `vault.currency` 必须是启用的本地币种。已使用 `gold` 的服务器继续保留 `gold` 和原账本，Vault 提供者仍为 `Wallet/gold`。
- 保留官网地址并填入有效 `api-key`（`plugin:yudream-wallet:manage` 权限）。未填写时，本地仍可用，官网查询与操作会报错。
- 核对已有 `assets.yml` 的 `enabled` 列表：本例需要 `gold`、`emerald`，开关按本服 code 保存；已有列表优先于 `config.yml` 的 `assets`，单换 JAR 不会覆盖。
- 停服备份、合并配置后重启。修改 code 或来源不会自动重命名、迁移或同步余额；已有余额不要随意换 code、精度或来源。
- 已完成 GOLD 迁入的服务器直接启用 hybrid，保留原完成标记；`EMERALD` 仍在官网，不再执行迁入。

## 旧 YuDream 官网一次性迁入

权限 `wallet.migrate`，默认 OP，控制台可执行：

```text
/wallet migration-status
/wallet migrate-site
/wallet migration-status
```

1. 保留原官网 `base-url`、`api-key`（需要 `plugin:yudream-wallet:manage`）；配置本地全部待迁入币种及相同 `scale`，关闭测试模式并重启。
2. 迁入期间暂停旧官网钱包的消费/入账及其它仍写官网的游戏服，保持读取期间余额稳定。当前旧接口没有全站同一时点快照。
3. 执行 `migrate-site`：读取本服已知玩家（Bukkit 离线玩家，加本服在线时长台账中的 UUID），查询每个启用的本地币种的官网余额；hybrid 中 source: website 的币种不参与迁入。
4. 跳过未绑定或 UUID 不一致的玩家，其余账户全部校验通过后，**追加**旧余额到各 UUID 本地账户，并在同一账本提交中保存完成标记。成功后重启、换配置、重复命令均拒绝再次迁入，包括已跳过的账户。

迁入边界：

- 仅覆盖本服已知 UUID；没有在本服留下记录的网站用户不在范围内。明确未绑定网站或官网返回 UUID 不一致的玩家会整人跳过并即时输出警告；该玩家之前已读到的其他币种也不入账。详情保留在服务端日志，完成时回复成功、未绑定与 UUID 不一致的人数。
- 官网只读取余额，不扣除旧款，不持续同步；旧历史流水留在官网，本地记录 `IMPORT` 流水。
- 网络失败、缺失/负数余额、精度不一致、重复网站身份映射到不同 UUID 等仍会整批中止；这些错误没有部分入账，也不消耗成功迁入机会。若所有候选均被跳过，则不写完成标记，核对后可以重试。
- 有正常账户成功迁入时，一次性标记照常完成；跳过账户不入账，也不会自动补迁。保留迁入日志，确认真实归属后另行处理补款，不能删除账本或重跑整批迁入。
- 迁入期间本地资金写入暂停；正常完成或读取失败后恢复。落盘结果不确定时停止资金操作，检查磁盘并重启恢复后再查看 `migration-status`。
- 完成标记与余额都在 `plugins/Wallet/wallet-ledger.wal`，不得单独删除/回滚账本来重新导入；删除该文件也会删除本地余额与流水。停服备份整个 Wallet 数据目录。

## 日常使用

```text
/wallet help
/wallet balance [币种]
/wallet history [币种] [页码]
/wallet assets
/wallet players
/wallet set <玩家名或UUID> <本地币种> <目标余额>
/wallet add <玩家名或UUID> <币种> <金额>
/wallet take <玩家名或UUID> <币种> <金额>
```

| 操作 | 权限与范围 |
| --- | --- |
| `balance` / `history` | 玩家查看自己的余额与流水；可按本服币种 code 筛选 |
| `assets` / `players` | `wallet.admin`，默认 OP；打开币种开关或玩家余额界面 |
| `set` / `add` / `take` | `wallet.adjust`，默认 OP；控制台可执行，按币种来源路由 |
| `migrate-site` / `migration-status` | `wallet.migrate`，默认 OP；本地账本一次性迁入及状态 |

- Tab 按权限和输入前缀补全子命令、在线玩家名、币种与示例金额；`set` 只补全本地币种。
- `set` 仅支持本地，目标余额可为 0，原子提交并记录 `SET` 流水和原余额；官网支持 `add` / `take`。加减金额必须大于 0，全部金额必须符合币种精度，不接受科学计数法。
- 管理命令接受在线玩家名、服务端已缓存的离线玩家名或准确 UUID；不查询 Mojang、不猜离线 UUID。兼容 `/walletin <玩家名或UUID> <币种> <金额>` 与 `/walletout ...`。
- 流水页左下“币种”按钮循环筛选，翻页保留选择。合并多个币种时最多浏览前 10000 条；更早流水请指定单币种。官网不可用时可用 `/wallet history gold` 单独查看本地流水，合并页会显示加载错误。
- Wallet 的菜单点击和拖拽均拦截，Shift、数字键交换、丢弃与双击不能移动图标；普通左右键切换币种开关。安装 MainMenu 后返回按钮打开主菜单，未安装时关闭钱包。

Vault 余额采用 double 接口，账本内部用 BigDecimal，并限制余额精度为 15 位，超限拒绝记账。Vault 玩家对象接口支持离线 UUID；旧字符串名称接口仅接受服务端缓存中的玩家名；不提供银行账户。

主页“我的资产”显示 Vault 主币种余额；“钱包与流水”进入钱包功能。普通玩家左键查看个人流水；管理员左键查看玩家货币、右键管理币种启停。玩家货币列表沿用本服在线时长台账；无 MainMenu 时直接使用上述命令。

## 给后续插件用

依赖 `compileOnly project(':Wallet')`，在 `paper-plugin.yml` 声明 Wallet `load: BEFORE`。通过 `WalletApi.wallet()` 获取当前后端：

```java
WalletApi.wallet().credit(player.getUniqueId(), player.getName(), "coin",
        new BigDecimal("100.00"), "shop-order-20261026-001", "订单入账");
```

`assets` / `balance` / `credit` / `debit` / `setBalance` / `transactions` 返回 CompletableFuture，不在区域线程阻塞等待。重试同一业务必须使用相同 `businessNo`；玩家、币种、方向或金额不一致时拒绝。`account:`、`site-import:` 为内部保留前缀。
本地币种的 Vault 和 WalletApi 共用余额及流水；仅在落盘强制提交后返回成功。`cachedAssets` / `cachedBalance` 直接读内存，不发网络请求。

账本为带长度和 CRC 的追加日志，单批上限 64 MiB；启动时仅裁去未写完整的末尾记录，完整记录校验失败则停止加载，不重置余额。流水完整保留，文件会随交易增长；不建议在线编辑或截断。

构建：`./gradlew :Wallet:build`。一次性迁入和 Folia/Vault 联调范围见 [验证记录](./VALIDATION.md)。
