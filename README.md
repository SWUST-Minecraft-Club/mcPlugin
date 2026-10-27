# Minecraft 26.3 插件集合

[![Build](https://github.com/zhou-BZDDX/mcPlugin/actions/workflows/build.yml/badge.svg)](https://github.com/zhou-BZDDX/mcPlugin/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Minecraft](https://img.shields.io/badge/Minecraft-26.3-green.svg)
![Java](https://img.shields.io/badge/Java-25-orange.svg)

Minecraft **26.3**（Folia）多插件仓库。所有插件放在 `./Minecraft-26_3-plugin/<插件名>/`，由仓库根目录的一个 Gradle 工程统一管理：共享构建配置和服务端版本，每个插件又能单独打包、单独启动测试服。

目前仓库里有 10 个插件：**[MainMenu](./Minecraft-26_3-plugin/MainMenu)**（主菜单框架，其它插件以它为依赖注册功能）、
**[RealTime](./Minecraft-26_3-plugin/RealTime)**（在主菜单里显示当前时间）、
**[YudreamPlaytime](./Minecraft-26_3-plugin/YudreamPlaytime)**（主菜单里显示查看者自己的在线时长，MainMenu 的伴侣插件）、
**[Wallet](./Minecraft-26_3-plugin/Wallet)**（本地多币种 / Vault 主币种 / 旧官网一次性迁入 + 钱包 API 和管理指令）、
**[ConcertoMenu](./Minecraft-26_3-plugin/ConcertoMenu)**（主菜单唱片机 → 打开客户端 Concerto 界面）、
**[JoinAnnounce](./Minecraft-26_3-plugin/JoinAnnounce)**（进服公告：dialog 弹窗 / 聊天栏可切换 + 首次进服文案 / 进出服广播 + 主菜单公告入口）、
**[ChatPrivacy](./Minecraft-26_3-plugin/ChatPrivacy)**（聊天防举报：把公开聊天改写为无签名系统消息，客户端无法把它作为举报证据）、
**[HandbookCore](./Minecraft-26_3-plugin/HandbookCore)**（全局双页手册：多插件内容 API、CE 图文与动态书签、全文检索和自动分页）、
**[Town](./Minecraft-26_3-plugin/Town)**（小镇经营：政厅核心 / 自主连通领土 / 矩形地块与租赁 / 镇库科技政策 / 玩家商业 / 建筑迁移，
26 页 ArcMenu 界面、Wallet / Vault 经济、CraftEngine 素材及 GitLab 资源分发；部署见模块 README）、
**[yudream-minecraft-server](./Minecraft-26_3-plugin/yudream-minecraft-server)**（把玩家动态上报给 YuDream Admin 的 Bukkit 桥接插件，
自带本地时长统计与断线补发，运行期反射兼容 Folia）。新增插件用 `./gradlew newPlugin -PpluginName=你的插件名`。

## 规范索引

仓库里的约定集中在以下几处，动手前先看一眼：

| 规范 | 位置 | 内容 |
| --- | --- | --- |
| AI 协作入口 | [`./AGENTS.md`](./AGENTS.md) | 硬规则、开工必读、沟通字典、现状快照 |
| 开发规范总纲 | [`./docs/dev/conventions.md`](./docs/dev/conventions.md) | 技术栈、仓库结构与**路径约定**、编码约定、模组约定、Git、文档纪律 |
| 版本与产物规范 | [`./docs/dev/版本与产物规范.md`](./docs/dev/版本与产物规范.md) | 涨号规则、产物命名、留档、ChangeLog、发布流程、历史产物重建 |
| 功能插件接入 | [`./docs/dev/功能插件接入规范.md`](./docs/dev/功能插件接入规范.md) | 依赖声明、注册 API、位置 / 排序 / 冲突规则 |
| 经验与坑 | [`./docs/exp/`](./docs/exp/) | Paper-Folia 插件 / Fabric 模组 / 构建环境坑库，遇到问题先查 |
| 计划（不提交） | `./docs/plan/` | 阶段计划与任务拆解；长期结论沉淀回 `docs/dev` 或 `docs/exp` |
| 新增插件 | [新增插件](#新增插件)、[`./templates/plugin-template/`](./templates/plugin-template) | 脚手架、目录名 / 包名 / 类名一致性 |
| CI | [`./.github/workflows/`](./.github/workflows) | `build.yml` 构建、`release.yml` 发布 |

> 一条规则只定义一次：规范写在 `./docs/dev/`，经验写在 `./docs/exp/`，README 只描述「这是什么、怎么用」。

## 环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 25 及以上 | Folia 26.3 要求 Java 25；本机装 JDK 26 也能用，Gradle 用工具链把字节码编译到 25 |
| Gradle | 9.8.1 | 仓库自带 wrapper（`gradlew`），不需要手动安装 Gradle |
| Folia API | `26.3.build.5-alpha` | 在 `gradle.properties` 中配置，对所有插件生效（Folia API 是 Paper API 的超集） |

> 本机没有 JDK 25 时，`settings.gradle` 里的 foojay 插件会自动下载对应版本的工具链，无需手动安装。

## 目录结构

树下每一项都相对仓库根目录（也就是 `./xxx/xxx` 那样的相对路径）：

```
.
├── AGENTS.md                       # AI 协作入口：硬规则 / 必读清单 / 沟通字典 / 现状快照
├── build.gradle                    # 所有插件共享的构建配置（依赖、工具链、Folia 测试服）
├── gradle.properties               # 统一版本：Minecraft / Folia API / Java（升级版本改这里）
├── settings.gradle                 # 自动发现 ./Minecraft-26_3-plugin/ 下的插件目录
├── docs/
│   ├── dev/                        # 规范层（提交）：总纲 + 版本与产物 + 功能插件接入
│   ├── exp/                        # 经验层（提交）：坑与经验库
│   ├── plan/                       # 计划层（不提交）：阶段计划与任务拆解
│   └── local.md                    # 本机机密（不提交）：路径 / 账密 / 代理 / token
├── templates/
│   └── plugin-template/            # newPlugin 用的模板（一份可直接编译运行的示例插件）
│       ├── build.gradle            # 只写版本号与额外依赖，公共配置在根 build.gradle
│       └── src/main/{java,resources}
├── Minecraft-26_3-plugin/          # 插件都放这里，一个目录一个插件
│   ├── MainMenu/                   # 主菜单框架 + 功能插件注册 API
│   ├── RealTime/                   # 在主菜单里显示当前时间（功能插件参考实现）
│   ├── YudreamPlaytime/            # 主菜单里显示查看者自己的在线时长（MainMenu 伴侣插件）
│   ├── Wallet/                     # 主菜单里的钱包（货币 / 余额 / 流水）+ 供后续插件依赖的钱包 API
│   ├── ConcertoMenu/               # 主菜单里的 Concerto 音乐入口（唱片机 → 打开客户端 Concerto 界面）
│   ├── JoinAnnounce/               # 进服公告（本人公告 / 首次进服文案 / 进出服广播 + 主菜单公告入口）
│   ├── ChatPrivacy/                # 聊天防举报（公开聊天改写为无签名系统消息）
│   ├── HandbookCore/               # 全局手册核心（Dialog 双页书 + CE 图片/物品 + 多插件 API）
│   ├── Town/                       # 小镇经营、租赁、商业与建筑迁移（ArcMenu + WalletApi + CraftEngine）
│   └── yudream-minecraft-server/   # YuDream Admin 桥接插件：上报 + 本地时长统计 + 断线补发（Java 8 / Bukkit 1.13.2 目标）
├── fabric-26_3-mod/                # Fabric 客户端模组（独立 Gradle 工程，不参与上面的插件构建）
│   ├── MainMenuRepair/             # 清理 ESC 暂停菜单的界面修复模组
│   └── MinecraftMcp/               # MCP 客户端测试能力；共享核心 + 独立游戏版本适配器
├── release/                        # 各版本的构建产物留档（提交进仓库）
├── scripts/                        # 仓库维护脚本（如 rebuild-history.sh）
├── run/                            # 共用的测试服目录（服务端、世界、插件配置都在这里，已 gitignore）
└── .github/                        # CI、Release、Issue/PR 模板、Dependabot
```

> 路径约定（一律 `./xxx/xxx` 相对形式、脚本自定位仓库根目录、禁止盘符路径）见
> [`./docs/dev/conventions.md`](./docs/dev/conventions.md) §2。

## 快速开始

```bash
# 1. 生成第一个插件（自动建目录、换包名和类名）
./gradlew newPlugin -PpluginName=MyPlugin

# 2. 编译打包，产物在 ./Minecraft-26_3-plugin/MyPlugin/build/libs/
./gradlew :MyPlugin:build

# 3. 启动测试服（Folia 26.3 + 自动加载刚构建的插件），服务端文件在仓库根目录的 ./run/
./gradlew :MyPlugin:runServer
```

首次 `runServer` 会自动下载 Folia 服务端并在 `./run/` 生成世界（内部执行 `runFolia`）；如果服务端提示 `You need to agree to the EULA`，把 `./run/eula.txt` 里的 `eula=false` 改成 `eula=true`（表示你同意 [Mojang EULA](https://aka.ms/MinecraftEULA)）后重新执行即可。

## 常用命令

| 命令 | 作用 |
| --- | --- |
| `./gradlew build` | 构建**所有**插件 |
| `./gradlew :插件名:build` | 只构建某个插件 |
| `./gradlew :插件名:runServer` | 启动测试服并加载该插件 |
| `./gradlew newPlugin -PpluginName=MyPlugin` | 按模板创建一个新插件 |
| `./gradlew listPlugins` | 列出所有插件及对应命令 |
| `./gradlew clean` | 清理所有插件的构建产物 |
| `./gradlew :插件名:tasks` | 查看该插件的全部任务 |

## 新增插件

```bash
# 默认包名 com.example.<插件名小写>
./gradlew newPlugin -PpluginName=Shop

# 也可以指定包名
./gradlew newPlugin -PpluginName=Shop -PpackageName=com.myserver.shop

# 确认已注册（settings.gradle 会自动发现新目录，不用手改配置）
./gradlew listPlugins
```

脚手架做的事：把 `./templates/plugin-template/` 复制成 `./Minecraft-26_3-plugin/Shop/`，并把模板里的 `ExamplePlugin` / `exampleplugin` / `com.example.exampleplugin` 全部替换成新名字，同时搬好 `src/main/java` 下的包目录。之后把 `paper-plugin.yml` 里的 `authors`、`website` 填上就能开工。

也可以直接手动复制 `./templates/plugin-template/` 到 `./Minecraft-26_3-plugin/<插件名>/` 再逐个改名，注意目录名、`paper-plugin.yml` 的 `name`/`main`、Java 包名要一致。

## 模板里有什么

`./templates/plugin-template/` 是一份能直接编译运行的完整示例，建议先读一遍再改：

| 文件 | 内容 |
| --- | --- |
| `ExamplePlugin.java` | 主类：释放配置、注册命令（`LifecycleEvents.COMMANDS`）、注册监听器 |
| `command/HelloCommand.java` | `/hello [名字]`：权限校验、可选参数 |
| `command/AdminCommand.java` | `/mcplugin reload\|info`：子命令、配置热重载 |
| `listener/PlayerJoinListener.java` | 玩家进服欢迎（读配置） |
| `resources/paper-plugin.yml` | Paper / Folia 插件描述文件 + 权限声明（`folia-supported: true` 已内置） |
| `resources/config.yml` | 默认配置，MiniMessage 文本格式 |

> 同一台服务器上装多个插件时，注意命令名和权限节点不要重复。脚手架会把权限节点一起改名（如 `shop.hello`），但命令字面量还是模板里的 `/hello`、`/mcplugin`，记得改成自己的。

## 插件：MainMenu（主菜单）

支持原生 YAML 的 ArcMenu 方块手机，玩家可以通过三种方式打开：**ESC 菜单里的「主菜单」按钮**、**`/cd` 命令**、**手持时钟右键**（对着空气或方块都行）。

### ArcMenu 页面与 YAML 编辑

`Beta_1_8_0` 优先使用 ArcMenu 3.2：竖屏机身、状态栏、现实时间小组件、原版物品应用图标与底部快捷栏。
首页常用入口、每页 12 项的应用抽屉、“我的”、管理列表、三种属性页、位置选择与自定义功能创建，新增 Vault 资产页，共 10 页。
小镇、Vault 资产、音乐和公告从桌面直接打开；在线时长在“我的”中查看，所有注册功能自动进入应用抽屉。

启动后直接编辑 `plugins/ArcMenu/menus/mainmenu/mainmenu-home.yml`，可控制布局、配色、文案、模型、悬停提示和按钮动作，执行 `/cd reload` 生效。
已有 YAML 在升级、重载和重启时均不覆盖。绑定、自定义主页与 META / 路由说明见 [MainMenu 配置指南](./Minecraft-26_3-plugin/MainMenu/README.md)，截图和实测边界见 [验证记录](./Minecraft-26_3-plugin/MainMenu/assets/VALIDATION.md)。
没有 ArcMenu 或显式关闭 `menu.arcmenu.enabled` 时继续使用背包界面。

手机两侧可选多卡片默认关闭，支持 YAML / 插件 API、按高度自动分栏、独立分页与动态进度；接入见 [侧卡指南](./Minecraft-26_3-plugin/MainMenu/SIDECARDS.md)。

### 逻辑位置与背包回退布局

以下 54 格规则继续用于底层排序、分页和固定位置；手机将可见功能按逻辑页、位置排成每页 12 个应用。
首页和手机各页的视觉布局由 YAML 控制。玻璃板等物品外观只描述背包回退界面。

```
第1行   [0]玻璃板 [1]时间   [2]玻璃板 [3]玻璃板 [4]玻璃板 [5]玻璃板 [6]玻璃板 [7]玻璃板 [8]玻璃板
第2行   玻璃板 ×9
第3行   [18]玻璃板 [19]功能区 [20]功能区 [21]功能区 [22]功能区 [23]功能区 [24]功能区 [25]功能区 [26]玻璃板
第4行   [27]上一页 [28]功能区 [29]功能区 [30]功能区 [31]功能区 [32]功能区 [33]功能区 [34]功能区 [35]下一页
第5行   [36]玻璃板 [37]功能区 [38]功能区 [39]功能区 [40]功能区 [41]功能区 [42]功能区 [43]功能区 [44]玻璃板
第6行   玻璃板 ×9（第 5 格是管理入口，仅管理员可见）
```

- **时间**（第 1 行第 2 格）：悬停显示当前时间（东 8 区），由 [RealTime](#插件realtime当前时间) 插件提供，展示型（点击无效）。
- **玻璃板**：非固定功能位，功能插件可以用自定义位置占用。
- **功能区**：第 3~5 行、第 2~8 列共 21 格，按排序值填充，每页 21 个，可翻页。
- **箭头**（第 4 行第 1 / 第 9 格）：功能区翻页，固定功能位。
- **管理入口**（第 6 行第 5 格）：仅管理员可见可点，固定功能位。

菜单打开期间会按 `menu.refresh-seconds`（默认 1 秒）整页重绘，所以功能插件的动态文本（例如时间）会自动刷新。

### 功能插件管理（管理员）

ArcMenu 点击手机桌面的“桌面管理”进入；背包回退界面从第 6 行第 5 格命令方块进入。**只有管理员**（权限 `mainmenu.admin`，默认 OP）能操作，页面和每次管理动作都会检查权限。

手机管理页使用“功能区 / 固定位置”标签切换，每个分类 16 项/页；背包回退使用对应的左、右两栏。
手机属性页提供显示开关、±1 / ±4 排序、固定方式和位置选择；下面的位置和物品说明仅描述背包回退界面：

```
第1行   [添加占位符][黑色玻璃板][添加自定义][黑色玻璃板 ×6]
第2~5行 [左栏×4][黑][右栏×4]
第6行   [左上一页][黑][黑][左下一页][返回][右上一页][黑][黑][右下一页]
```

| 操作 | 位置 | 说明 |
| --- | --- | --- |
| 进入管理页 | 主菜单第 6 行第 5 格 | 仅管理员 |
| 添加功能占位符 | 管理页第 1 行第 1 格（漏斗） | 在功能区末尾添加一个占位符；管理页左栏用红色染色玻璃板显示，主菜单不显示 |
| 添加自定义插件功能 | 管理页第 1 行第 3 格（书与笔） | 弹出 dialog 输入名称；创建后默认纸图标，可在管理页像其它功能一样配置 |
| 进入属性页 | 点左栏 / 右栏里的功能 | 左栏 → 非固定位置属性页；右栏 → 固定位置属性页；占位符 → 占位符属性页 |
| 返回上一级 | 第 6 行第 5 格（屏障） | 属性页、位置选择页都在这格返回 |
| 显示 / 隐藏 | 属性页第 3 行第 3 格（羊毛） | 黄绿色 = 显示中，红色 = 已隐藏（占位符没有此项） |
| 调整排序值 | 非固定位置 / 占位符属性页第 3 行第 5 格（空地图） | 左键 +1、右键 −1；按住 Shift 为 ±4 |
| 删除占位符 | 占位符属性页第 3 行第 7 格（TNT） | 从管理页删除该占位符，不影响已注册的功能插件 |
| 转为固定位置 | 非固定位置属性页第 3 行第 7 格（拉杆） | 还原原来的位置（管理员设过的、或插件自己声明的 `preferredSlot`）；从来没设置过位置的才默认第 1 行第 1 格，并切到固定位置属性页 |
| 更改位置 | 固定位置属性页第 3 行第 5 格（末影之眼） | 进入位置选择界面 |
| 转为非固定位置 | 固定位置属性页第 3 行第 7 格（红石火把） | 改回非固定位置功能插件，按排序值排进功能区 |
| 设置位置 | 位置选择界面里点格子 | 功能区与翻页位置不能选；管理入口那格是屏障，表示取消 |

两条相关规则：

- **隐藏的功能不再占位** —— 原来与它冲突的插件可以正常显示，不会被挤到功能区；
- **固定位置功能插件位置冲突时**，归到功能区并显示在**最前面**，但它**依然算固定位置功能插件**（在管理页右栏里、位置也保留）。

占位符说明：

- 占位符参与功能区的排序与分页，但主菜单渲染时跳过它，所以玩家侧看到的是空位，不会出现红色玻璃板。
- 占位符属性页只有“排序值”和“删除”两个操作；删除占位符后，后面的功能会按排序值前移。
- 占位符列表与排序值存在 `plugins/MainMenu/features.yml`（`placeholders` 段 + `features.<id>.order`），重启后恢复。

### 自定义插件功能（管理员）

ArcMenu 管理页点击“新应用”，在说明页点击“输入名称并创建”；通过 ArcMenu 的原生输入通道填写名称（最长 50 字符）。背包回退界面使用第 1 行第 3 格的书与笔按钮和 dialog。确认后创建一个自定义功能，默认图标为纸。
创建出来的功能是正常的 `MenuFeature`，所以在管理页里的显示 / 隐藏、排序值、固定位置等配置与其他附属插件完全一致。

外观与点击行为写入 `plugins/MainMenu/custom-features.yml`，修改后执行 `/cd reload` 生效：

```yaml
features:
  custom:<id>:
    name: "自定义功能"           # 显示名（纯文本）
    icon: PAPER                  # Bukkit 材质名，默认 PAPER
    command: "shop"              # 点击执行的指令（不带 /；留空则点击无效）
    description:
      - "<gray>点击执行 /shop</gray>"
    order: 20                    # 默认排序值，管理页可覆盖
```

- 点击功能时以**玩家身份**执行 `command`；`command` 留空时功能只展示、点击无效。
- 点击后先关闭主菜单，下一 tick 按“玩家输入指令”的流程触发 `PlayerCommandPreprocessEvent`，
  事件未被取消时再派发。因此 PlayerTitle 这类在 `PlayerCommandPreprocessEvent` 里处理 `/plt gui`
  子命令的插件也能正常打开界面；指令不存在或玩家没有权限时会收到提示。
- `description` 是 MiniMessage 文本列表；解析失败的行会按纯文本显示。
- `icon` 无效时回退为纸并在控制台告警。
- 手工删除 `custom-features.yml` 里的条目后执行 `/cd reload`，该自定义功能会从菜单和管理页移除；
  管理状态仍保留在 `features.yml`，以后用同一 id 重新添加会恢复。

> **背包回退界面**的物品材质写死在代码里（入口=命令方块、添加占位符=漏斗、添加自定义功能=书与笔、分隔=黑色染色玻璃板、显示/隐藏=黄绿色/红色羊毛、排序值=空地图、更改位置=末影之眼、转固定位置=拉杆、转非固定位置=红石火把、占位符=红色染色玻璃板、删除占位符=TNT、位置选择界面的功能名=命名牌、返回/取消=屏障），不走配置，避免旧版 `config.yml` 缺键时被兜底成石头。属性页只有操作位和返回键，其余格子是空的。

所有改动都会写进 `plugins/MainMenu/features.yml`，重启服务端后依然有效。

### 命令与权限

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/cd` | `mainmenu.use`（默认所有人） | 打开主菜单 |
| `/cd reload` | `mainmenu.reload`（OP） | 重载配置、自定义功能与 ArcMenu YAML；校验失败保留已发布菜单 |
| `/cd debug` | `mainmenu.reload`（OP） | 输出布局、功能位置、ESC 按钮状态、配置自检 |

### 其它插件怎么挂功能

功能插件以 MainMenu 为依赖，调用 `com.example.mainmenu.api` 注册「物品 / 悬停文本 / 点击是否有效 / 点击做什么 / 自定义位置」。
完整规范见 [功能插件接入规范](./docs/dev/功能插件接入规范.md)，最简形式：

```java
MainMenuApi.registry().register(
        MenuFeature.builder("shop:main")
                .icon(Material.EMERALD)
                .name("<green>商店</green>")
                .line("<gray>点击打开商店</gray>")
                .onClick((player, click) -> openShop(player))
                .build());
```

可选项：`preferredSlot(0~53)` 自定义位置（功能区以外的格子），`order(int)` 功能区排序值（越小越靠前；默认 0 = 按注册顺序，而跨插件的注册顺序由服务端加载顺序决定，需要确定性排序时用它）。

### ESC 菜单按钮是怎么实现的

Folia 26.3 沿用 Paper 的 dialog 系统，没有“直接往暂停界面加按钮”的 API。官方途径是 dialog 系统：

1. 插件在 **bootstrapper** 阶段注册一个 dialog（`mainmenu:main_menu`），并把它声明进 `minecraft:pause_screen_additions` 标签；
2. 插件 jar 内自带一个数据包（`src/main/resources/pack/`）负责把 dialog 键填进该标签；
3. 客户端会在暂停界面为标签里的每个 dialog 生成一个按钮，按钮文字取 dialog 的 `externalTitle`（即「主菜单」）；
4. 点击后打开该 dialog 页面，页面里的「打开主菜单」按钮通过自定义点击动作回调服务端，服务端再打开 6×9 箱子菜单。

**装了客户端模组后是一步到位**：模组会把暂停界面里这个按钮直接换成"发 `/cd`"的按钮（见 [客户端模组](#客户端模组mainmenurepair)），
点击即打开 6×9 界面，不再出现中间的 dialog 页面。没装模组的玩家仍是两次点击（客户端暂停界面只能打开 dialog）。

ESC 按钮是否就绪用 `/cd debug` 查看，正常会显示：

```
内置数据包：MainMenu/mainmenu（已启用） 来源=plugin
ESC 菜单按钮：已就绪：pause_screen_additions 包含 mainmenu:main_menu（标签共 1 项）
```


### 插件：RealTime（当前时间）

`./Minecraft-26_3-plugin/RealTime` 是 MainMenu 的一个功能插件，负责**第 1 行第 2 格的"当前时间"显示** ——
这部分原先内置在 MainMenu 里，现在拆成独立插件（MainMenu 只提供菜单框架和功能注册 API）。

| 配置项 | 说明 |
| --- | --- |
| `time.material` / `time.name` | 显示用的物品与名称 |
| `time.line` | 悬停文本，`{time}` 替换成格式化时间 |
| `time.format` / `time.zone-offset-hours` | 时间格式与时区偏移（默认 `yyyy-MM-dd HH:mm:ss` / 东 8 区） |

它直接实现 `MenuFeature` 接口（而不是用 builder），因为 `description()` 每次渲染都会被调用 ——
配合 MainMenu 的周期性重绘，显示的时间会自动跟着走。这也是给功能插件的参考写法：
**需要动态文本就实现接口，让 `icon()` / `description()` 每次现算。**

## 插件：JoinAnnounce（进服公告）

`./Minecraft-26_3-plugin/JoinAnnounce` 负责玩家进服时的公告：延迟弹出 **dialog 弹窗**（默认），
也可以切回聊天栏或两者同时显示；首次进服追加专属文案，可选标题 / 提示音，并接管进服 / 退服的全服广播。
文本一律 MiniMessage，支持 `<player>` / `<online>` / `<max>` / `<world>` 占位符。
`config-simple.yml` 是无徽章的 SWUST 社团公告示例（星空社 + 社团配色文案），复制到 `plugins/JoinAnnounce/config.yml` 即可用。

| 配置段 | 作用 |
| --- | --- |
| `announce.display` | 显示方式：`dialog`（默认，只弹窗）/ `chat`（只发聊天栏）/ `both` |
| `announce` | 进服公告：`enabled`、`delay-ticks`、`lines`（弹窗正文与聊天栏文本共用），以及可选的 `title` / `sound` |
| `announce.dialog` | 弹窗标题、正文宽度、按钮文字、是否允许 ESC 关闭 |
| `announce.once-per-day` | 每位玩家每天只在当天第一次进服时弹（默认开），跨天在线不打断、服务器重启重置；时区用 `announce.day-zone-offset-hours` |
| `first-join` | 首次进服追加的文案（接在 `announce.lines` 后面，只对第一次进服的玩家生效） |
| `broadcast.join` / `broadcast.quit` | 替换原版进服 / 退服广播；`enabled: false` 时完全不干预原版消息，`message` 留空则关闭该条 |
| `menu` | 主菜单「服务器公告」入口：材质、名称、悬停文本、`order`；没装 MainMenu 时自动跳过 |

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/joinannounce reload` | `joinannounce.admin`（OP） | 重载配置，菜单入口开关 / 排序同时生效 |
| `/joinannounce preview` | `joinannounce.admin`（OP） | 立即预览一遍（聊天栏 + 弹窗 + 标题 + 音效），方便调文案 |

弹窗用 26.3 的运行时 API（`Dialog.create(...)` + `player.showDialog(...)`），不需要 bootstrapper、
内置数据包或注册表项，也不会出现在 ESC 暂停菜单里。
Folia 适配：延迟公告与弹窗都在 `player.getScheduler()` 里执行；广播直接改写事件消息，不跨区域遍历在线玩家；
配置在 reload 时整体替换为不可变快照（`volatile` 引用），不同区域线程读到的都是完整配置。

## 插件：HandbookCore（全局手册）

`Beta_0_1_1` 用 Paper Dialog 展示统一双页书，核心仅附一条可删除的更新日志，玩法分类、攻略和插图由各插件贡献。
支持原版 / CE 图标、独立书签、长文自动续页、全文检索、当前页 / 总页数、内部文章链接、外链和玩家指令。
CraftEngine 与 MainMenu 可选，缺少书本资源时回退原生文字目录。

安装包、YAML / API 示例和实机记录见 [HandbookCore 指南](./Minecraft-26_3-plugin/HandbookCore/README.md)。
客户端可选 MainMenuRepair `Beta_1_2_0` 修复书本白框、居中与重复灰色页脚。

## 客户端模组：MainMenuRepair

`./fabric-26_3-mod/MainMenuRepair/` 是一个 **Fabric 客户端模组**，直接改客户端界面，把 ESC 暂停菜单里的这两样东西真正删掉：

| 目标 | 做法 |
| --- | --- |
| 「主菜单」按钮上那句 *注：自定义选项由第三方服务器或内容提供。请小心操作！* | Mixin 掉 `PauseScreen.addCustomDialogButtons` 里对 `Button$Builder.tooltip(...)` 的调用 |
| 右下角 4 个原版图标按钮（报告 Bug / 给予反馈 / 好友 / 举报玩家） | 把 `PauseScreen.createPauseMenu()` 里的图标行 `LinearLayout.horizontal()` 换成不接收子控件的实现，4 个按钮根本不会被创建 |

**为什么用模组而不是资源包**：那句提示是客户端 `PauseScreen` 里**无条件**附加的，4 个图标也是**无条件**创建的原版按钮，服务端既没有 API 也没有配置项能关掉；资源包只能改文案和贴图（图标会变成看不见但仍可点的按钮），模组才能真正删除。

### 构建与安装

```bash
./gradlew -p ./fabric-26_3-mod/MainMenuRepair build
# 产物：./fabric-26_3-mod/MainMenuRepair/build/libs/MainMenuRepair-mc26_3-fabric-Beta_1_2_0.jar
```

把 jar 放进客户端该实例的 `mods/` 目录，重启客户端即可。模组是纯客户端的，**不需要 Fabric API，也不需要服务端做任何事**。

> 26.3 客户端不再混淆，Fabric 也没有 26.3 的 yarn/intermediary，所以编译不需要 mappings、构建不需要 remap。
> 本工程因此不依赖 fabric-loom（`maven.fabricmc.net` 在部分网络下被限速到 KB 级，loom 会卡在下载上），
> 只下载官方客户端 jar + 用 `src/mixin-stubs/java` 里的 Mixin 注解桩编译，细节见模组自己的 README。

## 客户端测试模组：MinecraftMcp

`./fabric-26_3-mod/MinecraftMcp/` 为 **Minecraft 26.3 的 Fabric 客户端测试 Mod**，通过本机 MCP 提供 26 项测试工具：截图、GUI/控件查询与点击、键盘文本、背包/容器操作、物品组件与 tooltip、玩家/世界状态、聊天命令、等待与断言。需要安装对应版本的 Fabric API。

```bash
./gradlew -p ./fabric-26_3-mod/MinecraftMcp build
# 产物：./release/MinecraftMcp/MinecraftMcp-mc26_3-fabric-Beta_0_1_0.jar
```

默认端点 `http://127.0.0.1:8765/mcp`，Bearer 令牌由首次启动自动生成，保存在客户端实例的 `config/minecraft-mcp.json`。协议/参数校验/断言位于不引用 Minecraft 的 `core`；游戏调用位于 `versions/mc26_3`，后续可添加独立版本模块并分别发布 jar。连接配置、工具参数、多版本扩展和真实客户端冒烟步骤见 [`./fabric-26_3-mod/MinecraftMcp/README.md`](./fabric-26_3-mod/MinecraftMcp/README.md)。

## 开发约定

编码约定（命令写在代码里、MiniMessage、`folia-api` 用 `compileOnly`、`api-version` 跟随游戏版本、Folia 调度器与线程归属、
插件间依赖只用 `compileOnly`、配置与持久化、测试插件命名）全部收敛在
[`./docs/dev/conventions.md`](./docs/dev/conventions.md) §3、§4、§5；踩过的坑与规避方式见 [`./docs/exp/`](./docs/exp/)。

## 测试服说明

- 所有插件共用仓库根目录的 `./run/`：只下一份 Folia 服务端、只生成一份世界，插件各自的配置在 `./run/plugins/<插件名>/`。
- **不要同时启动两个插件的 `runServer`**，会抢同一个端口。
- 想让某个插件用独立目录：在它自己的 `build.gradle` 里写 `tasks.named('runFolia') { runDirectory(file('run')) }`，就会变成该插件目录下的 `./run/`。
- 想固定服务端构建号：在根 `build.gradle` 的 `runFolia` 配置里打开 `build(5)`，构建号见 [Folia 构建列表](https://fill.papermc.io/v3/projects/folia/versions/26.3/builds)。
- 想额外装别的插件到测试服：把 jar 放进 `./run/plugins/` 即可。

## 升级 Minecraft 版本

改 `gradle.properties`，对所有插件同时生效：

```properties
minecraftVersion=26.4
foliaApiVersion=26.4.build.1-alpha
targetJavaVersion=25
```

`foliaApiVersion` 的可选构建号见 [Folia API Maven 仓库](https://repo.papermc.io/repository/maven-public/dev/folia/folia-api/)。也可以用动态版本 `26.4.build.+`（不推荐：每次构建拉到的版本可能不同，产物不可复现）。

如果只有个别插件需要停留在旧版本，可以在那个插件的 `build.gradle` 里单独覆盖：

```groovy
dependencies {
    compileOnly 'dev.folia:folia-api:26.3.build.5-alpha'
}
```

## 发布到 GitHub Releases

改好版本号、补完 ChangeLog、确认 `./release/<项目名>/` 里有对应产物之后，打个 `v*` 标签推送即可 ——
`./.github/workflows/release.yml` 会构建全部工程，将 JAR、MainMenu / Wallet / Vault 更新 ZIP 和 SHA-256 校验文件附加到 Release。
每次 CI 构建也会上传 `MainMenu-Wallet-Vault-mc26_3` artifact；ZIP 包含相关插件、10 页菜单 YAML、配置合并片段和安装说明。
完整的前置检查与命令见 [`./docs/dev/版本与产物规范.md`](./docs/dev/版本与产物规范.md) §5。

## 版本与产物规范

产物命名格式、涨号规则（含「正式版之后再改动先回到测试版」）、留档、ChangeLog 规范、发布流程、
以及用 `./scripts/rebuild-history.sh` 从 git 历史重建产物，全部收敛在
[`./docs/dev/版本与产物规范.md`](./docs/dev/版本与产物规范.md) —— 一条规则只在那里定义一次。

下面两张表是**事实**：当前版本，以及每个版本对应的提交与改动。

## 当前版本

| 项目 | 正式版 | 测试版（进行中） |
| --- | --- | --- |
| MainMenu | `1_0_1`（2026.10.18） | `Beta_1_8_0`（10 页 YAML 手机；默认隐藏的多侧卡、按高度自动布局、独立分页与 YAML / API 接入） |
| RealTime | `1_0_0`（2026.10.18） | `Beta_1_1_0`（适配 Folia；去掉固定位提示） |
| MainMenuRepair（fabric） | `1_0_0`（2026.10.18） | `Beta_1_2_0`（新增手册正文居中、焦点白框修复与重复页脚隐藏） |
| MinecraftMcp（fabric） | — | `Beta_0_1_0`（MCP 客户端测试工具；核心与游戏版本适配器分离） |
| YudreamPlaytime | `1_0_0`（2026.10.19） | `Beta_1_1_0`（适配 Folia；台账缓存加锁） |
| yudream-minecraft-server | `2_0_0`（2026.10.19） | `Beta_2_1_0`（反射适配 Folia，保持 Java 8 / 1.13.2 兼容） |
| Town | — | `Beta_0_8_0`（新增 HandbookCore 小镇玩家指南、14 篇章节与操作入口；保留经营、迁移、26 页 ArcMenu、GitLab 分发及 Wallet / Vault 结算） |
| Wallet | —（还在测试版阶段） | `Beta_0_6_1`（修复旧配置默认币种加载；本地持久多币种、Vault 可配主币种、旧官网一次性迁入；保留 API 和原钱包界面） |
| ConcertoMenu | —（还在测试版阶段） | `Beta_0_2_0`（适配 Folia；首个版本：主菜单唱片机入口） |
| JoinAnnounce | —（还在测试版阶段） | `Beta_0_4_1`（dialog 弹窗公告、默认不发聊天栏消息；默认每位玩家每天首次进服才弹；底部固定「开始冒险」+「查看完整更新日志」双按钮；`config-simple.yml` 为 SWUST 社团公告示例） |
| ChatPrivacy | —（还在测试版阶段） | `Beta_0_1_0`（首个版本：公开聊天改写为无签名系统消息，客户端无法举报） |
| HandbookCore | — | `Beta_0_1_1`（全局双页手册、多插件 API、CE 图文/物品、动态书签、检索与分页；移除内置玩法示例，仅保留可删除的更新日志） |

> 涨号规则（正式版之后再改动先回到测试版 `Beta_<第 1 位>_x_x`，转正时去掉 `Beta`、第 3 位归 0、第 2 位至少 +1，第 1 位不变）见
> [`./docs/dev/版本与产物规范.md`](./docs/dev/版本与产物规范.md) §2；
> 逐版本改动见各项目目录下的 ChangeLog：`…-ChangeLog-Beta.md` 记所有测试版，`…-ChangeLog.md` 记正式版。

## 版本历史

`./release/<项目名>/` 里保留了每一版的产物；下面是对应的提交与改动（早期被同名覆盖的版本已用 `./scripts/rebuild-history.sh` 从 git 历史重建）。

**HandbookCore（插件）**

| 版本 | 提交 | 改动 |
| --- | --- | --- |
| `Beta_0_1_0` | `e1e251a` | 全局双页手册、多个贡献插件 API、CE 图片/物品与原版图标、动态书签、检索/分页、外链/指令；15 项测试与 26.3 实机验证，API/CE/安装 ZIP 已留档 |
| `Beta_0_1_1` | 本次提交 | 移除三个内置玩法示例，仅留可删除的更新日志；旧示例备份迁移、保留自定义内容，删除后重启不再补回 |

**Town（插件）**

| 版本 | 提交 | 改动 |
| --- | --- | --- |
| `Beta_0_1_0` | `fc39d10` | 首个版本：筹备成立、邀请成员、镇政厅、圈地、保护、祝福与公开 API |
| `Beta_0_2_0` | `cc789e7` | 界面重构为 ArcMenu 原生 YAML 并强制鼠标模式 |
| `Beta_0_3_0` | `7986ae9` | 11 页菜单重设计、在线邀请/成员详情/通用确认/分页；修复指令、邀请、镇政厅、扣费与持久化一致性 |
| `Beta_0_4_0` | `c5ea791` | 圈地页改为真实渲染小地图（逐方块采样 PNG）、7×7 大格、地图拖动与区块预览 |
| `Beta_0_5_0` | 未提交 | 11 页浅灰像素卡片风；修复地图线程、资源包索引、并发与尾随重建 |
| `Beta_0_6_0` | `1593cfb` | 自主连通领土、矩形地块与外镇租赁、镇库发展、离线商业与收件箱、三维建筑迁移和恢复；59 项测试与双客户端联调 |
| `Beta_0_6_1` | 本次提交 | 修复首次 CE 资源合包与托管、地图缺字白框和图片偏移；客户端加载回执确认、无资源包网格与仅服务端打包脚本 |
| `Beta_0_7_0` | 本次提交 | 新增 Wallet / Vault Economy 来源、离线收款、调用回执与管理员核对、经济来源隔离及 Folia 版 Vault 打包支持 |
| `Beta_0_7_1` | 本次提交 | 修复物品展示角度和余额排版，优化玉绿卡片、镇库币名、成员操作与长余额展示，78 项测试及实机校准 |
| `Beta_0_8_0` | 本次提交 | 新增 HandbookCore 小镇生活分类、14 篇玩家指南与 `/town guide`，覆盖安家、租地、经营和搬迁；规则随配置刷新，81 项测试 |

**MainMenu（插件）**

| 版本 | 提交 | 改动 |
| --- | --- | --- |
| `Beta_0_1_0` | `eeef7b7` | 首个版本：ESC 菜单按钮 / `/cd` / 时钟右键 + 6×9 布局 + 功能插件 API |
| `Beta_0_2_0` | `6d6cc5c` | 新增 `order` 功能区排序 |
| `Beta_0_2_1` | `b913340` | 修：dialog 按钮点了没反应（并改为不依赖工具链 fork 构建） |
| `Beta_0_3_0` | `67e70a6` | 删除在线时长功能；时钟右键支持空气 |
| `Beta_0_3_1` | `c80a3f6` | 修：ESC 按钮只能点开一次（ClickCallback 默认一次） |
| `Beta_0_4_0` | `e840d65` | 时钟右键空气改由客户端模组检测（服务端不触发该事件） |
| `Beta_0_5_0` | `abc1ded` | ESC→主菜单 一步到位；按钮文字改回默认白色 |
| `Beta_0_6_0` | `9108bca` | 时间显示拆成 RealTime 插件；菜单按秒整页重绘 |
| `Beta_0_7_0` | `5f35ef6` | 新增功能插件管理界面（管理页 / 属性页 / 位置选择 + 状态持久化）；冲突规则调整 |
| `Beta_0_8_0` | `2b788c2` | 固定位置属性页新增红石火把：改回非固定位置功能插件 |
| `Beta_0_8_1` | `25f2bd6` | 修：管理界面物品材质改为代码固定（不再被旧配置带成石头）；属性页不再填充空白格子 |
| `Beta_0_8_2` | `d9dedd3` | 修：改回固定位置时还原原来的位置（只有从未设置过位置的才默认第 1 行第 1 格） |
| `1_0_0` | `f4d7a65` | 出正式版：测试期功能汇总（主菜单框架 + 功能插件 API + 管理员管理界面 + 位置规则）；产物已留档 |
| `1_0_1` | `f4d7a65` | 修：`preferredSlot` 写在功能区或越界时被误判为位置冲突 |
| `Beta_1_0_2` | `da87b2c` | 正式版后重回测试版：修 jar 元数据里的仓库地址（`mcPligin` → `mcPlugin`）；仓库路径统一为相对路径；产物已留档 |
| `Beta_1_1_0` | `bfa2fe6` | 新增：功能项支持按查看者渲染（`icon(viewer)` / `description(viewer)` + 构建器 `iconForViewer` / `linesForViewer`），管理界面同步；产物已留档 |
| `Beta_1_2_0` | `eee95e1` | 新增 `MainMenuApi.open(player)` / `openOrNull(player)`：功能插件可在自己的界面里「返回上一级」；产物已留档 |
| `Beta_1_2_1` | `d2ca44a` | 调整：不再追加「点击使用 / 点击无效」开发者提示（移除两个配置项）；位置冲突提示保留 |
| `Beta_1_3_0` | 未提交 | 新增：适配 Folia 26.3（`folia-supported`、Folia 实体调度器、背包线程归属、并发容器） |
| `Beta_1_4_0` | 未提交 | 新增：管理页第 1 行第 1 格添加功能占位符（红玻璃板显示、主菜单不显示、仅排序值/删除） |
| `Beta_1_5_0` | 未提交 | 新增：管理页第 1 行第 3 格添加自定义插件功能（dialog 输入名称，`custom-features.yml` 配置图标/描述/指令） |
| `Beta_1_5_1` | 未提交 | 修复：自定义功能执行 PlayerTitle 等事件式子命令时只显示帮助页（补触发 `PlayerCommandPreprocessEvent`） |
| `Beta_1_6_0` | 本次提交 | 新增：原生 YAML 方块手机、物品应用图标、应用抽屉及管理页面；配置不覆盖、动态 META、原功能点击桥接 |
| `Beta_1_7_0` | 本次提交 | 新增 Vault 资产页与首页下一页，校验头像纹理并回退默认头；10 页模板与实际 Folia/Vault 联调 |
| `Beta_1_8_0` | 本次提交 | 新增默认隐藏的多侧卡，YAML / API 按查看者提供内容和动作，自动分栏居中、按高度分页，数值刷新复用实体 |

**RealTime（插件）**

| 版本 | 提交 | 改动 |
| --- | --- | --- |
| `Beta_0_1_0` | `9108bca` | 首个版本：主菜单第 1 行第 2 格显示当前时间 |
| `1_0_0` | `0c83099` | 出正式版：主菜单第 1 行第 2 格显示当前时间（时区与格式可配）；产物已留档 |
| `Beta_1_0_1` | `da87b2c` | 正式版后重回测试版：修 jar 元数据里的仓库地址（`mcPligin` → `mcPlugin`）；产物已留档 |
| `Beta_1_0_2` | `d2ca44a` | 调整：去掉「固定功能位 · 点击无效」开发者提示（移除 `time.hint`） |
| `Beta_1_1_0` | 未提交 | 新增：适配 Folia 26.3（`folia-supported` + Folia API 编译） |

**MainMenuRepair（fabric 模组）**

| 版本 | 提交 | 改动 |
| --- | --- | --- |
| `Beta_0_1_0` | `5ee50b8` | 首个版本：去掉 4 个图标按钮 + 第三方内容警告 |
| `Beta_0_1_1` | `e54c905` | 放宽 Fabric Loader 依赖到 `>=0.19.0` |
| `Beta_0_1_2` | `3567f41` | 修：注解保留级别（`missing @Mixin annotation`） |
| `Beta_0_1_3` | `04e0a64` | 注入 + 控件遍历兜底两层方案 |
| `Beta_0_1_4` | `cbc550b` | 修：`@Invoker` 的 mixin 必须写成类 |
| `Beta_0_1_5` | `a0c8374` | 注入全部 `remap = false` |
| `Beta_0_1_6` | `00293c0` | 构造函数排队 + 下一 tick 清理 |
| `Beta_0_1_7` | `6167758` | 清理逻辑内联（mixin 静态方法与 lambda 不合并） |
| `Beta_0_1_8` | `2167a30` | 挂到 `Screen#init` / `Gui#setScreen` |
| `Beta_0_2_0` | `103105d` | 新增 Fabric API 屏幕事件通道（真正生效的那条） |
| `Beta_0_3_0` | `abc1ded` | 拦下中间 dialog，ESC→主菜单 一步到位 |
| `Beta_0_3_1` | `a11139f` | 改用 Fabric API 把按钮直接换成"发 /cd" |
| `1_0_0` | `0c83099` | 出正式版：清理 ESC 暂停菜单（去掉 4 个图标按钮 + 第三方内容警告，ESC→主菜单一步到位）；产物已留档 |
| `Beta_1_0_1` | `da87b2c` | 正式版后重回测试版：修 `fabric.mod.json` 里的仓库地址（`mcPligin` → `mcPlugin`）；产物已留档 |
| `Beta_1_0_2` | `02f969a` | 修：服务端未装 MainMenu 时时钟右键空气仍会判定并发未知命令；改为先检查命令树里的 `/cd clockair`；产物已留档 |
| `Beta_1_1_0` | `c3c9587` | 新增：去掉服务端 dialog 右上角的「自定义屏幕」警告图标；产物已留档 |
| `Beta_1_1_1` | `e287e34` | 修：警告图标移除后标题仍偏左；改为隐藏按钮后把标题控件按屏幕宽度重新居中；产物已留档 |
| `Beta_1_1_2` | `f141dba` | 修：移除暂停菜单图标按钮后中间留出空行；按实际位置算出行距，把下方按钮整体上移；产物已留档 |
| `Beta_1_2_0` | 本次提交 | 新增 HandbookCore 书本整体居中、焦点白框修复、重复灰色页脚隐藏；修正 Redirect 注解桩 |

**YudreamPlaytime（插件，MainMenu 伴侣）**

| 版本 | 提交 | 改动 |
| --- | --- | --- |
| `Beta_0_1_0` | `bfa2fe6` | 首个版本：主菜单里显示查看者自己的在线时长与 AFK 时长（玩家头图标、点击无效）；数据读本机 yudream 桥接插件的本地台账 |
| `1_0_0` | `5e976f5` | 出正式版：测试期功能汇总（按查看者渲染的「我的在线时长」；配置项 stats-file / cache-seconds / order）；产物已留档 |
| `Beta_1_1_0` | 未提交 | 新增：适配 Folia 26.3；修复：台账缓存读取加同步，避免多区域线程并发读写 |

**yudream-minecraft-server（Bukkit 桥接插件）**

| 版本 | 提交 | 改动 |
| --- | --- | --- |
| `1_6_2` | 上游产物 | 官方正式版（反编译基线，未入库；产物见产品线自己的发布渠道） |
| `Beta_1_7_0` | `bfa2fe6` | 基线纳入本仓（Gradle 工程，spigot-api 1.13.2 + Java 8）；新增本地在线时长统计、待发事件落盘与恢复补发、`/yudreammc playtime`；产物已留档 |
| `Beta_1_8_0` | `cfb0618` | 新增：本地台账定期与后端对齐（补齐本地缺的历史，当前区间只认确实在线的一侧）；修：玩家列表接口改走管理端路径（需 `manage` 权限） |
| `Beta_1_8_1` | `7528ca8` | 修：对齐解析漏了平台响应信封（列表在 `data.records` 里），本地时长因此永远补不上后端历史；新增 `/yudreammc syncplaytime` 立即对齐并把结果回到聊天框 |
| `Beta_1_8_2` | `dce5329` | 修：毫秒字段是字符串（平台长整型序列化方式），解析只认数字导致"更新本地 0 条"；键名加别名兼容、路径回退放宽到任意非 2xx 并记住可用路径、无变化时回执带记录样例 |
| `Beta_1_8_3` | `7ad68c8` | 修：后端累计是 `*At(now)` 口径（已含当前未结算区间），本地当已结算值采用导致当前这段被算两遍（本地比后端多一截）；`/yudreammc playtime` 增加后端侧数字对照 |
| `Beta_1_8_4` | `83b9d0f` | 修：后端有记录时改为"以后端为准"（累计 + 当前区间一起采用），修正历史被灌大的本地数字；挂机区间不再跨重启累加 |
| `2_0_0` | `5e976f5` | 出正式版：测试期功能汇总（本地在线时长统计 + 待发落盘补发 + 与后端对齐 + 玩家列表接口链路修复）；产物已留档 |
| `Beta_2_0_1` | `90c01d9` | 修：管理端列表接口 500 时不再每个同步周期刷 WARN（路径探测一次并持久化到 `sync-endpoint.txt`）；同步无变化不再打 INFO；status 回执带当前接口 |
| `Beta_2_1_0` | 未提交 | 新增：反射式 Folia 调度兼容层（`folia-supported`），全局 / 异步 / 实体调度器分流；保持 Java 8 + spigot-api 1.13.2 |

**Wallet（插件）**

| 版本 | 提交 | 改动 |
| --- | --- | --- |
| `Beta_0_1_0` | `eee95e1` | 首个版本：主菜单钱包（绿宝石 / 余额 / 流水 / 货币类型 / 玩家货币）+ `WalletApi` 钱包 API；数据与操作全部走官网 |
| `Beta_0_2_0` | `25dbacd` | 新增：管理员在货币类型页点击羊毛切换本服启停（写 `assets.yml`）；修：返回主菜单（下一 tick 重开，旧版 MainMenu 退回 `/cd`） |
| `Beta_0_2_1` | `6c54d9f` | 修：停用币种后流水页仍显示该币种（流水按本服启用币种过滤）；新增按币种过滤的 `transactions` 重载 |
| `Beta_0_3_0` | `31fcf90` | 新增管理员指令 `/walletin` / `/walletout`（在线玩家、按资产精度校验、写官网流水）；`wallet.adjust` / `wallet.admin` 权限声明 |
| `Beta_0_4_0` | 未提交 | 新增：适配 Folia 26.3（界面回调走玩家实体调度器，异步回复回到执行者区域线程） |
| `Beta_0_5_0` | `b645a8e` | 新增：本地测试模式 `test-mode`，余额与流水写入本机账本，默认关闭 |
| `Beta_0_6_0` | 本次提交 | 新增本地持久多币种及 Vault 主币种经济服务、旧 YuDream 官网一次性迁入；修复钱包菜单桥接 |
| `Beta_0_6_1` | 本次提交 | 修复旧配置缺少 currencies 时默认 coin 未加载、钱包启动失败；新增配置回归测试与官网后端升级说明 |

**ConcertoMenu（插件）**

| 版本 | 提交 | 改动 |
| --- | --- | --- |
| `Beta_0_1_0` | `0dcb330` | 首个版本：主菜单唱片机 → 发 `concerto:menu` 的 `open` 指令打开客户端 Concerto 主界面 |
| `Beta_0_2_0` | 未提交 | 新增：适配 Folia 26.3（`folia-supported` + Folia API 编译） |

**JoinAnnounce（插件）**

| 版本 | 提交 | 改动 |
| --- | --- | --- |
| `Beta_0_1_0` | 未提交 | 首个版本：进服公告（延迟 / 多行 / 占位符）、首次进服文案、可选标题与音效、进出服全服广播、主菜单「服务器公告」入口、`/joinannounce reload\|preview` |
| `Beta_0_2_0` | 未提交 | 新增：进服公告改为 dialog 弹窗（运行时 `Dialog.create` + `player.showDialog`），`announce.display` 可切 dialog / chat / both；主菜单入口与 preview 同步 |
| `Beta_0_2_1` | 未提交 | 调整：进出服广播默认关闭，插件默认不往聊天栏发任何消息；`preview` 严格按 `announce.display` |
| `Beta_0_2_2` | 未提交 | 调整：`config-simple.yml` 恢复为 SWUST 社团公告文案（无徽章） |
| `Beta_0_3_0` | 未提交 | 新增：`announce.once-per-day` 每位玩家每天首次进服才弹（跨天在线不打断、服务器重启重置，时区可配） |
| `Beta_0_3_1` | 未提交 | 修：`once-per-day` 默认改为开（旧配置缺键也生效，解决退出重进仍弹）；启动日志打印公告模式；补 `DailyNoticeGate` 单元测试 |
| `Beta_0_4_0` | 未提交 | 新增：首个进服弹窗底部加「查看完整更新日志」按钮（外部链接），与「开始冒险」并排且主按钮更宽 |
| `Beta_0_4_1` | 未提交 | 修：两个按钮改用 `confirmation` 类型渲染，固定在弹窗底部页脚（不再随正文滚动） |

> 此前的社团徽章 / 资源包尝试（当时编号 `Beta_0_3_0`～`Beta_0_5_0`）已整体回退，产物已从 `./release/JoinAnnounce/` 移除；
> 该编号段已重新用于后续功能（当前 `Beta_0_3_0` 是每日一次公告）。

**ChatPrivacy（插件）**

| 版本 | 提交 | 改动 |
| --- | --- | --- |
| `Beta_0_1_0` | `8ad3f05` | 首个版本：监听 `AsyncChatEvent`，把公开聊天改写为无签名系统消息（客户端无法举报）；Folia 实体调度器发送；`/chatprivacy status\|reload` |

## 常见问题

**新增的插件目录没被识别**：目录里必须有 `build.gradle`，否则 `settings.gradle` 会跳过并打印一行提示。

**依赖下载慢或超时**：把根 `build.gradle` 里 `subprojects` 中的 `mavenCentral()` 换成国内镜像（`folia-api` 只有 PaperMC 官方仓库有，镜像里没有，所以要保留）：

```groovy
repositories {
    maven { url = 'https://maven.aliyun.com/repository/public' }
    maven {
        name = 'papermc'
        url = 'https://repo.papermc.io/repository/maven-public/'
    }
}
```

Gradle 插件（如 run-paper）走的是插件门户，慢的话在 `settings.gradle` 顶部加：

```groovy
pluginManagement {
    repositories {
        maven { url = 'https://maven.aliyun.com/repository/gradle-plugin' }
        gradlePluginPortal()
    }
}
```

**下载 Gradle 失败**：wrapper 默认走华为云镜像（见 `./gradle/wrapper/gradle-wrapper.properties`）。想换回官方源，把 `distributionUrl` 改成 `https://services.gradle.org/distributions/gradle-9.8.1-bin.zip` 即可；CI 里已经在构建前自动换回官方源。

**`runServer` 启动后立刻退出**：看 `./run/eula.txt` 是否还是 `eula=false`，改成 `true` 再启动。

**改了 `paper-plugin.yml` 或 `config.yml` 不生效**：`paper-plugin.yml` 需要重新 `./gradlew :插件名:build` 再重启服务端；`config.yml` 在 `./run/plugins/<插件名>/` 下，改完用插件自带的 reload 命令热重载。注意默认配置只在文件不存在时才会释放，删掉该文件才会重新生成。

**提示 Java 版本不对**：确认 `gradle.properties` 的 `targetJavaVersion` 不低于服务端要求（Folia 26.3 为 25），并让本机装有对应 JDK。

## 参考链接

- [Paper / Folia 插件开发文档](https://docs.papermc.io/paper/dev/)
- [Paper 工程配置说明（Folia API 兼容）](https://docs.papermc.io/paper/dev/project-setup/)
- [Folia 服务端下载 / 构建列表](https://fill.papermc.io/v3/projects/folia/versions/26.3/builds)
- [Folia API Maven 仓库](https://repo.papermc.io/repository/maven-public/dev/folia/folia-api/)
- [MiniMessage 文本格式](https://docs.advntr.dev/minimessage/format.html)
- [run-paper（本地测试服插件，含 Folia 支持）](https://github.com/jpenilla/run-task)

## License

[MIT](LICENSE)
