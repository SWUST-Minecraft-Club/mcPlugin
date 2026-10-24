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

[Beta_0_2_0 -> Beta_0_3_0] 2026.10.25 02:45:47
1.重构：ArcMenu 界面按 800×450 市政档案风格重设计，扩展为 11 页（无镇/邀请/创建/筹备/已成立/成员/成员详情/在线邀请/圈地/祝福/确认），
   使用统一安全区、原版物品图标、黄铜与绿宝石配色；视觉与命中区整体按视野 70% 居中缩放；成员、地图和祝福不再侵入底栏
2.新增：独立邀请接受/拒绝页、8 行在线玩家选择器、成员详情页、6 槽祝福分页和通用确认页；成立、圈地、放弃领地、祝福购买及危险操作均有明确确认
3.修复：全部页面跳转先由 TownUi 写齐 META；邀请输入键、成员原始名参数、地图冻结中心、同页重开会话识别、周期刷新和 Wallet 异步余额/币种预热
4.修复：invite/kick/transfer/promote/demote 指令树；/town members、/town buffs 增加可选页码与聊天降级，补 /town info [名称]、/town reload 和 /town confirm-cancel
5.修复：全局唯一邀请、接受时过期/已有小镇/人数重验、创建时清理旧邀请；转让支持离线成员且旧镇长自动降为官员，加载时规范化多镇长数据
6.修复：镇政厅放置目标与他镇区块校验、按实际破坏目标收回；矿车箱等实体容器保护、活塞仅跨界拦截、claim-already-yours 消息缺失
7.修复：Wallet 回调切回 Folia 玩家/全局调度线程；成立/圈地/祝福扣款后按原小镇重验，永久祝福并发购买退款，退款失败记录业务号日志
8.修复：YAML 快照按序号串行写入避免旧写覆盖新写；buff 重载周期即时生效，退出玩家和关服清理 UI 会话；新增模型、索引和全菜单生成 JUnit 回归测试
9.调整：文本输入改用 ArcMenuTextInput API 唤起（回车即提交，弃用需先按 Esc 的 Arcitx）；输入模式改为玩家自选（默认 mouse=客户端本地光标渲染，保留 touch/vision），Town 不再强制切换
10.新增：圈地地图四向拖动（每次 3 块）与确认页世界内区块边界光点预览（购买绿色/放弃红色）；ArcMenu config 光标建议 size 3.0、灵敏度 3.0；Town 过渡与标题微动画增量合并进 animations.yml（首次写入自动备份）
