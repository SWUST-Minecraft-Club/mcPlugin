# MainMenu · 方块手机

`Beta_1_8_0`：Folia 26.3，Java 25。安装 ArcMenu 3.2 后，`/cd`、ESC 主菜单按钮、时钟入口统一打开原生 YAML 主页。
竖屏机身、暖象牙桌面、玉绿小组件、原版物品应用图标和底部快捷栏。
首页六个常用入口，配套应用抽屉、“我的”和管理设置；手机外观全部由原生 YAML 构成，无需新增图片资源包。

生图概念、实现取舍与提示词见 [设计规划](./assets/DESIGN.md)，实际游戏截图见 [验证记录](./assets/VALIDATION.md)。

手机两侧支持多张可选卡片：默认隐藏，YAML 和插件 API 注册，按内容高度自动分栏、居中和独立分页。
启用方式、技能/积分接入与 API 示例见 [侧卡指南](./SIDECARDS.md)。从 Beta_1_7_0 更新侧卡无需替换现有手机 YAML。

## 安装与配置

1. 将留档的 MainMenu jar 放进服务端 `plugins/`，同时安装 ArcMenu 3.2。
2. 启动一次，默认页面会释放到 `plugins/ArcMenu/menus/mainmenu/`。
3. 直接编辑其中的 YAML，执行 `/cd reload`。不需要运行生成脚本或重新编译插件。

从 Beta_1_6_0 或更早版本升级，须备份并更新首页和公共头像节点；可将交付包的 10 页模板复制到上述目录，或备份后删除对应旧页并执行 `/cd reload`。首页“下一页”和页码圆点进入应用抽屉；其中箭头继续翻看每页 12 个应用。

**已有 YAML 永不自动覆盖**，包括升级、重载和重启。需要恢复某一页默认配置时，先备份，再删除该页并执行 `/cd reload`。
没有安装 ArcMenu、接入失败或关闭 `menu.arcmenu.enabled` 时，使用原有背包界面。
从关闭状态重新启用后执行 `/cd reload` 即可重新接入；安装或移除插件本身仍需重启服务端。

| 文件 | 用途 |
| --- | --- |
| `mainmenu-home.yml` | 手机首页；状态栏、时钟、六个应用、底部快捷栏 |
| `mainmenu-features.yml` | 应用抽屉；全部可见功能，每页 12 项原版物品图标 |
| `mainmenu-economy.yml` | 我的资产；Vault 主币种余额、服务状态、刷新与原钱包入口 |
| `mainmenu-profile.yml` | 我的；玩家头像、在线时长、挂机时长、在线人数 |
| `mainmenu-manage.yml` | 桌面管理；功能区、固定位置两个标签，各 16 项独立分页 |
| `mainmenu-attribute-function.yml` | 普通功能的显示、排序和固定方式 |
| `mainmenu-attribute-fixed.yml` | 固定功能的显示、位置和布局方式 |
| `mainmenu-attribute-placeholder.yml` | 占位符的排序和删除 |
| `mainmenu-position.yml` | 固定位置选择 |
| `mainmenu-custom.yml` | 自定义功能创建入口；调用 ArcMenu 原生输入通道 |

默认模板源码在 [arcmenu](./src/main/resources/arcmenu/)，开发期生成器在 [generate_menus.py](./assets/generate_menus.py)。
生成器只重建源码资源，**不参与服务端运行，也不读取或改写服务端的 YAML**。

## 用 YAML 控制主页

首页的 `frontend` 控制视觉，`backend` 控制点击与提示。比如在 `mainmenu-home.yml` 修改：

```yaml
frontend:
  page-title:
    # 保留现有坐标、字号等字段
    content: '&#283F35欢迎回家'
  town-icon:
    # 原版模型直接可用；头颅、方块和普通物品应分别选择合适的 context
    material: minecraft:emerald
    context: FIXED
backend:
  town-open:
    # 保留现有 x、y、width、height；视觉和命中区域要一起移动
    condition: '{meta:mm-bind-town-available} == true'
    actions:
      left:
        - 'open: mainmenu:feature {meta:mm-bind-town-id} left'
      right:
        - 'open: mainmenu:feature {meta:mm-bind-town-id} right'
```

