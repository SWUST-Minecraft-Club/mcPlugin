# Town 验证记录

## Beta_0_8_0 玩家指南验证

日期：2026-10-26。Folia 26.3 build 5 / Java 25 / HandbookCore Beta_0_1_1 / ArcMenu 3.2.0 / CraftEngine 26.9.2。
MinecraftMcp 控制独立 Minecraft 26.3 客户端 TownReader，1280×800、GUI 缩放 2；客户端安装 MainMenuRepair Beta_1_2_0，使用普通玩家权限阅读。

### 自动化

- `:Town:build --offline` 成功，81 项 JUnit 测试，0 失败、0 错误、0 跳过。新增 3 项使用真实 HandbookCore 目录与分页器验证：所有内部链接可达、常用关键词可检索、权限过滤、配置替换保留稳定 ID、全文不丢失及每张纸的行数和栏宽。
- 贡献 1 个分类、14 篇文章；内容包括成立、成员、连通领土、测绘、地块、租赁、镇库、科技政策、现货、收购、收件箱、迁移和常见问题。按钮不直接确认收费、施工或审批。
- 文字版 `./PLAYER_GUIDE.md` 从同一 `TownGuide` 定义按默认配置导出，未另写一套玩法规则。

### 实机

| 场景 | 结果与证据 |
| --- | --- |
| 无手册核心 | 保留 ArcMenu、CE、MainMenu、Wallet 等原有依赖，移除 HandbookCore 后 Town 正常启用；`/town guide` 返回手册暂不可用与 `/town help` 提示，没有链接外部 API 的类加载异常 |
| 注册与入口 | 加入 HandbookCore 后启动注册成功；普通玩家 `/town guide` 打开[入门双页书](./screenshots/town-handbook-start.png)，CE 书页通过当前 GitLab 分发加载 |
| 分类与分页 | [小镇生活目录](./screenshots/town-handbook-category.png) 显示 14 篇、共 3 页；实际点击到第 3 页，显示建筑迁移和常见问题；文章箭头可切换正文 |
| 内部链接 | 实际点击入门的“成立自己的小镇”，进入成立文章；继续翻页可阅读政厅说明 |
| 搜索 | `/handbook search 搬家` 返回[建筑迁移及相关问题](./screenshots/town-handbook-search.png) |
| 玩家指令按钮 | 空背包打开测绘文章，实际点击“领取测绘工具”，手册合上、背包出现 1 个带小镇名称的测绘木棍，并收到选点说明 |
| 配置刷新 | 独立测试服将人数设为 4、成立费 2345.67、赠地 13、扩张费 78.9、斜角连接启用；`/town reload` 后文章显示[新的成立人数和费用](./screenshots/town-handbook-rules.png)。赠地与连通文案由自动化覆盖；随后恢复测试配置 |
| 核心重载 | `/handbook reload` 后 Town 贡献保留，分类仍有 14 篇；不向核心 `content/` 写入玩法副本 |

本轮验证指南贡献、阅读、导航、普通玩家按钮和配置刷新；未重跑全部经营与搬迁业务。Town 单独运行、同时缺少 ArcMenu 的启动场景存在既有 `ArcMenuTextInputListener` 类加载问题，本轮未修改该部分，因此“无手册核心”验证不代表所有软依赖都缺失时仍能启动。测试服仍有既有 Wallet 菜单构造及 CE 缓存提示。

```text
Town-mc26_3-plugin-Beta_0_8_0.jar  e1f8016fdf0887303cd574718e314c3e09f6839a95f4dc5895b180ce01d687f1
```

## Beta_0_7_1 图标、余额与风格验证

日期：2026-10-26。Town `Beta_0_7_1` / Folia 26.3 build 5 / Java 25 / ArcMenu 3.2.0 / CraftEngine 26.9.2 / 用户提供的 Folia 版 Vault。MinecraftMcp 控制 Minecraft 26.3 客户端 PackTester，1280×720；复用前轮隔离测试服，实际加载 jar 与本版留档逐字节一致。

### 自动化与布局

