# Minecraft 26.3 插件集合

[![Build](https://github.com/zhou-BZDDX/mcPlugin/actions/workflows/build.yml/badge.svg)](https://github.com/zhou-BZDDX/mcPlugin/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Minecraft](https://img.shields.io/badge/Minecraft-26.3-green.svg)
![Java](https://img.shields.io/badge/Java-25-orange.svg)

Minecraft **26.3**（Paper）多插件仓库。所有插件放在 `./Minecraft-26_3-plugin/<插件名>/`，由仓库根目录的一个 Gradle 工程统一管理：共享构建配置和服务端版本，每个插件又能单独打包、单独启动测试服。

目前仓库里有 4 个插件：**[MainMenu](./Minecraft-26_3-plugin/MainMenu)**（主菜单框架，其它插件以它为依赖注册功能）、
**[RealTime](./Minecraft-26_3-plugin/RealTime)**（在主菜单里显示当前时间）、
**[YudreamPlaytime](./Minecraft-26_3-plugin/YudreamPlaytime)**（主菜单里显示查看者自己的在线时长，MainMenu 的伴侣插件）、
**[yudream-minecraft-server](./Minecraft-26_3-plugin/yudream-minecraft-server)**（把玩家动态上报给 YuDream Admin 的 Bukkit 桥接插件，
自带本地时长统计与断线补发）。新增插件用 `./gradlew newPlugin -PpluginName=你的插件名`。

## 规范索引

仓库里的约定集中在以下几处，动手前先看一眼：

| 规范 | 位置 | 内容 |
| --- | --- | --- |
| AI 协作入口 | [`./AGENTS.md`](./AGENTS.md) | 硬规则、开工必读、沟通字典、现状快照 |
| 开发规范总纲 | [`./docs/dev/conventions.md`](./docs/dev/conventions.md) | 技术栈、仓库结构与**路径约定**、编码约定、模组约定、Git、文档纪律 |
| 版本与产物规范 | [`./docs/dev/版本与产物规范.md`](./docs/dev/版本与产物规范.md) | 涨号规则、产物命名、留档、ChangeLog、发布流程、历史产物重建 |
| 功能插件接入 | [`./docs/dev/功能插件接入规范.md`](./docs/dev/功能插件接入规范.md) | 依赖声明、注册 API、位置 / 排序 / 冲突规则 |
| 经验与坑 | [`./docs/exp/`](./docs/exp/) | Paper 插件 / Fabric 模组 / 构建环境三份坑库，遇到问题先查 |
| 计划（不提交） | `./docs/plan/` | 阶段计划与任务拆解；长期结论沉淀回 `docs/dev` 或 `docs/exp` |
| 新增插件 | [新增插件](#新增插件)、[`./templates/plugin-template/`](./templates/plugin-template) | 脚手架、目录名 / 包名 / 类名一致性 |
| CI | [`./.github/workflows/`](./.github/workflows) | `build.yml` 构建、`release.yml` 发布 |

> 一条规则只定义一次：规范写在 `./docs/dev/`，经验写在 `./docs/exp/`，README 只描述「这是什么、怎么用」。

## 环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 25 及以上 | Paper 26.3 要求 Java 25；本机装 JDK 26 也能用，Gradle 用工具链把字节码编译到 25 |
| Gradle | 9.8.1 | 仓库自带 wrapper（`gradlew`），不需要手动安装 Gradle |
| Paper API | `26.3.build.200-stable` | 在 `gradle.properties` 中配置，对所有插件生效 |

> 本机没有 JDK 25 时，`settings.gradle` 里的 foojay 插件会自动下载对应版本的工具链，无需手动安装。

## 目录结构

树下每一项都相对仓库根目录（也就是 `./xxx/xxx` 那样的相对路径）：

```
.
├── AGENTS.md                       # AI 协作入口：硬规则 / 必读清单 / 沟通字典 / 现状快照
├── build.gradle                    # 所有插件共享的构建配置（依赖、工具链、runServer）
├── gradle.properties               # 统一版本：Minecraft / Paper API / Java（升级版本改这里）
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
│   └── yudream-minecraft-server/   # YuDream Admin 桥接插件：上报 + 本地时长统计 + 断线补发（Java 8 / Bukkit 1.13.2 目标）
├── fabric-26_3-mod/                # Fabric 客户端模组（独立 Gradle 工程，不参与上面的插件构建）
│   └── MainMenuRepair/             # 清理 ESC 暂停菜单的界面修复模组
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

# 3. 启动测试服（Paper 26.3 + 自动加载刚构建的插件），服务端文件在仓库根目录的 ./run/
./gradlew :MyPlugin:runServer
```

首次 `runServer` 会自动下载 Paper 服务端并在 `./run/` 生成世界；如果服务端提示 `You need to agree to the EULA`，把 `./run/eula.txt` 里的 `eula=false` 改成 `eula=true`（表示你同意 [Mojang EULA](https://aka.ms/MinecraftEULA)）后重新执行即可。

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
| `resources/paper-plugin.yml` | Paper 插件描述文件 + 权限声明 |
| `resources/config.yml` | 默认配置，MiniMessage 文本格式 |

> 同一台服务器上装多个插件时，注意命令名和权限节点不要重复。脚手架会把权限节点一起改名（如 `shop.hello`），但命令字面量还是模板里的 `/hello`、`/mcplugin`，记得改成自己的。

## 插件：MainMenu（主菜单）

6×9 的功能主菜单，玩家可以通过三种方式打开：**ESC 菜单里的「主菜单」按钮**、**`/cd` 命令**、**手持时钟右键**（对着空气或方块都行）。

### 界面布局

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

主菜单第 6 行第 5 格的命令方块是管理入口，**只有管理员**（权限 `mainmenu.admin`，默认 OP）能看到和点击；其他玩家那一格是普通玻璃板，点了也没反应。

管理页布局（左栏 = 非固定位置功能插件，右栏 = 固定位置功能插件，每栏 16 格/页）：

```
第1行   黑色玻璃板 ×9
第2~5行 [左栏×4][黑][右栏×4]
第6行   [左上一页][黑][黑][左下一页][返回][右上一页][黑][黑][右下一页]
```

| 操作 | 位置 | 说明 |
| --- | --- | --- |
| 进入管理页 | 主菜单第 6 行第 5 格 | 仅管理员 |
| 进入属性页 | 点左栏 / 右栏里的功能 | 左栏 → 非固定位置属性页；右栏 → 固定位置属性页 |
| 返回上一级 | 第 6 行第 5 格（屏障） | 属性页、位置选择页都在这格返回 |
| 显示 / 隐藏 | 属性页第 3 行第 3 格（羊毛） | 黄绿色 = 显示中，红色 = 已隐藏 |
| 调整排序值 | 非固定位置属性页第 3 行第 5 格（空地图） | 左键 +1、右键 −1；按住 Shift 为 ±4 |
| 转为固定位置 | 非固定位置属性页第 3 行第 7 格（拉杆） | 还原原来的位置（管理员设过的、或插件自己声明的 `preferredSlot`）；从来没设置过位置的才默认第 1 行第 1 格，并切到固定位置属性页 |
| 更改位置 | 固定位置属性页第 3 行第 5 格（末影之眼） | 进入位置选择界面 |
| 转为非固定位置 | 固定位置属性页第 3 行第 7 格（红石火把） | 改回非固定位置功能插件，按排序值排进功能区 |
| 设置位置 | 位置选择界面里点格子 | 功能区与翻页位置不能选；管理入口那格是屏障，表示取消 |

两条相关规则：

- **隐藏的功能不再占位** —— 原来与它冲突的插件可以正常显示，不会被挤到功能区；
- **固定位置功能插件位置冲突时**，归到功能区并显示在**最前面**，但它**依然算固定位置功能插件**（在管理页右栏里、位置也保留）。

> 管理界面里的物品材质写死在代码里（入口=命令方块、分隔=黑色染色玻璃板、显示/隐藏=黄绿色/红色羊毛、排序值=空地图、更改位置=末影之眼、转固定位置=拉杆、转非固定位置=红石火把、位置选择界面的功能名=命名牌、返回/取消=屏障），不走配置，避免旧版 `config.yml` 缺键时被兜底成石头。属性页只有三个操作位和返回键，其余格子是空的。

所有改动都会写进 `plugins/MainMenu/features.yml`，重启服务端后依然有效。

### 命令与权限

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/cd` | `mainmenu.use`（默认所有人） | 打开主菜单 |
| `/cd reload` | `mainmenu.reload`（OP） | 重载配置 |
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

Paper 26.3 没有“直接往暂停界面加按钮”的 API。官方途径是 dialog 系统：

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
# 产物：./fabric-26_3-mod/MainMenuRepair/build/libs/MainMenuRepair-1.0.0.jar
```

把 jar 放进客户端该实例的 `mods/` 目录，重启客户端即可。模组是纯客户端的，**不需要 Fabric API，也不需要服务端做任何事**。

> 26.3 客户端不再混淆，Fabric 也没有 26.3 的 yarn/intermediary，所以编译不需要 mappings、构建不需要 remap。
> 本工程因此不依赖 fabric-loom（`maven.fabricmc.net` 在部分网络下被限速到 KB 级，loom 会卡在下载上），
> 只下载官方客户端 jar + 用 `src/mixin-stubs/java` 里的 Mixin 注解桩编译，细节见模组自己的 README。

## 开发约定

编码约定（命令写在代码里、MiniMessage、`paper-api` 用 `compileOnly`、`api-version` 跟随游戏版本、Paper API 优先于 NMS、
插件间依赖只用 `compileOnly`、配置与持久化、测试插件命名）全部收敛在
[`./docs/dev/conventions.md`](./docs/dev/conventions.md) §3、§4、§5；踩过的坑与规避方式见 [`./docs/exp/`](./docs/exp/)。

## 测试服说明

- 所有插件共用仓库根目录的 `./run/`：只下一份服务端、只生成一份世界，插件各自的配置在 `./run/plugins/<插件名>/`。
- **不要同时启动两个插件的 `runServer`**，会抢同一个端口。
- 想让某个插件用独立目录：在它自己的 `build.gradle` 里写 `tasks.named('runServer') { runDirectory(file('run')) }`，就会变成该插件目录下的 `./run/`。
- 想固定服务端构建号：在根 `build.gradle` 的 `runServer` 配置里打开 `build(200)`，构建号见 [papermc.io/downloads/paper](https://papermc.io/downloads/paper)。
- 想额外装别的插件到测试服：把 jar 放进 `./run/plugins/` 即可。

## 升级 Minecraft 版本

改 `gradle.properties`，对所有插件同时生效：

```properties
minecraftVersion=26.4
paperApiVersion=26.4.build.1-stable
targetJavaVersion=25
```

`paperApiVersion` 的可选构建号见 [PaperMC Maven 仓库](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/)。也可以用动态版本 `26.4.build.+`（不推荐：每次构建拉到的版本可能不同，产物不可复现）。

如果只有个别插件需要停留在旧版本，可以在那个插件的 `build.gradle` 里单独覆盖：

```groovy
dependencies {
    compileOnly 'io.papermc.paper:paper-api:26.2.build.150-stable'
}
```

## 发布到 GitHub Releases

改好版本号、补完 ChangeLog、确认 `./release/<项目名>/` 里有对应产物之后，打个 `v*` 标签推送即可 ——
`.github/workflows/release.yml` 会构建全部工程并把 jar 挂到该 Release 上。
完整的前置检查与命令见 [`./docs/dev/版本与产物规范.md`](./docs/dev/版本与产物规范.md) §5。

## 版本与产物规范

产物命名格式、涨号规则（含「正式版之后再改动先回到测试版」）、留档、ChangeLog 规范、发布流程、
以及用 `./scripts/rebuild-history.sh` 从 git 历史重建产物，全部收敛在
[`./docs/dev/版本与产物规范.md`](./docs/dev/版本与产物规范.md) —— 一条规则只在那里定义一次。

下面两张表是**事实**：当前版本，以及每个版本对应的提交与改动。

## 当前版本

| 项目 | 正式版 | 测试版（进行中） |
| --- | --- | --- |
| MainMenu | `1_0_1`（2026.10.18） | `Beta_1_2_1`（按查看者渲染 + `MainMenuApi.open`；去掉开发者提示） |
| RealTime | `1_0_0`（2026.10.18） | `Beta_1_0_2`（去掉固定位提示） |
| MainMenuRepair（fabric） | `1_0_0`（2026.10.18） | `Beta_1_0_1`（修 jar 元数据里的仓库地址） |
| YudreamPlaytime | `1_0_0`（2026.10.19） | —（已转正） |
| yudream-minecraft-server | `2_0_0`（2026.10.19） | `Beta_2_0_1`（修：列表接口 500 时不再刷 WARN，路径探测一次并持久化） |
| Wallet | —（还在测试版阶段） | `Beta_0_2_1`（管理员可切换本服货币启停；流水只显示启用币种） |
| ConcertoMenu | —（还在测试版阶段） | `Beta_0_1_0`（首个版本：主菜单唱片机入口） |

> 涨号规则（正式版之后再改动先回到测试版 `Beta_<第 1 位>_x_x`，转正时去掉 `Beta`、第 3 位归 0、第 2 位至少 +1，第 1 位不变）见
> [`./docs/dev/版本与产物规范.md`](./docs/dev/版本与产物规范.md) §2；
> 逐版本改动见各项目目录下的 ChangeLog：`…-ChangeLog-Beta.md` 记所有测试版，`…-ChangeLog.md` 记正式版。

## 版本历史

`./release/<项目名>/` 里保留了每一版的产物；下面是对应的提交与改动（早期被同名覆盖的版本已用 `./scripts/rebuild-history.sh` 从 git 历史重建）。

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

**RealTime（插件）**

| 版本 | 提交 | 改动 |
| --- | --- | --- |
| `Beta_0_1_0` | `9108bca` | 首个版本：主菜单第 1 行第 2 格显示当前时间 |
| `1_0_0` | `0c83099` | 出正式版：主菜单第 1 行第 2 格显示当前时间（时区与格式可配）；产物已留档 |
| `Beta_1_0_1` | `da87b2c` | 正式版后重回测试版：修 jar 元数据里的仓库地址（`mcPligin` → `mcPlugin`）；产物已留档 |
| `Beta_1_0_2` | `d2ca44a` | 调整：去掉「固定功能位 · 点击无效」开发者提示（移除 `time.hint`） |

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

**YudreamPlaytime（插件，MainMenu 伴侣）**

| 版本 | 提交 | 改动 |
| --- | --- | --- |
| `Beta_0_1_0` | `bfa2fe6` | 首个版本：主菜单里显示查看者自己的在线时长与 AFK 时长（玩家头图标、点击无效）；数据读本机 yudream 桥接插件的本地台账 |
| `1_0_0` | `5e976f5` | 出正式版：测试期功能汇总（按查看者渲染的「我的在线时长」；配置项 stats-file / cache-seconds / order）；产物已留档 |

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

**Wallet（插件）**

| 版本 | 提交 | 改动 |
| --- | --- | --- |
| `Beta_0_1_0` | `eee95e1` | 首个版本：主菜单钱包（绿宝石 / 余额 / 流水 / 货币类型 / 玩家货币）+ `WalletApi` 钱包 API；数据与操作全部走官网 |
| `Beta_0_2_0` | `25dbacd` | 新增：管理员在货币类型页点击羊毛切换本服启停（写 `assets.yml`）；修：返回主菜单（下一 tick 重开，旧版 MainMenu 退回 `/cd`） |
| `Beta_0_2_1` | `6c54d9f` | 修：停用币种后流水页仍显示该币种（流水按本服启用币种过滤）；新增按币种过滤的 `transactions` 重载 |

## 常见问题

**新增的插件目录没被识别**：目录里必须有 `build.gradle`，否则 `settings.gradle` 会跳过并打印一行提示。

**依赖下载慢或超时**：把根 `build.gradle` 里 `subprojects` 中的 `mavenCentral()` 换成国内镜像（`paper-api` 只有 PaperMC 官方仓库有，镜像里没有，所以要保留）：

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

**提示 Java 版本不对**：确认 `gradle.properties` 的 `targetJavaVersion` 不低于服务端要求（26.3 为 25），并让本机装有对应 JDK。

## 参考链接

- [Paper 插件开发文档](https://docs.papermc.io/paper/dev/)
- [Paper 工程配置说明](https://docs.papermc.io/paper/dev/project-setup/)
- [Paper 服务端下载 / 构建列表](https://papermc.io/downloads/paper)
- [MiniMessage 文本格式](https://docs.advntr.dev/minimessage/format.html)
- [run-paper（本地测试服插件）](https://github.com/jpenilla/run-task)

## License

[MIT](LICENSE)