这是**局部修改示例**，不是整页替换文件。左右键、潜行左右键和中键可以分别配置。
圆角用不重叠的矩形带拼成；配色使用标准 YAML 锚点，例如 `color: &phone-bg '#F4F1E8'`。
修改首次定义的 `&phone-bg` 会同步所有 `*phone-bg` 引用；`phone-sage`、`phone-gold`、`phone-dock` 同理。
也可以把动作改成 ArcMenu 支持的 `open: 其它菜单ID`、`tell:`、玩家命令等；MainMenu 不会重新生成你的动作。
详细语法参考仓库的 `./docs/exp/arcmenu-yml知识库.md`，运行版本差异以实际 ArcMenu 3.2 校验结果为准。

默认画布为 800×450，`pixels-per-block: 42.7`、`distance: 2`。默认坐标已经包含渲染补偿；
直接调整坐标后应在游戏内核对，设计坐标与 YAML 原始坐标不是 1:1。
默认页面不设置全局主菜单和打开命令，避免与其它 ArcMenu 页面争用。
手机的状态栏信号、电池图形为外观装饰，时钟、日期、问候、在线人数由实际数据刷新。
桌面“桌面管理”对普通玩家显示为“我的时光”，对应物品也会切换为时钟；管理路由仍独立校验权限。

### 绑定其它功能

在 MainMenu 的 `config.yml` 中配置绑定：

```yaml
menu:
  arcmenu:
    enabled: true
    home-menu: mainmenu-home
    clock-zone: Asia/Shanghai
    bindings:
      town: town:main
      wallet: wallet:wallet
      music: concertomenu:menu
      notice: joinannounce:notice
      time: realtime:clock
      playtime: yudreamplaytime:my-playtime
      shop: shop:main
```

新增绑定 `shop` 后，可在任意主页文本里写 `{meta:mm-bind-shop-plain-name}`，
按钮用 `open: mainmenu:feature {meta:mm-bind-shop-id} left`。
绑定名使用小写字母、数字或连字符，最长 32 字符，首字符为字母或数字。
缺失的内置绑定使用默认值；显式设置空字符串可以禁用该绑定。
功能未注册或被管理员隐藏时，`available` 为 `false`，路由也会拒绝调用。
主页卡片的静态标题和装饰模型由 YAML 作者决定；改变绑定后，可同时调整对应标题和模型。

`home-menu` 可改为另一个已经存在的菜单 ID（小写字母、数字、下划线和连字符）。
插件同样会向该主页提供下列 META，并按刷新间隔更新。

## 动态数据契约

所有数据都在当前查看者的 `META` 作用域。使用 `{meta:键名}` 引用，不能跨玩家共享缓存。

| 键 | 内容 |
| --- | --- |
| `mm-player` / `mm-online` | 查看者名字 / 在线人数 |
| `mm-player-material` | 校验后的玩家皮肤材质；无效纹理或第三方认证 URL 使用默认玩家头，避免整页失败 |
| `mm-economy-available/balance/short-balance/currency/provider/status` | Vault 是否可用、完整/缩写余额、币种、提供者和状态；实际键用连字符连接，失败显示 `--` |
| `mm-player-short` | 按手机宽度截短的查看者名字 |
| `mm-clock` / `mm-date` / `mm-greeting` | 现实时间、中文日期、按时段问候；时区由 `clock-zone` 指定 |
| `mm-tools-label/material/tooltip` | 随管理权限变化的桌面工具入口 |
| `mm-feature-count` | 当前可见功能数，不含占位符 |
| `mm-admin-label` | 管理员为“菜单管理”，普通玩家为空 |
| `mm-page` / `mm-pages` | 全部功能当前页 / 总页数 |
| `mm-apps-page/pages/count` | 手机应用抽屉页码、页数、可见应用数；每页 12 项 |
| `mm-manager-side/page/pages/count/title` | 管理页当前标签及独立分页数据 |
| `mm-function-tab` / `mm-fixed-tab` | 带选中色的管理标签文案 |
| `mm-function-page/pages/count` | 管理左栏当前页 / 总页数 / 数量，实际键分别用连字符连接 |
| `mm-fixed-page/pages/count` | 管理右栏对应信息 |
| `mm-selected-owner/order/visibility/position` | 所选功能的插件、排序、显示状态、固定位置 |
| `mm-position-0-occupant` … `mm-position-53-occupant` | 指定逻辑位置当前占用者的 ID |

