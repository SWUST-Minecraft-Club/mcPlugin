# 手机侧卡

MainMenu `Beta_1_8_0` 在手机两侧提供独立卡片，可显示积分、技能等级、任务进度或快捷操作。
需要 Folia 26.3 / ArcMenu 3.2。卡片由 MainMenu 绘制在原生手机的同一平面上；手机本身仍由 ArcMenu YAML 编辑。

## 启用与配置

首次启动仅在文件缺失时生成 `plugins/MainMenu/sidecards.yml`。默认 `enabled: false`，插件注册卡片也不会替管理员打开开关。
将其改为 `true` 后执行 `/cd reload`。已有手机 YAML 无需替换；本版不会覆盖管理员修改。

```yaml
enabled: true
menus: [home, mainmenu-features, mainmenu-profile, mainmenu-economy]
layout:
  left-x: 32
  right-x: 552
  width: 216
  top: 35
  height: 360
  gap: 12
  max-per-column: 3
cards:
  points:
    enabled: true
    order: 10
    side: auto
    title: 我的积分
    icon: {material: EMERALD, context: FIXED}
    rows:
      - {label: 玩家, value: '{player}'}
      - {label: 在线, value: '{online} 人'}
    buttons:
      - id: apps
        label: 全部应用
        actions: ['route: apps']
overrides: {}
```

这是完整的最小示例；“我的积分”标题下目前展示玩家和在线人数，接入实际积分前应修改标题或替换为真实数据。
随插件附带的配置有资产、服务器、快捷操作三张可用卡；技能、任务、小镇固定值示例均单独关闭。

`home` 自动跟随 MainMenu `config.yml` 的 `menu.arcmenu.home-menu`；其它值选择 MainMenu 自带页面的菜单 ID。默认管理页不展示。
支持默认 800×450、ppb 42.7、distance 2 的手机及 ArcMenu 鼠标模式下的等效画布。
修改画布比例后侧卡跳过该页并记录诊断，避免用错误坐标拦截点击。

### 卡片字段

| 字段 | 说明 |
| --- | --- |
| `enabled` | 是否展示这张 YAML 卡，默认 true |
| `order` / `side` | 排序值；位置 `auto`、`left`、`right` |
| `min-height` | 最小高度，默认 88；实际高度同时考虑内容 |
| `permission` | 可选，查看者需要此权限 |
| `requires-plugin` | 可选，仅指定插件启用时显示 |
| `title` | MiniMessage 标题，过长截短 |
| `icon.material` | Bukkit 原版材质名；`none` 不显示图标 |
| `icon.context` | 普通物品 `FIXED`；库存方块图标显式选 `GUI` |
| `rows` | 最多 8 行，含 `label`、`value`；有 `progress` 时显示十格进度条 |
| `buttons` | 最多 2 个，含唯一 `id`、`label`、`actions` |
| `actions` | 点击卡片正文的动作；未配置时只作展示 |

`progress`、`max` 可填数值或真实占位符；进度为两者之比，限制在 0～1；`max` 默认 1。
如 `progress: '18', max: '30'` 为 60%。不能解析的进度为 0。
物品角度遵循已记录的两类参数：`FIXED` 三轴等比，`GUI` 额外 Y=180°、正深度 0.1。
自定义资源包模型需实际核对；不使用 `Material.isBlock()` 猜测图标类别。

### 动态数据

文案支持 MiniMessage、`{player}`、`{online}`、`{meta:mm-*}` 和已安装 PlaceholderAPI 的真实字段。
例如资产卡直接读取 `{meta:mm-economy-short-balance}` 和 `{meta:mm-economy-currency}`。
未解析的 PAPI 字段显示 `--`。技能、农场、积分插件的字段由其自身文档或 API 确定；MainMenu 不自带星露谷技能系统。

### 点击动作

```yaml
actions:
  left: ['route: economy']
  right: ['message: <green>右键查看提示']
```

也可写列表，作用于所有点击：`actions: ['route: economy']`。
支持 `all`、`left`、`right`、`shift-left`、`shift-right`、`middle`；实际可用按键由 ArcMenu 输入模式与客户端决定。
按钮的 `actions` 使用同一语法。

| 动作 | 行为 |
| --- | --- |
| `route: economy` | 调用 MainMenu 路由，完整路由表见 MainMenu README |
| `feature: town:main` | 调用已注册功能；保留隐藏、权限、可点击检查与点击类型 |
| `menu: custom-menu` | 打开已有原生 ArcMenu 页面 |
| `player: home` | 关闭手机，以查看者本人权限执行命令 |
| `message: <green>你好` | 向查看者发送消息，可继续执行后续消息 |
| `close` | 关闭手机 |

