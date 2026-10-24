# Town（小镇）

Minecraft 26.3（Folia）小镇系统：筹备成立 → 邀请成员 → 扣费成立 → 放置镇政厅 → 免费圈地 / 点选扩张 → 领地保护 → 领地祝福。
界面使用 [ArcMenu](https://docs.myarc.icu) 原生 YAML，经济走本仓 Wallet，镇政厅道具支持 CraftEngine 自定义物品（缺失回落原版）。

- 版本：`Beta_0_3_0`（见 `./Town-mc26_3-plugin-ChangeLog-Beta.md`）
- 主类：`com.example.town.TownPlugin`（包名 `com.example.town`）

## 依赖与降级矩阵

全部软依赖，缺谁都能启动，只降级对应能力：

| 依赖 | 有 | 无 |
| --- | --- | --- |
| [ArcMenu](https://docs.myarc.icu)（需 ProtocolLib） | `/town` / 主菜单入口 / 右键镇政厅 → 11 页世界内菜单 | 全功能走 `/town` 指令、聊天成员列表和文本地图 |
| [Wallet](../Wallet/) | 成立 / 圈地 / 祝福扣费与余额展示 | 提示「钱包服务不可用」，费用类操作不可用，其余功能正常 |
| [CraftEngine](https://docs.myarc.icu) | 镇政厅优先使用 `townhall.craftengine-id` 指定的物品 | 回落 `townhall.fallback-material`（默认磁石） |
| [MainMenu](../MainMenu/) | 主菜单功能区出现「小镇」入口，按查看者显示信息 | 无主菜单入口，仍可用 `/town` |

## ArcMenu 界面

当前版本为 `Beta_0_3_0` 重设计的 11 页界面：

1. 无镇主页、收到邀请、创建小镇；
2. 筹备主页、已成立主页；
3. 成员管理（8 行分页）、成员详情、在线玩家选择器（8 行分页）；
4. 9×9 领地地图、领地祝福（6 槽分页）、通用确认页。

设计采用 800×450 的深墨绿黑 + 黄铜 + 绿宝石配色，使用 rectangle / frame / text 和原版 item 模型，不需要额外贴图素材。
菜单由插件在启动和重载时生成到 `plugins/ArcMenu/menus/town-*.yml`，动态内容使用每玩家 META，不依赖 PlaceholderAPI。

- **输入模式默认鼠标（客户端 Mod 本地渲染光标，最跟手）**：ArcMenu `config.yml` 建议 `mouse.policy: "player-choice"`、`mouse.default: "mouse"`；玩家可 `/arcmenu mode` 自选 touch / vision。Town 不再强制切换模式。
- 光标大小与跟手感由 ArcMenu 全局配置控制：`mouse.cursor.size`（建议 3.0）、`sensitivity-x/y`（建议 3.0）、`sample-rate`（建议 60）；与菜单缩放无关。
- 文本输入（小镇名称 / 邀请玩家名）点击输入框后由 `ArcMenuTextInput` API 唤起，回车即提交。
- 圈地地图左键购买、右键放弃，均先进入确认页；确认期间目标区块边界会在世界中用光点标出（购买绿色 / 放弃红色）。地图支持四向拖动（每次 3 块）与「回到我的位置」。
- 成立、圈地、放弃领地、祝福购买、踢人、转让、退出、解散都有明确确认页；无 ArcMenu 时改为 60 秒内重复同一指令确认，`/town confirm-cancel` 取消。
- 页面关闭后通过 ArcMenu 会话快照清理本地状态；同页翻页和重新居中不会被误认为关闭。
- Town 的过渡与标题微动画以 `town-` 前缀**增量合并**进 ArcMenu 全局 `animations.yml`（首次写入自动备份为 `animations.town-backup.yml`），不覆盖默认菜单的既有绑定。
- 菜单几何补偿由 `MenuBuilder` 统一处理，`pixels-per-block` 固定为 42.7；视觉与点击区域一起按视野的 70% 居中显示；调布局只改生成器，不手改运行目录里的产物。

## 玩法流程

1. `/town create <名称>`（或界面「创建小镇」）→ 小镇进入筹备中；
2. 输入玩家名或使用在线选择器邀请，对方在邀请页接受；
3. 成员数 ≥ `town.min-members`（默认 2）后确认成立费用（默认 1000）→ 获得镇政厅道具；
4. 放置镇政厅：成为小镇中心 + 设定传送点 + 以中心为半径 `founding.free-claim-radius`（默认 1 → 3×3）免费圈地；
5. 扩张：圈地地图点选（青色 = 可买，必须与已有领地相连），每块默认 50；右键本镇区块可放弃，镇政厅所在块除外；
6. 领地祝福：对身处本镇领地的成员周期施加药水效果；非永久时长可续购叠加，永久祝福不可重复购买；
7. 保护：默认拦截非成员的破坏 / 放置 / 容器（含矿车箱）/ 交互 / 桶 / 爆炸 / 活塞跨界 / 火焰蔓延 / 展示框与盔甲架操作 / 实体破坏，全部可在 `protection` 段开关；`town.bypass` 可无视。

`claim.max-chunks` 是小镇地块总数上限（含成立赠送），`0` 表示不限。

## 指令与权限

`/town` 打开界面（无 ArcMenu 时为帮助）。常用子命令：

- 创建与成立：`create <名>`、`found`、`accept`、`reject`；
- 成员：`invite <玩家>`、`members [页]`、`kick|transfer|promote|demote <玩家>`、`leave`、`disband`；
- 领地：`claim`、`unclaim`、`map`、`spawn`；
- 祝福与查询：`buffs [页]`、`buff buy <id>`、`info [名称]`、`list`；
- 确认：`confirm <action>`、`confirm-cancel`；
- 菜单内部路径：`member <玩家>`、`invitepicker [页]`、`claimmap center`、`claimmap|unclaimat <行> <列>`；
- 管理：`reload`（权限 `town.reload`）、`admin reload`、`admin givehall`（权限 `town.admin`）。

| 权限 | 默认 | 说明 |
| --- | --- | --- |
| `town.use` | true | 基础功能 |
| `town.admin` | op | 管理指令 |
| `town.bypass` | op | 无视领地保护 |
| `town.reload` | op | 重载配置 |

## 数据与集成

- 运行期数据：`plugins/Town/config.yml`（全键兜底）+ `plugins/Town/towns.yml`。
- 保存：registry 锁内生成独立快照，异步文件 IO 按递增序号串行写入；旧快照不能覆盖新快照，关服写最新数据。
- 经济：启动和重载预热 Wallet 资产，余额未命中时异步查询；扣款后重验原小镇、职位、人数、地块或祝福状态，失败退款并记录退款失败日志。
- ArcMenu API：编译期依赖 `./libs/arcmenu-api-3.2.0.jar`，运行期由服务器的 ArcMenu 提供实现；社区版/高级版运行时 jar 不入库。
- 公开 API：`com.example.town.api.TownApi` 提供只读查询；事件包括 Founded / Disbanded / Claim / Unclaim / BuffActivate。
- ArcMenu YAML 知识库：[`./docs/exp/arcmenu-yml知识库.md`](../../docs/exp/arcmenu-yml知识库.md)；官方 wiki：<https://docs.myarc.icu>。

## 验证与已知限制

```bash
./gradlew :Town:test :Town:build
```

测试覆盖区块相邻、注册表冲突、镇长唯一性、布局器边界、11 页菜单结构、Arcitx 引用、成员参数、81 格动作与祝福分页。真实 ArcMenu 校验和玩法联调仍需在 Folia 测试服执行。

- 扣款与退款使用同一次操作的关联业务号，但崩溃后的自动对账/恢复台账尚未实现；极端崩溃窗口仍可用 Wallet 流水人工核对，不能宣称跨插件事务完全原子化。
- 本版不引入 SQL、跨服同步、税收、战争或分块权限系统。