下列前缀均支持同一组字段：

- `mm-bind-<绑定名>`：主页绑定功能。
- `mm-slot-0` … `mm-slot-53`：当前功能页的逻辑位置。
- `mm-function-0` … `mm-function-15`、`mm-fixed-0` … `mm-fixed-15`：当前管理列表。
- `mm-selected`：当前选择的功能。
- `mm-app-0` … `mm-app-11`：当前手机应用抽屉；按逻辑页、位置排序，过滤占位符与隐藏项。
- `mm-manager-0` … `mm-manager-15`：管理页当前标签下的条目。

| 后缀 | 内容 |
| --- | --- |
| `-id` / `-available` / `-clickable` | 功能 ID / 是否可显示 / 功能声明是否可点击 |
| `-name` / `-plain-name` / `-short-name` / `-heading` | 带颜色名 / 纯文本名 / 短名称 / 截短标题 |
| `-badge` | 有功能时为 `◆`，空位为空 |
| `-lore` / `-tooltip` | 悬停正文 / 包含名称的完整提示；空位显示“暂无功能” |
| `-line-0` … `-line-7` | 前 8 行悬停文本，保留颜色 |
| `-plain-line-0` … `-plain-line-7` | 对应纯文本行，可着主题色显示 |
| `-summary` | 截短的第一行纯文本，适合卡片摘要 |
| `-material` / `-context` | 从功能图标提取的 ArcMenu 材质表达式 / 建议显示视角 |

另外，`mm-slot-<位置>-conflict` 为位置冲突提示；管理列表的 `-state`、`-order` 为状态和排序值。
`available` 不等于 `clickable`，例如时间功能可见但不可点击。动态正文应居中，预留长名称和金额的空间。

### 物品渲染边界

默认主页使用 YAML 中指定的原版模型。唱片机、头像使用 `GUI` + `rotation.y: 180` + `scale.z: 0.1`；
平面物品使用 `FIXED`，X/Y/Z 等比缩放。参数来自用户提供的 UltimateShop-ArcMenu 1.0.0，并在 26.3 实际截图核对，
完整证据和分类差异见仓库的 `./docs/exp/arcmenu-item-angles.md`。

应用抽屉、管理和属性页使用动态材质，统一 `context: GUI`、Y 轴旋转 180°、正深度 0.1。
它能让本次验证的头颅、唱片机与平面图标保持库存视角；资源包自定义的显示变换仍需另行核对。
手机列表的空位用淡灰玻璃片作低调占位，标注“空位”，不能点击。`item` 节点不能构建空气，`context` 和 `visible` 是静态字段，
不能通过虚构的前端 `condition` 或三个互斥 AIR 模型实现动态显示。

通用 `-material` 不可用时返回纸；`mm-app-*` 与 `mm-manager-*` 的空位材质为淡灰玻璃片。
`-context` 是默认动态列表的 `GUI` 视角提示，需配合上述旋转与深度；不能直接写成动态 `context`。
头像纹理只接受官方 `textures.minecraft.net/texture/` 的哈希，拒绝无效 Base64、第三方认证皮肤 URL；后者显示默认头。默认模板不再直接使用玩家名构建头颅。
桥接可以提取原版材质、头颅纹理、传统 CustomModelData、皮革染色和旗帜图案；
这不等同于任意 `ItemStack` 元数据无损转换。背包回退界面继续保留完整物品。

