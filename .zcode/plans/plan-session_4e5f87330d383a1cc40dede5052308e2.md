# 小镇插件（Town）开发计划（v3：全操作 GUI 化 + 材质自制/自集）

## 0. 结论
新增插件 `./Minecraft-26_3-plugin/Town/`（版本 `Beta_0_1_0`），基于 ArcMenu（世界内 3D 贴图菜单 Application API）+ Wallet（经济）+ CraftEngine（镇政厅物品）+ MainMenu（主菜单入口）。**所有操作全部有 GUI 路径**：创建（名称输入）、邀请（在线玩家选择器点选）、接受/拒绝、成立扣费、圈地点选购买、buff 购买、踢人/转让/升降职、解散；指令作为等价通道与无 ArcMenu 时的兜底。**不改任何现有插件的代码**。

**材质与图片全部由我解决**：HTML→PNG 自制为主（写 HTML/CSS，无头浏览器截图导出），不足处自行收集 **CC0/可商用免费素材**补充；只用于本插件，来源与许可记录进项目 README，不用任何不明版权素材。

## 1. 功能范围
1. **成立流程（全程 GUI）**：无镇玩家主界面 → 「创建小镇」→ 聊天栏输入名称（GUI 引导+取消，聊天捕获+非法名校验）→ 「筹备中」界面 → 「邀请成员」打开**在线玩家选择器**（分页点选，标注对方已有小镇）→ 对方界面出现「待接受邀请」卡片（接受/拒绝）→ 人数 ≥ `founding.min-members`（可配置，默认 2）后「成立」按钮亮起 → 费用确认弹层 → 扣费成立、发镇政厅。
2. **镇政厅道具**：CE 自定义物品优先（config 配 id），CE 缺失回落可配置原版材质（默认磁石），PDC 标记。放置=小镇中心：设传送点 + `founding.free-claim-radius`（默认 1 → 3×3）免费自动圈地；市长可拆回道具（保留地块）；右键镇政厅方块打开小镇界面。
3. **圈地与扩张**：必须与已有地块相连；`expansion.price` / `expansion.max-chunks` 可配置；GUI 圈地地图点选购买。
4. **成员管理（GUI）**：成员列表分页，点成员弹操作（升/降职、转让[确认]、踢出[确认]）；退出小镇按钮。
5. **保护**：默认保护破坏/放置/容器/交互/桶/爆炸/活塞跨界/燃烧蔓延/展示框盔甲架等，开关可配置；成员放行，`town.bypass` 管理员放行。
6. **领地 buff**：config 列表（id、名称、价格+币种、时长秒 `-1`=永久、药水效果+等级），GUI 商店点选购买（显示价格/状态），对身处小镇领地内成员周期施加，到期自动失效。
7. **扩展预留**：`com.example.town.api` 公开 `TownApi`（静态门面，仿 WalletApi）+ 事件（Found/Disband/Claim/Unclaim/BuffActivate）。

## 2. 界面方案（全部 ArcMenu Application）
自建 **ArcApp UI 工具层**（`arcui/` 包）：基于 `ArcMenuApi.registerApplication` + `surface.toWorld` + 每玩家私有实体 + 指针激活事件；调度走 `ArcMenuApplicationContext.execute/runTimer`（ArcMenu 内置 Folia 分支）。组件集：
- **主页版式**（有镇：信息+导航；无镇：创建入口+待接受邀请卡片）
- **ListView / PlayerPicker**：分页列表（成员、buff 商店、在线玩家选择器——邀请/踢人/转让共用）
- **GridView**：圈地点选地图 9×9（中心/本镇/他人/可买/不相连/超限 着色），点格购买并整屏重渲染反馈
- **文本输入**：聊天捕获组件（小镇命名等，含取消按钮与等待状态）
- **确认弹层**：一切花钱/危险操作二次确认并标注费用
- 动态文字全部服务端渲染（不依赖 PAPI）；背景/按钮/状态块用贴图（见 §3），贴图经 ArcMenu images 资源包 glyph 显示，若 glyph 联调不过回退 rectangle/frame/item 实体拼版（功能不受影响，仅观感降级）
- 打开入口：MainMenu 功能 `town:main`（按查看者渲染）、`/town`、镇政厅方块右键
- **降级**：无 ArcMenu/ProtocolLib 时全功能走指令等价通道（站立 `/town claim`、`/town map` 文本地图等），入口提示安装 ArcMenu；MainMenu/Wallet/CE 软依赖全走隔离 Hook 类（防 `NoClassDefFoundError`，见 exp/paper-plugin.md）

