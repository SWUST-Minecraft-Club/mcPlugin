# Town ChangeLog（测试版）

[Beta_0_1_0] 2026.10.25 00:12:37
1.新增小镇系统首个版本：筹备成立（最低人数可配）、邀请 / 接受 / 拒绝、成立扣费（Wallet）、解散、踢出 / 转让 / 升降职、退出
2.新增镇政厅道具（CraftEngine 自定义物品优先、原版材质回落，PDC 标记）：放置即设小镇中心 + 传送点 + 免费自动圈地（半径可配），市长可挖回
3.新增圈地系统：必须与已有领地相连（斜角相连可配）、每块扣费、单镇上限可配、世界白名单可配
4.新增领地保护：破坏 / 放置 / 容器 / 交互 / 桶 / 爆炸 / 活塞跨界 / 火焰蔓延 / 展示框与盔甲架 / 实体破坏，全部可开关；成员放行，town.bypass 无视
5.新增领地 buff：配置化（药水效果 + 等级 + 时长 / 永久），对身处本镇领地的成员周期施加，到期自动失效
6.新增 ArcMenu 界面（Application API）：主页（三种形态）、创建（原生聊天输入，缺 ArcMenu 回落聊天捕获）、成员管理、在线玩家选择器、领地祝福商店、9×9 圈地点选地图（左键购买 / 右键放弃 / 每秒刷新状态）
7.新增 MainMenu 功能入口 town:main（按查看者渲染自己的小镇信息）
8.新增公开 API com.example.town.api（TownApi + Founded/Disbanded/Claim/Unclaim/BuffActivate 事件）
9.新增指令等价通道 /town 全量子命令与权限（town.use / town.admin / town.bypass），未装 ArcMenu 时功能完整可用

[Beta_0_1_0 -> Beta_0_2_0] 2026.10.25 01:30:00
1.重构：界面整体改为 ArcMenu 原生 YAML 菜单（插件启动生成 `plugins/ArcMenu/menus/town-*.yml`，渲染与命中全部交给 ArcMenu），
   删除自绘实体渲染层 `arcui/`（实测巨字重叠 / 命中错位，弃用）；几何按 arcmenu-designing 技能实测补偿生成（800×450、ppb 42.7、G/ZK 缩放、rectangle 顶部补偿）
2.新增：鼠标输入模式——ArcMenu 服务器配置 `mouse.policy: "force-mouse"` + 打开菜单时 `setPointerMode(MOUSE)` 兜底；
   圈地地图格改为光标直觉：左键购买 / 右键放弃
3.调整：名称输入改用 Arcitx 前端输入框（显示模板 {input}{caret}），删除聊天捕获；创建/邀请等全部操作保持 GUI 路径
4.新增指令：`/town members [页]`（成员界面）、`/town claimmap <行> <列>`（点选圈地）、`/town claimmap center`、
   `/town unclaimat <行> <列>`；危险操作（退出/解散/踢出/转让）改为「60 秒内再点一次确认」（`/town confirm <action>`），删除 `/town cancel`
5.修复：菜单校验器报错——region tooltip 需为字符串/字符串数组（原生成嵌套对象）、frontend 文本不支持 condition（改中性文案）、
   元素 id 需跨 frontend/backend 全局唯一、Arcitx submit 必须指向已存在命中区（坑详见 docs/exp/arcmenu-yml知识库.md §25）