- `:Town:build --offline` 成功，78 项 JUnit 测试，0 失败、0 错误、0 跳过。新增 5 项检查覆盖完整精度、零余额、极大/极小金额、长币名和样式清理。
- 保持 ppb=42.7、800×450 画布与 70% 居中比例。校准矩形宽 /1.01、高 /0.915、底边固定，frame 两轴 /1.01；保留七层透视与独立地图图片锚点。
- [像素审计](./style-layout-audit.json) 检查首页、镇库和成员详情共 14 处卡片边界，最大误差 0.75 画布单位；地图真实图片范围 `[214,184,579,549)`，最大误差小于 0.465 画布单位。
- 图标按既有库存模型记录分类：箱子和头颅 GUI + Y=180° + 正 Z=0.1；普通图标 FIXED、三轴等比。实拍检查铃铛、书、门、箱子及成员头颅，184 宽的成员操作卡上下排布。

### 实机结果

| 场景 | 结果与证据 |
| --- | --- |
| 正常余额 | [主页](./screenshots/town-style-home.png) 显示 `8,984.75`，币种单独一行；与 Vault 持久账本一致 |
| 大额与长币名 | [大额余额](./screenshots/town-style-large-balance.png) 显示 `≈123.45万亿`，长币名省略；[悬停](./screenshots/town-style-balance-tooltip.png) 保留完整金额、币名与来源 |
| 零余额 | [零余额](./screenshots/town-style-zero-balance.png) 显示 `0`，不误判为查询中或服务不可用 |
| 查询失败 | 提供者故意抛错，[余额显示暂不可用](./screenshots/town-style-balance-failure.png)，5 秒后允许重试；未将失败值显示为负余额 |
| 服务断开与恢复 | 注销 Economy 后[等待连接](./screenshots/town-style-balance-unavailable.png)，菜单继续显示；重新注册后自动恢复，切回正常模式再次显示 `8,984.75` |
| 镇库 | [镇库页](./screenshots/town-style-civic.png) 显示 `15.25` 与金币，流水使用可读币名；首页入口与返回主页通过实际视角和右键命中验证 |
| 成员与地图 | [成员详情](./screenshots/town-style-member.png) 高卡文字位于图标下方；[地图](./screenshots/town-style-map.png) 保持图片、框线与网格对齐 |

大额和长币名截图保留本轮描边加固前的画面，最终版仅将相应卡片描边加固为 1 单位，金额规则及排版一致。全部场景由临时 TestPluginVault 构造，金额展示模式不修改持久账本；未据此扩大第三方经济提供者兼容性声明。修复服务未解析时立即完成的预热回调：只在解析成功后主动刷新，失败或未连接等待正常周期，避免同步重入。

### 交付审计

- 服务端包共 19 个文件，SHA-256 清单覆盖其余 18 个，全部一致。Town jar 与留档一致；Vault 与用户原件一致，其余依赖与上一版包逐字节一致。
- 默认仍为 wallet / TEST、`eula=false`、`online-mode=true`，Linux 启动脚本保留执行权限。无真实上传令牌、TestPlugin、客户端、世界和玩家数据。
- 本轮验证聚焦菜单与余额状态，既有资金恢复、经营和迁移流程见下方历史验证；未重新宣称全业务覆盖。测试服仍有既有 Wallet 菜单构造、CE 生命周期缓存及 Java 弃用警告。Windows 启动脚本本轮未在 Windows 实机执行。

```text
Town-mc26_3-plugin-Beta_0_7_1.jar  804d2535ddcb7eb69dcd3622510b19d7740bf0035a11a45f071a1177791c1f7c
Town-Server-mc26_3-Beta_0_7_1-GitLab.zip  5b7eee7507791c9d7abc779e125f88d500a8f7685359131ee31941b989c613c4
```

## Beta_0_7_0 Vault 经济验证

日期：2026-10-26。Folia 26.3 build 5 / Java 25 / 用户提供的 Vault `1.7.3-b0-folia` / ArcMenu 3.2.0 / CraftEngine 26.9.2。MinecraftMcp 控制 Minecraft 26.3 客户端 PackTester，1280×720。

### 自动化与联调方式