## 3. 材质与图片（我负责产出）
- **管线**：HTML/CSS 设计 → 本机无头浏览器（ZCode browser-use）按像素尺寸截图 → PNG → 放入 `Town/src/main/resources/arcmenu/images/town/`；HTML 源码留 `./Minecraft-26_3-plugin/Town/texture-src/` 备改。
- **自制清单（首批）**：主面板背景（2048×1152，16:9 深色石质+金色点缀）、按钮（常态/悬停/禁用三态）、列表行条、确认弹层面板、输入提示面板、圈地格子六态（中心/本镇/他人/可买/不相连/超限）、导航图标底板。
- **收集补充**：缺失的小件（角标、边框花饰等）从 CC0 素材站（如 Kenney UI packs 等）取用并改造；每个用到的素材在 Town README 记录「来源 + 许可证」。
- **运行期安装**：按内容哈希释放/更新到 `plugins/ArcMenu/images/town/`，贴图变更时提示玩家重收资源包。

## 4. 数据与持久化
`plugins/Town/`：`config.yml`（全键兜底）+ `towns.yml`（小镇/成员/职位/邀请/地块/buff 到期；原子写临时文件+move，异步 IO，内存并发映射+不可变快照）。地块索引 `ConcurrentHashMap<ChunkKey, townId>`；`claim.worlds` 世界过滤；筹备超时自动解散（可配置）。

## 5. 指令与权限（等价通道）
`/town`（打开界面）、`create/found/disband/invite/accept/reject/kick/leave/transfer/promote/demote/info/list/spawn/claim/unclaim/map/buffs/buff buy/reload`、`/town admin`（givehall、claim/unclaim）。权限 `town.use`（默认 true）、`town.admin`（op）、`town.bypass`（op）。

## 6. 新增/改动文件清单
- 新增 `./Minecraft-26_3-plugin/Town/`（约 50 个源文件：主类、TownConfig、Messages、model/、store/、TownRegistry、service/、protection/、townhall/、buff/、command/、hook/（MainMenu/ArcMenu/CE/Wallet 四隔离钩子）、**arcui/**（工具层+组件+各界面）、api/ + 资源 config.yml、paper-plugin.yml（`folia-supported: true`，软依赖 MainMenu/ArcMenu/Wallet/CraftEngine）、arcmenu images 与 texture-src、README、ChangeLog-Beta）。
- 现有插件零改动；根 `./build.gradle` 不动（settings.gradle 自动发现）。
- 文档：AGENTS.md 现状快照追加 Town、根 README 版本表加行、视情况补 `docs/exp/`（ArcMenu 联调坑）。
- 产物：`./release/Town/Town-mc26_3-plugin-Beta_0_1_0.jar`（构建自动归档）。

## 7. 实施顺序
1. `./gradlew newPlugin -PpluginName=Town` 脚手架 → 数据模型/存储/指令等价通道（含 Wallet 扣费）。
2. **ArcMenu spike**（最大不确定点先排雷）：ArcMenu jar（D:\downloads）+ ProtocolLib 放 `./run/plugins/` 本地联调（不提交），验证 Application 打开/指针事件/私有实体/图片 glyph → 定稿 arcui 工具层。
3. arcui 组件（列表/选择器/网格/输入/确认）→ 各界面接线（主页/创建/邀请/成员/buff/圈地点选）。
4. 材质产出（HTML→PNG + CC0 收集）与接入。
5. 镇政厅（CE 优先+回落）与保护监听器；MainMenu 入口、TownApi/事件、文档涨号。

## 8. 验证与交付
- `./gradlew :Town:build` 通过且 `git status` 干净（含 release 留档）；`:Town:runServer` 实测：外部插件全缺时 Town 正常加载、指令通道完整；装 ArcMenu 后 GUI 全流程端到端（创建→邀请→成立→放镇政厅→免费圈地→点选扩张→买 buff→保护生效）用日志与截图举证。
- 完成后提交 `main` 并推送 `origin`（双地址自动同步镜像），提交信息 `feat(Town): 新增小镇插件（Beta_0_1_0）`。

## 9. 风险与备选
- ArcMenu Application API 细节需联调确认，是工作量与不确定性最大项；若个别交互确实无法落地，带实测证据回来确认替代，不擅自改方案。
- 图片走资源包：首次安装/更新贴图后玩家需重新接受资源包（提示与文档写清）。
- Wallet 扣费并发：先本地预检 + `debit` 幂等键兜底，失败回滚领地变更。