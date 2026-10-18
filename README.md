# Minecraft 26.3 插件集合

[![Build](https://github.com/zhou-BZDDX/mcPligin/actions/workflows/build.yml/badge.svg)](https://github.com/zhou-BZDDX/mcPligin/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Minecraft](https://img.shields.io/badge/Minecraft-26.3-green.svg)
![Java](https://img.shields.io/badge/Java-25-orange.svg)

Minecraft **26.3**（Paper）多插件仓库。所有插件放在 `Minecraft-26_3-plugin/<插件名>/`，由仓库根目录的一个 Gradle 工程统一管理：共享构建配置和服务端版本，每个插件又能单独打包、单独启动测试服。

目前仓库里有 2 个插件：**[MainMenu](Minecraft-26_3-plugin/MainMenu)**（主菜单框架，其它插件以它为依赖注册功能）和 **[RealTime](Minecraft-26_3-plugin/RealTime)**（在主菜单里显示当前时间）。新增插件用 `./gradlew newPlugin -PpluginName=你的插件名`。

## 环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 25 及以上 | Paper 26.3 要求 Java 25；本机装 JDK 26 也能用，Gradle 用工具链把字节码编译到 25 |
| Gradle | 9.8.1 | 仓库自带 wrapper（`gradlew`），不需要手动安装 Gradle |
| Paper API | `26.3.build.200-stable` | 在 `gradle.properties` 中配置，对所有插件生效 |

> 本机没有 JDK 25 时，`settings.gradle` 里的 foojay 插件会自动下载对应版本的工具链，无需手动安装。

## 目录结构

```
.
├── build.gradle                    # 所有插件共享的构建配置（依赖、工具链、runServer）
├── gradle.properties               # 统一版本：Minecraft / Paper API / Java（升级版本改这里）
├── settings.gradle                 # 自动发现 Minecraft-26_3-plugin/ 下的插件目录
├── templates/
│   └── plugin-template/            # newPlugin 用的模板（一份可直接编译运行的示例插件）
│       ├── build.gradle            # 只写版本号与额外依赖，公共配置在根 build.gradle
│       └── src/main/{java,resources}
├── Minecraft-26_3-plugin/          # 插件都放这里，一个目录一个插件
│   └── MainMenu/                   # 主菜单插件（功能插件接入规范见其 docs/）
├── fabric-26_3-mod/                # Fabric 客户端模组（独立 Gradle 工程，不参与上面的插件构建）
│   └── MainMenuRepair/             # 清理 ESC 暂停菜单的界面修复模组
├── run/                            # 共用的测试服目录（服务端、世界、插件配置都在这里，已 gitignore）
└── .github/                        # CI、Release、Issue/PR 模板、Dependabot
```

## 快速开始

```bash
# 1. 生成第一个插件（自动建目录、换包名和类名）
./gradlew newPlugin -PpluginName=MyPlugin

# 2. 编译打包，产物在 Minecraft-26_3-plugin/MyPlugin/build/libs/
./gradlew :MyPlugin:build

# 3. 启动测试服（Paper 26.3 + 自动加载刚构建的插件），服务端文件在仓库根目录的 run/
./gradlew :MyPlugin:runServer
```

首次 `runServer` 会自动下载 Paper 服务端并在 `run/` 生成世界；如果服务端提示 `You need to agree to the EULA`，把 `run/eula.txt` 里的 `eula=false` 改成 `eula=true`（表示你同意 [Mojang EULA](https://aka.ms/MinecraftEULA)）后重新执行即可。

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

脚手架做的事：把 `templates/plugin-template/` 复制成 `Minecraft-26_3-plugin/Shop/`，并把模板里的 `ExamplePlugin` / `exampleplugin` / `com.example.exampleplugin` 全部替换成新名字，同时搬好 `src/main/java` 下的包目录。之后把 `paper-plugin.yml` 里的 `authors`、`website` 填上就能开工。

也可以直接手动复制 `templates/plugin-template/` 到 `Minecraft-26_3-plugin/<插件名>/` 再逐个改名，注意目录名、`paper-plugin.yml` 的 `name`/`main`、Java 包名要一致。

## 模板里有什么

`templates/plugin-template/` 是一份能直接编译运行的完整示例，建议先读一遍再改：

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
第6行   玻璃板 ×9
```

- **时间**（第 1 行第 2 格）：悬停显示当前时间（东 8 区），由 [RealTime](#插件realtime当前时间) 插件提供，展示型（点击无效）。
- **玻璃板**：非固定功能位，功能插件可以用自定义位置占用。
- **功能区**：第 3~5 行、第 2~8 列共 21 格，按注册顺序填充，每页 21 个，可翻页。
- **箭头**（第 4 行第 1 / 第 9 格）：功能区翻页，固定功能位。

菜单打开期间会按 `menu.refresh-seconds`（默认 1 秒）整页重绘，所以功能插件的动态文本（例如时间）会自动刷新。

### 命令与权限

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/cd` | `mainmenu.use`（默认所有人） | 打开主菜单 |
| `/cd reload` | `mainmenu.reload`（OP） | 重载配置 |
| `/cd debug` | `mainmenu.reload`（OP） | 输出布局、功能位置、ESC 按钮状态、配置自检 |

### 其它插件怎么挂功能

功能插件以 MainMenu 为依赖，调用 `com.example.mainmenu.api` 注册「物品 / 悬停文本 / 点击是否有效 / 点击做什么 / 自定义位置」。
完整规范见 [功能插件接入规范](Minecraft-26_3-plugin/MainMenu/docs/功能插件接入规范.md)，最简形式：

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

### 测试用功能插件

`Minecraft-26_3-plugin/TestPlugin01` ~ `TestPlugin25` 是为验证主菜单而生成的测试插件，每个依赖 MainMenu 并注册 1 个功能（TestPlugin01 注册 2 个），共 26 个功能，用来压测排序、自定义位置、冲突改排和分页：

| 插件 | 场景 |
| --- | --- |
| TestPlugin01 | 自定义位置占第 1 行第 1 格（若被抢先则冲突改排）+ 额外注册第 2 个功能 |
| TestPlugin02 | 自定义位置占第 1 行第 9 格 |
| TestPlugin03 | 想占用上一页（固定功能位 `27`）→ 应改排到功能区并提示 |
| TestPlugin04 | 也想要第 1 行第 1 格（与 TestPlugin01 抢同一格，先注册者得） |
| TestPlugin05 | 想占用下一页（固定功能位 `35`）→ 应改排到功能区并提示 |
| TestPlugin06 | `preferredSlot` 写在功能区格子上 → 视为未指定位置，不算冲突 |
| TestPlugin07 ~ 25 | 无自定义位置，按 `order` 排进功能区（19 个） |

一共 26 个功能：2 个自定义位置 + 24 个在功能区 → 2 页（21 + 3）。

安装到测试服：把 `Minecraft-26_3-plugin/*/build/libs/*.jar` 全部拷进服务端 `plugins/` 后重启即可。
不需要它们时一条命令删除：`rm -rf Minecraft-26_3-plugin/TestPlugin*`（Release 工作流已经排除 `TestPlugin*`，不会发布测试插件）。

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

`Minecraft-26_3-plugin/RealTime` 是 MainMenu 的一个功能插件，负责**第 1 行第 2 格的"当前时间"显示** ——
这部分原先内置在 MainMenu 里，现在拆成独立插件（MainMenu 只提供菜单框架和功能注册 API）。

| 配置项 | 说明 |
| --- | --- |
| `time.material` / `time.name` | 显示用的物品与名称 |
| `time.line` | 悬停文本，`{time}` 替换成格式化时间 |
| `time.format` / `time.zone-offset-hours` | 时间格式与时区偏移（默认 `yyyy-MM-dd HH:mm:ss` / 东 8 区） |
| `time.hint` | 固定位置提示，留空不显示 |

它直接实现 `MenuFeature` 接口（而不是用 builder），因为 `description()` 每次渲染都会被调用 ——
配合 MainMenu 的周期性重绘，显示的时间会自动跟着走。这也是给功能插件的参考写法：
**需要动态文本就实现接口，让 `icon()` / `description()` 每次现算。**

## 客户端模组：MainMenuRepair

`fabric-26_3-mod/MainMenuRepair/` 是一个 **Fabric 客户端模组**，直接改客户端界面，把 ESC 暂停菜单里的这两样东西真正删掉：

| 目标 | 做法 |
| --- | --- |
| 「主菜单」按钮上那句 *注：自定义选项由第三方服务器或内容提供。请小心操作！* | Mixin 掉 `PauseScreen.addCustomDialogButtons` 里对 `Button$Builder.tooltip(...)` 的调用 |
| 右下角 4 个原版图标按钮（报告 Bug / 给予反馈 / 好友 / 举报玩家） | 把 `PauseScreen.createPauseMenu()` 里的图标行 `LinearLayout.horizontal()` 换成不接收子控件的实现，4 个按钮根本不会被创建 |

**为什么用模组而不是资源包**：那句提示是客户端 `PauseScreen` 里**无条件**附加的，4 个图标也是**无条件**创建的原版按钮，服务端既没有 API 也没有配置项能关掉；资源包只能改文案和贴图（图标会变成看不见但仍可点的按钮），模组才能真正删除。

### 构建与安装

```bash
./gradlew -p fabric-26_3-mod/MainMenuRepair build
# 产物：fabric-26_3-mod/MainMenuRepair/build/libs/MainMenuRepair-1.0.0.jar
```

把 jar 放进客户端该实例的 `mods/` 目录，重启客户端即可。模组是纯客户端的，**不需要 Fabric API，也不需要服务端做任何事**。

> 26.3 客户端不再混淆，Fabric 也没有 26.3 的 yarn/intermediary，所以编译不需要 mappings、构建不需要 remap。
> 本工程因此不依赖 fabric-loom（`maven.fabricmc.net` 在部分网络下被限速到 KB 级，loom 会卡在下载上），
> 只下载官方客户端 jar + 用 `src/mixin-stubs/java` 里的 Mixin 注解桩编译，细节见模组自己的 README。

## 26.3 开发要点

1. **命令写在代码里**。Paper 插件不使用 `plugin.yml` 的 `commands:` 字段，而是在 `onEnable` 里监听 `LifecycleEvents.COMMANDS` 注册 Brigadier 命令。
2. **文本用 MiniMessage**。Adventure 的 `MiniMessage` 随 Paper 内置，配置里的 `<yellow>` 会被渲染成颜色；旧版 `&a` 写法需要自己用 `LegacyComponentSerializer` 转换。
3. **`paper-api` 必须是 `compileOnly`**。运行期由服务端提供这些类，用 `implementation` 打包进去会造成类冲突。这条已在根 `build.gradle` 配好。
4. **`api-version` 填 `26.3`**，与 `gradle.properties` 的 `minecraftVersion` 保持一致。
5. **优先用 Paper API 而非 NMS**。需要读写方块、实体、数据包时先查 [Paper 文档](https://docs.papermc.io/paper/dev/)，只有官方 API 覆盖不到时才考虑 paperweight-userdev。

## 测试服说明

- 所有插件共用仓库根目录的 `run/`：只下一份服务端、只生成一份世界，插件各自的配置在 `run/plugins/<插件名>/`。
- **不要同时启动两个插件的 `runServer`**，会抢同一个端口。
- 想让某个插件用独立目录：在它自己的 `build.gradle` 里写 `tasks.named('runServer') { runDirectory(file('run')) }`，就会变成该插件目录下的 `run/`。
- 想固定服务端构建号：在根 `build.gradle` 的 `runServer` 配置里打开 `build(200)`，构建号见 [papermc.io/downloads/paper](https://papermc.io/downloads/paper)。
- 想额外装别的插件到测试服：把 jar 放进 `run/plugins/` 即可。

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

```bash
# 1. 修改要发布的插件里的 version，例如 Minecraft-26_3-plugin/Shop/build.gradle
# 2. 提交并打 tag
git commit -am "chore: release shop 0.2.0"
git tag v0.2.0
git push origin main --tags
```

推送 `v*` 标签会触发 `.github/workflows/release.yml`，构建所有插件并把 jar 全部附加到该 Release 上。

## 版本与产物命名规范

所有构建产物按固定格式命名，并**留档在仓库根的 `release/`**（每次小改版的产物都保留，不覆盖）：

| 类型 | 命名格式 | 示例 |
| --- | --- | --- |
| Paper 插件 | `<项目名>-<游戏版本>-plugin-<项目版本>` | `MainMenu-mc26_3-plugin-Beta_0_6_0.jar` |
| Fabric 模组 | `<项目名>-<游戏版本>-fabric-<项目版本>` | `MainMenuRepair-mc26_3-fabric-Beta_0_3_1.jar` |
| NeoForge 模组（后续） | `<项目名>-<游戏版本>-neoforge-<项目版本>` | — |
| Forge 模组（后续） | `<项目名>-<游戏版本>-forge-<项目版本>` | — |

- **游戏版本**：把点换成下划线，26.3 → `mc26_3`。构建脚本按 `gradle.properties` 的 `minecraftVersion` 自动生成，不用手写。
- **项目版本**：测试期为 `Beta_x_x_x`；出正式版后去掉 `Beta`（例如 `1_0_0`）。

### 版本号怎么涨

| 改动类型 | 规则 | 示例 |
| --- | --- | --- |
| 修 bug | 第 3 位 +1 | `Beta_0_3_1` → `Beta_0_3_2` |
| 新增功能 | 第 2 位 +1，第 3 位归 0 | `Beta_0_3_2` → `Beta_0_4_0` |
| 出正式版 | 第 1 位 +1，后两位归 0，去掉 `Beta` | `Beta_0_4_0` → `1_0_0` |

版本号写在各项目的 `build.gradle`（插件）/ `gradle.properties`（模组）里。

### 产物留档

`./gradlew build` 结束后会自动把 jar 复制到 `release/`（任务 `archiveArtifact`，挂在 `build` 的 `finalizedBy` 上）。
文件名自带版本，所以每次构建留下的是独立文件、不会互相覆盖。测试插件（`TestPlugin*`）是联调夹具，不参与留档，也不随 Release 发布。

`release/` 已提交进仓库，所以历史产物能长期保留。要手动补一次留档：`./gradlew archiveArtifact`。

### ChangeLog 规范

每个项目在**版本号变化时**都要追加一段 ChangeLog，文件放在各自项目目录下，文件名固定：

| 文件 | 记录范围 |
| --- | --- |
| `<项目名>-<游戏版本>-<类型>-ChangeLog-Beta.md` | 测试期（`Beta_x_x_x`）的改动 |
| `<项目名>-<游戏版本>-<类型>-ChangeLog.md` | 正式版（去掉 `Beta` 之后）的改动 |

格式（时间取该版本的提交时间；正式版只写日期）：

```
[Beta_0_1_0] 2026.10.17 12:54:26
1.新增……功能
2.修复了……

[Beta_0_1_1 -> Beta_0_2_0] 2026.10.17 20:33:12
1.新增……功能
2.修复了……
```

```
[正式版1_0_0] 2026.11.01
1.新增……功能

[正式版1_0_0 -> 2_0_0] 2026.12.05
1.新增……功能
2.修复……
```

- 首个版本写 `[版本号]`，之后每次写 `[上一版本 -> 新版本]`
- 每条用 新增 / 修复 / 调整 措辞并顺序编号
- 现有项目的历史记录已按 git 提交时间补齐，见各项目目录下的 ChangeLog 文件

### 当前版本

| 项目 | 版本 |
| --- | --- |
| MainMenu | `Beta_0_6_0` |
| RealTime | `1_0_0`（正式版，2026.10.18） |
| MainMenuRepair（fabric） | `1_0_0`（正式版，2026.10.18） |

> RealTime 与 MainMenuRepair 已出正式版：之后按「第 1 位 +1、后两位归 0」涨号，不再带 `Beta`。
> 逐版本改动见各项目目录下的 ChangeLog：`…-ChangeLog-Beta.md` 记测试期，`…-ChangeLog.md` 记正式版。

### 版本历史

`release/` 里保留了每一版的产物；下面是对应的提交与改动（早期被同名覆盖的版本已用 `scripts/rebuild-history.sh` 从 git 历史重建）。

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

**RealTime（插件）**

| 版本 | 提交 | 改动 |
| --- | --- | --- |
| `Beta_0_1_0` | `9108bca` | 首个版本：主菜单第 1 行第 2 格显示当前时间 |

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

**下载 Gradle 失败**：wrapper 默认走华为云镜像（见 `gradle/wrapper/gradle-wrapper.properties`）。想换回官方源，把 `distributionUrl` 改成 `https://services.gradle.org/distributions/gradle-9.8.1-bin.zip` 即可；CI 里已经在构建前自动换回官方源。

**`runServer` 启动后立刻退出**：看 `run/eula.txt` 是否还是 `eula=false`，改成 `true` 再启动。

**改了 `paper-plugin.yml` 或 `config.yml` 不生效**：`paper-plugin.yml` 需要重新 `./gradlew :插件名:build` 再重启服务端；`config.yml` 在 `run/plugins/<插件名>/` 下，改完用插件自带的 reload 命令热重载。注意默认配置只在文件不存在时才会释放，删掉该文件才会重新生成。

**提示 Java 版本不对**：确认 `gradle.properties` 的 `targetJavaVersion` 不低于服务端要求（26.3 为 25），并让本机装有对应 JDK。

## 参考链接

- [Paper 插件开发文档](https://docs.papermc.io/paper/dev/)
- [Paper 工程配置说明](https://docs.papermc.io/paper/dev/project-setup/)
- [Paper 服务端下载 / 构建列表](https://papermc.io/downloads/paper)
- [MiniMessage 文本格式](https://docs.advntr.dev/minimessage/format.html)
- [run-paper（本地测试服插件）](https://github.com/jpenilla/run-task)

## License

[MIT](LICENSE)