- `:Town:build` 成功，73 项 JUnit 测试，0 失败、0 错误、0 跳过。新增 12 项检查覆盖调用回执、未知结果、人工核对、重启、退款、来源绑定、精度与损坏台账保护。
- 新服务端包干净解压，测试时仅调整 EULA、离线验证、端口、测试世界与 Town 的 `economy.provider: vault`，复用依赖缓存。真实 Vault 正常启用，Town 使用它实际类加载器里的标准 Economy API。
- 临时 `TestPluginVault` 通过 Bukkit ServicesManager 注册可控经济提供者 `FixtureCoin`，初始 10000 金币、精度 2，资金和调用次数落盘。转移时断言在线账户属于当前玩家区域、离线账户处于 Folia 全局线程；夹具只存在于被忽略的联调目录，不随包发布。

### 实机结果

| 流程 | 结果与证据 |
| --- | --- |
| 未注册服务 | Vault 已启用、Wallet 测试模式同时存在；Town 显示等待 Economy，收费成立被拒绝，没有改用 Wallet |
| 晚注册 | `/testvault register` 后无需重载 Town，`/town admin economy` 显示 `Vault / FixtureCoin` 与 `vault:TestPluginVault:FixtureCoin` |
| 成立与镇库 | 成立扣 1000，余额 9000；贡献 12.50、提取 2.25 后玩家 8989.75、镇库 10.25，三个资金调用均在玩家区域执行 |
| 离线入账 | 固定离线 UUID 入账 7.25，夹具确认 Folia 全局线程并记录 `OFFLINE_CREDIT_OK`；玩家账户不受影响 |
| 余额不足 | 贡献 999999 被明确拒绝，业务为 DECLINED，资金调用计数和镇库均不变 |
| 已扣款但回复丢失 | 贡献 5 时提供者保存扣款后抛异常；玩家变为 8984.75，外部累计调用 5 次，Town 保留 REQUESTED / PENDING，镇库仍为 10.25 |
| 未知结果跨重启 | 正常停服并重启，晚注册提供者后继续多轮恢复；外部调用仍为 5、余额仍为 8984.75，待核对记录保留，没有再次扣费 |
| 人工核对 | 对交易 `1bbb96b7-70d9-42ee-b326-b6997e4af333` 的 debit 标记 success，Town 补记贡献并完成业务，镇库变为 15.25；外部调用仍为 5，玩家余额不变 |
| 退款 | 夹具提交 3.25 的收费业务并令本地应用拒绝，真实 Vault 扣款、退款各一次，结果 REFUNDED；外部累计调用 7，最终玩家余额回到 8984.75 |
| 菜单余额 | 首页实际显示 `8984.75 金币`，与提供者持久账本一致，见 [Vault 余额截图](./screenshots/town-vault-balance.png) |
| GitLab 分发 | 新包继续自动合包上传；本轮最终资源 ZIP 为 859609 字节、SHA-1 `6fbe867d2fa6e4dd43142d5082b25f98b8159474`，匿名 GET 200，下载内容与 CE 最终 ZIP、缓存一致 |

Vault 标准 API 不提供业务号幂等保证，未知结果需要管理员核对；这是本版明确的恢复边界。实机使用的是可控提供者，没有据此声称任意第三方经济插件都支持 Folia 或已完成兼容验证。现货、租赁与建筑迁移的完整流程见下方历史 Wallet 联调，本轮实机覆盖新的 Vault 调用链及失败恢复。

### 交付审计

- GitLab 服务端包共 19 个文件，SHA-256 清单覆盖其余 18 个；Town jar 与留档、Vault jar 与用户原件分别逐字节一致。
- 默认 `eula=false`、`online-mode=true`、Town 来源 wallet / TEST；已包含 Vault，切换为 vault 并重启后还需经济提供者注册服务。
- 无真实上传令牌、TestPlugin、客户端、世界或玩家数据；Linux 启动脚本保留执行权限。Windows 启动逻辑未在 Windows 实机执行。
- 使用包含 Vault 的包再次打包逐字节一致；`--economy vault` 能生成 Vault 配置并继承同一 Vault jar。
- 启动仍有既有 Wallet 菜单构造警告及 CE 生命周期缓存降级提示；本轮未修改这些依赖。预期的未知结果恢复警告在人工核对后结束。