打开页面、命令、关闭动作会停止本次后续动作。没有 console、OP 或提权动作。
每次点击重新读取卡片可见性和注册状态；卡片消失或布局变化时拒绝旧位置的点击。

## 自动布局算法

1. 合并 YAML 与 API 卡片，先过滤关闭、权限不符、依赖未启用或内容提供器返回 null 的卡片。
2. 按 `order`、`id` 稳定排序。
3. 实际高度为 `max(min-height, 60 + 普通行×20 + 进度行×32 + 有按钮时30)`，宽度由列配置决定。
4. `auto` 选择当前页能容纳且已用高度较小的列；平手放左列。固定左右的卡片只考虑指定列。
5. 同时检查高度、间距和每列数量；放不下就开始下一页，保持排序，不回填之前的页。
6. 每列整体垂直居中。0 张不生成实体或点击区域；1 张、2 张及更多卡片均按同一规则处理。

默认每列最多 3 张，不保证一页总是 6 张：多行高卡片可能提前分页。
单卡超过整列高度时跳过并记录一次诊断，应减少行数或将详情放入子菜单。
卡片页码保留到玩家退出，独立于手机应用抽屉页码；数量减少时钳制到有效页。

仅标题、数值或进度更新时复用现有实体与坐标。卡片注册、显隐、排序、图标、行结构或按钮结构变化时重新排布。
刷新周期使用 MainMenu 的 `menu.refresh-seconds`，默认 1 秒；两 tick 更新平面和悬停边框。
配色可通过 `theme` 的 `background/border/hover/text/muted/value/track/button` 修改，格式为 `'#RRGGBB'`。

## 插件注册 API

依赖与 Folia 线程约定沿用仓库的 `./docs/dev/功能插件接入规范.md`。
编译依赖 `compileOnly project(':MainMenu')`，运行声明 MainMenu 在自身之前加载并加入类路径。

```java
import com.example.mainmenu.api.MainMenuApi;
import com.example.mainmenu.api.MenuSidecard;
import java.util.List;
import org.bukkit.Material;

// 在自身 onEnable 中调用；levelOf/progressOf 由技能插件读取自己的内存缓存。
MainMenuApi.sidecards().register(this,
    MenuSidecard.builder("farming:skills")
        .order(20)
        .side(MenuSidecard.Side.AUTO)
        .visibleWhen(viewer -> viewer.hasPermission("farming.use"))
        .contentForViewer(viewer -> new MenuSidecard.Content(
            "生活技能", Material.WHEAT,
            List.of(MenuSidecard.Row.progress("耕种", "Lv." + levelOf(viewer), progressOf(viewer)))))
        .onClick((viewer, action, click) -> openSkills(viewer))
        .build());
```

`progressOf` 返回 0～1；内容提供器返回 null 可对单个查看者隐藏。MiniMessage 或按钮需要使用完整 `Content` 构造器，
参数为 `Component title, Material icon, IconContext, List<Row>, List<Button>`。
纯展示卡不设置 `onClick`。回调的 `action` 是 `card` 或按钮 ID；回调前手机关闭，可直接打开自己的界面。
回调与提供器在查看者所属区域线程执行，不得阻塞网络或磁盘 IO。异常隔离并记录，不影响其它卡片。

注册 ID 必须唯一，建议 `<插件>:<卡片>`；`yaml:` 保留给配置卡。
`MainMenuApi.sidecardsOrNull()` 可做可选接入，也可从 Bukkit 服务系统读取 `MenuSidecardRegistry`。
插件禁用会自动注销其卡片；自己的 `onDisable` 也可调用 `unregisterAll(this)`。

管理员可覆盖 API 卡片的开关、左右位置、排序与额外权限，不改其数据源或回调：

```yaml
overrides:
  'farming:skills': {enabled: true, side: right, order: 20, permission: farming.use}
```

## 诊断与验证边界

当前查看者可用 META：`mm-sidecards-count/page/pages`，以及当前页 `mm-sidecards-slot-<序号>-id/x/y/width/height`。
位置为上述设计单位；关闭页面时清理，不用于持久保存玩家数据。
单个卡片不展示时检查其权限、依赖、提供器和服务端诊断日志；错误配置重载保留上一份有效侧卡配置。

实际环境、输入测试、截图与像素量测见 [验证记录](./assets/VALIDATION.md)。升级其它 Minecraft 或 ArcMenu 版本需重新核对。
