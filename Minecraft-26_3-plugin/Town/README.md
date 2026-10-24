# Town（小镇）

Minecraft 26.3（Folia）小镇系统：筹备成立 → 邀请成员 → 扣费成立 → 放置镇政厅 → 免费圈地 / 点选扩张 → 领地保护 → 领地 buff。
界面用 [ArcMenu](https://docs.myarc.icu)（世界内 3D 贴图菜单，Application API），经济走本仓 Wallet，镇政厅道具支持 CraftEngine 自定义物品（缺失回落原版）。

- 版本：`Beta_0_1_0`（首个测试版，见 `./Town-mc26_3-plugin-ChangeLog-Beta.md`）
- 主类：`com.example.town.TownPlugin`（包名 `com.example.town`）

## 依赖与降级矩阵

全部软依赖，缺谁都能启动，只降级对应能力：

| 依赖 | 有 | 无 |
| --- | --- | --- |
| [ArcMenu](https://docs.myarc.icu)（需 ProtocolLib） | `/town` / 主菜单入口 / 右键镇政厅 → 3D 贴图界面（主页、创建、成员、邀请选择器、buff 商店、**圈地点选地图**） | 全功能走 `/town` 指令 + 聊天文本地图，入口提示安装 ArcMenu |
| [Wallet](../Wallet/) | 成立 / 圈地 / buff 扣费与余额展示 | 提示「钱包服务不可用」，费用类操作不可用（其余功能正常） |
| [CraftEngine](https://docs.myarc.icu) | 镇政厅道具优先用 `townhall.craftengine-id` 指定的自定义物品 | 回落 `townhall.fallback-material`（默认磁石） |
| [MainMenu](../MainMenu/) | 主菜单功能区出现「小镇」入口（按查看者渲染小镇信息） | 无入口，仅指令 |

## 玩法流程

1. `/town create <名称>`（或界面「创建小镇」）→ 小镇进入**筹备中**；
2. 邀请成员（界面在线玩家选择器 / `/town invite <玩家>`），对方接受；
3. 成员数 ≥ `town.min-members`（默认 2）后，镇长「成立」→ 扣 `founding.cost`（默认 1000）→ 获得**镇政厅**道具；
4. 放置镇政厅：成为小镇中心 + 设定传送点 + 以中心为半径 `founding.free-claim-radius`（默认 1 → 3×3）**免费圈地**；
5. 扩张：圈地地图点选（青色 = 可买，必须与已有领地相连），每块 `expansion.price-per-chunk`（默认 50）；右键本镇区块可放弃（镇政厅所在块除外）；
6. 「领地祝福」：对身处本镇领地的成员周期施加药水效果，配置见 `buffs.entries`（时长 `-1` = 永久）；
7. 保护：默认拦截非成员的破坏 / 放置 / 容器 / 交互 / 桶 / 爆炸 / 活塞跨界 / 火焰蔓延 / 展示框与盔甲架操作 / 实体破坏，全部可在 `protection` 段开关；`town.bypass` 权限可无视。

## 指令与权限

`/town` 打开界面（无 ArcMenu 时为帮助）。子命令：`create <名>`、`found`、`disband [confirm]`、`invite <玩家>`、`accept`、`reject`、`leave`、`kick|transfer|promote|demote <玩家>`、`info [名]`、`list`、`spawn`、`claim`、`unclaim`、`map`、`buffs`、`buff buy <id>`、`cancel`；管理：`/town admin reload | givehall`。

| 权限 | 默认 | 说明 |
| --- | --- | --- |
| `town.use` | true | 基础功能 |
| `town.admin` | op | 管理指令 |
| `town.bypass` | op | 无视领地保护 |

## 文件与集成

- 运行期数据：`plugins/Town/config.yml`（全键兜底）+ `plugins/Town/towns.yml`（原子写、异步落盘）。
- ArcMenu 集成：编译期依赖 `./libs/arcmenu-api-3.2.0.jar`（gitignore 白名单已放行入库；运行期由服务器的 ArcMenu 提供实现）。社区版/高级版运行时 jar **不入库**。
- ArcMenu YAML 菜单知识库（供后续写静态菜单用）：[`./docs/exp/arcmenu-yml知识库.md`](../../docs/exp/arcmenu-yml知识库.md)；官方 wiki：<https://docs.myarc.icu>。

## 已知限制（Beta_0_1_0）

- 贴图素材（HTML→PNG 自制 + CC0 收集）尚未接入：当前界面用混凝土压扁面板 + 文字实体渲染，功能完整、观感朴素；素材管线落地后在 `Beta_0_2_0` 增强。
- 文字尺寸 / 锚点常量（`arcui/UiMetrics`）是经验值，需进服按 `arcmenu-designing` 技能的校准流程微调。
- 扣费的幂等键含时间戳：极端情况下（扣费后、落账前服务器崩溃）可能多扣一次，可用 Wallet 流水（remark 含小镇/区块）人工对账。