```text
Town-mc26_3-plugin-Beta_0_7_0.jar  a7bbe81a887cb8268839480067de7f1cb7c066d6227eb4c30315a483e7f48f4e
Town-Server-mc26_3-Beta_0_7_0-GitLab.zip  a7b2a3e2de1c6ea5e0cb8e863eae76a1286be3724f1e31b8a7d02f1fbd2c0001
Vault-1.7.3-folia.jar  140b9e75cd5371280f4bbf1cc68dc733ce7b8ffe3ea273f01c4da831022d4dd8
```

## Beta_0_6_1 GitLab 分发验证

日期：2026-10-26。使用 CE 26.9.2 原生 GitLab host，分发项目为 `yudream/mc-texture`（项目 ID `51`，公开项目）。本轮调整部署配置与打包脚本，Town jar 与已留档的 `Beta_0_6_1` 字节一致；此前的 61 项测试结果见下方资源修复记录。

### 上传、下载与客户端

- 干净最小服启动后自动完成 ArcMenu 资源生成、CE 合包及 GitLab 上传。CE 缓存保存实际附件 URL 和 SHA-1；无需将每个资源 ZIP 提交到 Git 仓库。
- MinecraftMcp 控制的 Minecraft 26.3 客户端 PackTester 从 GitLab 下载中心地图包，客户端记录为 `862629` 字节、SHA-1 `687036d527b959f0e868cfb14dae1b285453c72c`。
- 执行 `/town claimmap move 3 0` 后自动合包、上传并发送新地形。最新包为 `866166` 字节、SHA-1 `a7f1d2d40589fc92300ca125427943cd7bfbc865`；匿名 HTTP GET 返回 `200`，下载内容、CE 最终 ZIP、CE 缓存与客户端实际下载记录的 SHA-1 全部一致。
- 截图人工复核：中心与移动后地形均正常显示，地图位于面板边框内，无缺字白框。截图：[中心地图](./screenshots/town-gitlab-map.png) · [移动后的地图](./screenshots/town-gitlab-panned.png)。
- 分发仓库的 `README.md` 与 `craftengine-host.yml` 可匿名读取，配置片段未含真实凭据。CE 26.9.2 的实际配置结构为 `resource-pack.packs.default`，以本版 jar 配置与实测为准；官方网页仍展示旧版结构。

### 交付审计

- GitLab 服务端包共 18 个文件，包内 SHA-256 清单覆盖其余 17 个文件，全部校验通过；Linux 启动脚本保留可执行权限，默认 `eula=false`、正版验证开启。
- 全包检查未包含本机真实上传令牌；随包仅提供令牌示例。Linux 启动脚本已验证缺少凭据时拒绝启动、正确读取 CRLF 单行令牌文件并传入 Java 环境，且不输出令牌内容。
- 以 GitLab 包再次打包，输出逐字节一致；`--host self` 能恢复 CE 内置托管并移除 GitLab 启动凭据要求。
- Windows 启动脚本已检查配置与注入逻辑，未在 Windows 实机执行。本轮游戏测试使用独立离线测试服；交付包仍启用正版验证。日志仍有既有 Wallet 菜单构造警告。

```text
Town-Server-mc26_3-Beta_0_6_1-GitLab.zip  575e7bb273e62b15dce14868a6ea97c7c47cf17bdce5638f31142a697bd7d43e
```

## Beta_0_6_1 资源修复验证

日期：2026-10-26。Folia 26.3 build 5 / Java 25 / ArcMenu 3.2.0 / CraftEngine 26.9.2。使用同仓库 `./fabric-26_3-mod/MinecraftMcp/` 控制 Minecraft 26.3 客户端，玩家 PackTester，1280×720；资源测试使用普通客户端操作，无需客户端安装 ArcMenu Editor。

### 自动化与交付包

- `:Town:build` 成功；61 项 JUnit 测试，0 失败、0 错误。新增回归检查覆盖最终包缺字体、旧图片 CRC、正确合包，以及等待页无图片字形但保留 49 格和全部圈地操作。
- 新服务端 ZIP 直接干净解压后启动，未预放世界、地图缓存或 CE 最终包。Town 自动等待 CE 加载，再构建 ArcMenu 索引和 CE 最终包，日志确认生成、校验、压缩、上传完成。
- 最终包共 16 个文件，各文件 SHA-256 验证通过，内含 Town jar 与留档字节相同；无客户端、Mod、TestPlugin、世界和玩家资料。
- 本地测试仅调整 EULA、离线验证、端口及 CE 离线 UUID 校验，复用依赖缓存；CE 托管 IP 仍为交付默认的 `auto`。交付包保持正版验证。