## 动作路由

统一通过 `open: mainmenu:<路由> [参数]` 调用。插件会在玩家所属 Folia 区域线程执行，
每次重新检查权限、功能是否仍存在、是否被隐藏以及是否允许点击。

| 路由 | 作用 |
| --- | --- |
| `home` | 回到配置的首页，功能分页重置为 1 |
| `apps [偏移]` | 手机应用抽屉；每页 12 项，`-1` / `1` 翻页 |
| `economy` | 原生资产页；只读取 Vault，不直接修改余额 |
| `profile` | 打开“我的” |
| `tools` | 管理员打开桌面管理，普通玩家打开“我的” |
| `features [偏移]` | 保留的逻辑分页路由，仍打开应用抽屉；修改 `mm-page`，供自行编写 54 位置模板使用 |
| `feature <功能ID> <点击类型>` | 调用原功能回调；先关闭当前 ArcMenu |
| `slot <0..53> <点击类型>` | 按当前页逻辑位置调用原功能 |
| `manage [left或right] [偏移]` | 切换管理标签及其独立翻页 |
| `edit <功能ID>` / `attribute` | 选择功能 / 回到其属性页 |
| `toggle` | 显示或隐藏所选功能 |
| `order <-4或-1或1或4>` | 调整所选功能排序 |
| `fixed` / `position` / `pick <位置>` | 切换固定方式 / 打开位置选择 / 确认位置 |
| `placeholder-add` / `placeholder-delete` | 添加占位符 / 删除所选占位符 |
| `custom` / `input` | 创建说明页 / 请求名称并创建自定义功能 |

点击类型为 `left`、`right`、`shift-left`、`shift-right`、`middle`。
从 `manage` 开始的管理路由均要求 `mainmenu.admin`（默认 OP）；页面本身也声明该权限。
原生名称输入最长 50 字符；在未装 Editor 的客户端使用铁砧界面，点击右侧结果提交，关闭则取消。
实际通道由 ArcMenu 选择。

## 数据、重载与验证

原有注册 API、隐藏释放位置、固定位置冲突、排序、分页和占位符规则继续使用。
54 个位置与每页 21 项是底层注册/背包规则；手机将实际可见功能按逻辑位置重新排成每页 12 个应用。
固定功能在应用抽屉中列在前面，随后是功能区；占位符继续影响逻辑布局，但不显示在应用抽屉。
管理状态仍保存到 `plugins/MainMenu/features.yml`，自定义功能仍定义在 `plugins/MainMenu/custom-features.yml`。
功能插件无需修改注册代码，参见仓库的 `./docs/dev/功能插件接入规范.md`。

`/cd reload` 同时读取 MainMenu 配置、自定义功能和 ArcMenu YAML；ArcMenu 校验失败会报告错误并保留已经发布的菜单。
该操作会重载 ArcMenu 的整个菜单库，打开中的页面可能被关闭；修改任何其它插件的坏 YAML 也会阻止发布。
它不自动重建新增图片的资源包；使用新图片时仍按 ArcMenu 的资源流程操作。

构建与配置测试：`./gradlew :MainMenu:build`。游戏内验证、截图与适用边界见 [验证记录](./assets/VALIDATION.md)。

手机与钱包更新包已接入 CI 和 GitHub Release；下载、发布入口见 [版本与产物规范 §5](../../docs/dev/版本与产物规范.md#5-发布到-github-releases)。本地构建相关插件和 MainMenuRepair 后执行 `python3 ./scripts/package-mainmenu-wallet.py`，默认从本次 `build/libs` 输出打包；离线从留档生成时使用 `--artifacts archive --vault-jar <Folia-Vault-JAR>`。Vault 下载来源与哈希固定在依赖锁；部署合并片段及迁入步骤见 [安装说明](./deploy/安装说明.md)。
