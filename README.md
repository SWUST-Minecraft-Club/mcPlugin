# Minecraft 26.3 插件集合

[![Build](https://github.com/your-name/your-repo/actions/workflows/build.yml/badge.svg)](https://github.com/your-name/your-repo/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Minecraft](https://img.shields.io/badge/Minecraft-26.3-green.svg)
![Java](https://img.shields.io/badge/Java-25-orange.svg)

Minecraft **26.3**（Paper）多插件仓库。所有插件放在 `Minecraft-26_3-plugin/<插件名>/`，由仓库根目录的一个 Gradle 工程统一管理：共享构建配置和版本号，每个插件又能单独打包、单独启动测试服。

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
├── Minecraft-26_3-plugin/
│   └── MCPlugin263/                # 示例插件，同时是 newPlugin 的模板
│       ├── build.gradle            # 本插件独有的：版本号、额外依赖
│       ├── src/main/java/com/example/mcplugin263/
│       │   ├── MCPlugin263.java            # 主类：配置、命令、监听器注册
│       │   ├── command/HelloCommand.java   # /hello [名字]
│       │   ├── command/AdminCommand.java   # /mcplugin reload|info
│       │   └── listener/PlayerJoinListener.java
│       ├── src/main/resources/
│       │   ├── paper-plugin.yml    # Paper 插件描述文件
│       │   └── config.yml          # 默认配置（MiniMessage 文本）
│       └── run/                    # 本插件的测试服目录（首次 runServer 生成，已 gitignore）
└── .github/                        # CI、Release、Issue/PR 模板、Dependabot
```

新增插件只需要在 `Minecraft-26_3-plugin/` 下建一个含 `build.gradle` 的目录，`settings.gradle` 会自动把它注册成 Gradle 子工程，不用手改配置。

## 常用命令

| 命令 | 作用 |
| --- | --- |
| `./gradlew build` | 构建**所有**插件 |
| `./gradlew :插件名:build` | 只构建某个插件，产物在该插件目录的 `build/libs/` |
| `./gradlew :插件名:runServer` | 启动该插件的测试服（Paper 26.3 + 自动加载本插件） |
| `./gradlew listPlugins` | 列出所有插件及对应命令 |
| `./gradlew newPlugin -PpluginName=MyPlugin` | 按模板创建一个新插件 |
| `./gradlew clean` | 清理所有插件的构建产物 |
| `./gradlew :插件名:tasks` | 查看该插件的全部任务 |

首次 `runServer` 会自动下载 Paper 服务端并在该插件的 `run/` 目录生成世界。服务端会因为没同意 EULA 主动退出一次，把 `run/eula.txt` 里的 `eula=false` 改成 `eula=true`（表示你同意 [Mojang EULA](https://aka.ms/MinecraftEULA)），再执行一次即可。

## 新增插件

```bash
# 复制模板并自动替换包名、类名、插件名、权限节点
./gradlew newPlugin -PpluginName=Shop

# 也可以指定包名（默认 com.example.shop）
./gradlew newPlugin -PpluginName=Shop -PpackageName=com.myserver.shop

# 换一个插件当模板（默认以 MCPlugin263 为模板，所以别删它）
./gradlew newPlugin -PpluginName=Shop -Ptemplate=MCPlugin263

# 确认已注册
./gradlew listPlugins
```

脚手架做的事：把 `MCPlugin263` 整个目录复制成 `Minecraft-26_3-plugin/Shop/`，把 `MCPlugin263`/`mcplugin263`/`com.example.mcplugin263` 全部换成新名字，并搬好 `src/main/java` 下的包目录。之后把 `paper-plugin.yml` 里的 `authors`、`website` 填上就能开工。

也可以直接手动复制 `MCPlugin263` 目录再逐个改名，记得目录名、`paper-plugin.yml` 的 `name`/`main`、Java 包名和 `build.gradle` 的 `version` 要一致。

## 插件清单

| 插件 | 说明 |
| --- | --- |
| [MCPlugin263](Minecraft-26_3-plugin/MCPlugin263) | 模板 / 示例插件：`/hello [名字]`、`/mcplugin reload\|info`、进服欢迎、MiniMessage 配置 |

权限节点：`mcplugin263.hello`（默认所有人）、`mcplugin263.admin`（OP）、`mcplugin263.reload`（OP）。

> 同一台服务器上装多个插件时，注意命令名和权限节点不要重复，否则会被后加载的插件覆盖。

## 26.3 开发要点

1. **命令写在代码里**。Paper 插件不使用 `plugin.yml` 的 `commands:` 字段，而是在 `onEnable` 里监听 `LifecycleEvents.COMMANDS` 注册 Brigadier 命令（见 `MCPlugin263.java`）。
2. **文本用 MiniMessage**。Adventure 的 `MiniMessage` 随 Paper 内置，配置里的 `<yellow>` 会被渲染成颜色；旧版 `&a` 写法需要自己用 `LegacyComponentSerializer` 转换。
3. **`paper-api` 必须是 `compileOnly`**。运行期由服务端提供这些类，用 `implementation` 打包进去会造成类冲突。这条已在根 `build.gradle` 配好。
4. **`api-version` 填 `26.3`**，与 `gradle.properties` 的 `minecraftVersion` 保持一致。
5. **优先用 Paper API 而非 NMS**。需要读写方块、实体、数据包时先查 [Paper 文档](https://docs.papermc.io/paper/dev/)，只有官方 API 覆盖不到时才考虑 paperweight-userdev。

## 测试服说明

- 每个插件有独立的 `run/` 目录（`Minecraft-26_3-plugin/<插件名>/run`），互不影响，可以同时开多个。
- 想让所有插件共用一个测试服目录：打开根 `build.gradle` 里 `runServer` 配置中注释掉的 `runDirectory(rootProject.file('run-shared'))`（共用时不能同时启动两个 runServer）。
- 想固定服务端构建号：同处打开 `build(200)`，构建号见 [papermc.io/downloads/paper](https://papermc.io/downloads/paper)。
- 想额外装别的插件到测试服：放进该插件的 `run/plugins/` 目录即可。

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

**`runServer` 启动后立刻退出**：看该插件 `run/eula.txt` 是否还是 `eula=false`，改成 `true` 再启动。

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