### 实机结果

| 流程 | 结果与证据 |
| --- | --- |
| 原包复现 | Beta_0_6_0 干净首次启动只有 ArcMenu 本地 ZIP，没有 CE 最终 ZIP 和托管缓存；客户端地图显示与反馈一致的大白框 |
| 新包自动加载 | 客户端启用服务器资源包，首次连接自动收到资源；进入游戏后补发缓存包取得可核验回执，地图自动显示，无需手动发送资源包 |
| 拒绝资源包 | 隔离测试服拒绝资源包后保留可操作区块网格，不创建缺失的图片字形；无大白框 |
| 新位置与回切 | 最终新包执行 `/town claimmap move 3 0`，先显示网格，新增 PNG 经自动合包投递后显示对应地形；30 秒尾随重建也已在日志确认 |
| 重连 | 从多人游戏界面断开并重连，打开已缓存中心地图，无需手工执行 CE 指令，自动显示正确地形 |
| CE 物品 | 最终新包获取 `town:town_hall` 与 `/town select`，背包与手持显示政厅立方体、透明测绘工具的正确材质 |
| 图片锚点 | 真实图片像素范围 `[214,184,579,549)`，相对设计边界最大误差小于 0.465 画布单位；保持 ppb=42.7，见 [测量记录](./resource-layout-audit.json) |

截图：[正常地图](./screenshots/town-map-resources.png) · [新位置加载网格](./screenshots/town-map-waiting.png) · [CE 物品材质](./screenshots/town-ce-resources.png)。根因和资源生命周期见 [资源投递经验](../../../docs/exp/town-resource-packs.md)。

本轮聚焦资源投递、地图切页与对齐；经营和迁移结果保留于下方历史记录。测试日志仍有既有 Wallet 菜单构造警告，本次没有修改 Wallet；不能把整服日志描述为完全无警告。公网、局域网或反向代理下载地址需按实际部署配置，本轮验证的是本机连接。

交付 SHA-256：

```text
Town-mc26_3-plugin-Beta_0_6_1.jar  6b05f3e5443766d0ab77e8cbe7c42e2f86124ec223f9acb7725b24d197773cb1
Town-Server-mc26_3-Beta_0_6_1.zip  b912c44e9c750d9e835225058ffd20202f9568a9335e0f241d27eede0d82a2a5
```

## Beta_0_6_0 历史验证

日期：2026-10-26。Folia 26.3 build 5 / Java 25 / ArcMenu 3.2.0 / CraftEngine 26.9.2 / Wallet 本地测试模式。两名真实客户端玩家为 TownTester 与 MarketBuyer，通过 MinecraftMcp 发送普通客户端操作，临时 TestPluginTown 只提供场景、服务状态读取和视角定位；夹具不随产物发布。

### 自动化

59 项 JUnit 测试通过：注册表和区块拓扑、赠地数据、地块权限/修改审批/出租状态、镇库与政策、持久支付重试和退款、托管交易和背包意图、三维变换、重叠快照与工程恢复、25 页菜单结构、地图资源生命周期等。运行命令见模块 README。

贴图机械转换的 `--check` 对 4 张成品逐像素验证通过。Gradle 编译仍有既有地图生物群系名称与药水 API 的弃用警告。

### 实机结果

| 流程 | 结果与证据 |
| --- | --- |
| 单人成立、CE 核心 | MarketBuyer 单人成立中文镇“商旅小筑”，扣 1000 TEST；实际右键放置 CE 核心，仅领取中心块，剩余 8 额度 |
| 自主领土与拓扑 | MapleTown 自选两块，余 6 额度；放弃连接中间块被拒绝，提示会使领土与政厅断开 |
| 外镇租赁 | MapleTown 住宅租给属于商旅小筑的玩家，100 TEST / 7 天，业主收到租金 |
| 边界审批 | 租期内缩小地块：未同意不能提交，租户同意后成功；稳定 ID、租约保留 |
| 私产权限 | 两位玩家去掉 op 后，原业主/镇长破坏出租地块被拒绝；租户破坏相同范围内方块成功 |
| 镇库与发展 | 贡献 2000 + 500 TEST；采购科技、政策；槽位满时拒绝新政策，升级档案后可购买 |
| 药水与丰收 | 玩家在镇域实际持有 HASTE I；100 次成熟小麦真实破坏事件产出 119 小麦（概率加成的一次样本） |
| 折扣实际应用 | 迁移基础 20.25，物流 5% + 工匠 10% 后报价 17.21；商业政策使 2.50 成交净收由 2.38 变为 2.40 |
| 现货与完整商品数据 | 托管 10 个“枫叶认证钻石”，买家购买并领取，名称、数量保留 |
| 预付订单与离线结算 | 发布 5×3.00 预付订单扣 15；卖家离线时成交现货、买家供应 2 个得 5.70；取消剩余 3 个返 9.00 |
| 重启收件箱 | 重启后原有 2、1 个邮件及业主订单收货可领取；真实菜单领取后买家累计 5 个，托管库存按成交数减少 |
| 旋转建筑迁移 | 箱内 3 钻石、楼梯、告示牌、CE 核心测试方块及木椅从源区搬至目标；箱子与 CE facing 旋转正确，家具目标位置正确 |
| 迁移重启与撤回 | 待验收工程重启后仍为 REVIEW，内容存在；普通迁移和 CE 重叠移动撤回恢复原址、原方向及费用 |
| 原址重叠、贴地、冲突 | 1×2 源区旋转后与目标重叠，写入成功；贴地从 Y=70 调至 65；占用目标提示具体冲突坐标 |
| 政厅迁址 | 真实登记 CE 核心独立迁移并旋转，验收更新政厅和回镇坐标；未重复赠地 |
| 租赁建筑迁移 | 房东发起，租户同意；目标微调使同意失效，重新审批后施工、验收，租约和 ID 保留 |
| 商铺联动 | 施工时购买被拒绝；验收将 81 格商业地块和商品锚点旋转到新坐标、版本 +1，之后继续购买成功 |
| 部分家具 | 仅选择双座长凳的一格被拒绝，提示完整家具碰撞范围必须包含在源区 |
| 旧数据兼容 | 添加无新字段的旧 9 块镇，加载后 claims=9、free=0、granted=true；保存重启仍保持，无重复赠地 |
| ArcMenu | 启动加载 25 份 Town 菜单，无菜单校验错误；实际点击首页、列表、表单、规划、工程、确认、收件箱和审批等按钮 |
| 原生输入 | 真实点击贡献按钮、输入 12.50、点击铁砧结果、确认页提交，镇库增加 12.50；修复原生输入 maxLength >50 导致无法打开的问题 |
| 材质与布局 | CE 核心、透明测绘工具均实际显示；主页和经营页面截图复核，关键中心/行距像素测量见 layout-audit.json |

主要迁移工程：`c92cc8a0-7908-4967-9650-1d6315324aef`（旋转商铺，验收）、`e8a326f3-d428-47a9-bf9f-258ac1c27267`（CE 重叠旋转，撤回）、`82313b53-6aca-4abc-aa64-729e2282899e`（政厅迁址，验收）、`f3315035-1ed8-4a46-b52d-f9d38da25115`（租户审批迁移，验收）。

### 范围说明

- Wallet 正式网站接口未连接；幂等重试与支付恢复有单元测试，本次实际货币联调使用本地 TEST 账本。
- 已做多次完整重启及待验收恢复；未对每个写盘指令边界逐一执行进程强杀或硬盘故障注入。
- 客户端未装 ArcMenu Editor；本次点击使用服务器命中区、实际视角与右键，文本输入为原生铁砧路径。Mouse/Touch/Vision 的客户端 Mod 专用交互未全覆盖。
- CraftEngine 验证范围为本版核心、原版容器/告示牌和默认木椅；部分长凳预检拒绝。未声称支持任意第三方自定义状态或插件数据库。
- 联调早期 ArcMenu 同步重建资源包曾触发一次启动 watchdog 警告，之后启动正常；刻意拒绝无审批施工时的异常出自临时夹具。不能把全程日志描述为完全无警告。
