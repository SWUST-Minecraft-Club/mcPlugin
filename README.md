# MCPlugin263

[![Build](https://github.com/your-name/MCPlugin263/actions/workflows/build.yml/badge.svg)](https://github.com/your-name/MCPlugin263/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Minecraft](https://img.shields.io/badge/Minecraft-26.3-green.svg)
![Java](https://img.shields.io/badge/Java-25-orange.svg)

Minecraft **26.3**（Paper）插件开发工程模板：Gradle 工程 + 示例插件代码 + 本地测试服 + GitHub CI 一次配好，clone 下来就能写插件。

## 环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 25 及以上 | Paper 26.3 要求 Java 25；本机装的是 JDK 26 也能用，Gradle 会用工具链把字节码编译到 25 |
| Gradle | 9.8.1 | 仓库自带 wrapper（`gradlew`），不需要手动安装 Gradle |
| Paper API | `26.3.build.200-stable` | 在 `gradle.properties` 中配置 |

> 本机没有 JDK 25 时，`settings.gradle` 里的 foojay 插件会自动下载对应版本的工具链，无需手动装。

## 快速开始

```bash
git clone https://github.com/your-name/MCPlugin263.git
cd MCPlugin263

./gradlew build        # 编译打包，产物在 build/libs/MCPlugin263-<版本>.jar
./gradlew runServer    # 启动 Paper 26.3 测试服，并自动加载刚构建的插件
```

首次 `runServer` 会自动下载 Paper 服务端并在 `run/` 目录生成世界。服务端会因为没同意 EULA 主动退出一次，把 `run/eula.txt` 里的 `eula=false` 改成 `eula=true`（表示你同意 [Mojang EULA](https://aka.ms/MinecraftEULA)），再执行一次 `./gradlew runServer` 即可。

进服后在游戏里或控制台输入 `/mcplugin info`、`/hello` 就能看到示例效果。

## 常用命令

| 命令 | 作用 |
| --- | --- |
| `./gradlew build` | 编译 + 打包 + 检查 |
| `./gradlew runServer` | 启动本地 Paper 测试服（服务端文件在 `run/`） |
| `./gradlew clean` | 清理构建产物 |
| `./gradlew build --refresh-dependencies` | 强制重新拉取依赖 |
| `./gradlew tasks` | 查看全部可用任务 |

## 目录结构

```
.
├── build.gradle                    # 依赖、Java 工具链、runServer 配置
├── gradle.properties               # Minecraft / Paper API / Java 版本（升级版本改这里）
├── settings.gradle                 # 工程名与工具链解析
├── src/main/java/com/example/mcplugin263/
│   ├── MCPlugin263.java            # 插件主类：加载配置、注册命令与监听器
│   ├── command/HelloCommand.java   # /hello [名字]
│   ├── command/AdminCommand.java   # /mcplugin reload|info
│   └── listener/PlayerJoinListener.java  # 玩家进服欢迎
├── src/main/resources/
│   ├── paper-plugin.yml            # Paper 插件描述文件
│   └── config.yml                  # 默认配置（MiniMessage 文本）
└── .github/                        # CI、Release、Issue/PR 模板、Dependabot
```

## 插件自带的命令与权限

| 命令 | 权限节点 | 默认 |
| --- | --- | --- |
| `/hello [名字]` | `mcplugin263.hello` | 所有人 |
| `/mcplugin info` | `mcplugin263.admin` | OP |
| `/mcplugin reload` | `mcplugin263.reload` | OP |

权限在 `paper-plugin.yml` 中声明，改完记得 `./gradlew build` 重新打包。

## 26.3 开发要点

1. **命令写在代码里**。Paper 插件不使用 `plugin.yml` 的 `commands:` 字段，而是在 `onEnable` 里监听 `LifecycleEvents.COMMANDS` 注册 Brigadier 命令（见 `MCPlugin263.java`）。
2. **文本用 MiniMessage**。Adventure 的 `MiniMessage` 随 Paper 内置，配置里的 `<yellow>` 会被渲染成颜色；旧版 `&a` 写法需要自己用 `LegacyComponentSerializer` 转换。
3. **`paper-api` 必须是 `compileOnly`**。运行期由服务端提供这些类，用 `implementation` 打包进去会导致类冲突。
4. **`api-version` 填 `26.3`**。`paper-plugin.yml` 里的 `api-version` 与 `gradle.properties` 的 `minecraftVersion` 保持一致。
5. **优先用 Paper API 而非 NMS**。需要读写方块、实体、数据包时先查 [Paper 文档](https://docs.papermc.io/paper/dev/)，只有官方 API 覆盖不到时才考虑 paperweight-userdev。

## 升级 Minecraft 版本

只改 `gradle.properties`：

```properties
minecraftVersion=26.4
paperApiVersion=26.4.build.1-stable
targetJavaVersion=25
```

`paperApiVersion` 的可选构建号见 [PaperMC Maven 仓库](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/)。也可以用动态版本 `26.4.build.+`（不推荐：每次构建拉到的版本可能不同，产物不可复现）。

## 发布到 GitHub Releases

```bash
# 1. 修改 build.gradle 里的 version，例如 0.2.0
# 2. 提交并打 tag（tag 名与 version 保持一致）
git commit -am "chore: release 0.2.0"
git tag v0.2.0
git push origin main --tags
```

推送 `v*` 标签会触发 `.github/workflows/release.yml`，自动构建并把 jar 附加到 Release 上。

## 改造成你自己的插件

1. `settings.gradle` 的 `rootProject.name`
2. `build.gradle` 的 `group`、`version`
3. Java 包目录 `src/main/java/com/example/mcplugin263/` 及每个文件顶部的 `package`
4. `src/main/resources/paper-plugin.yml` 的 `name`、`main`、`authors`、`website`、权限节点前缀
5. `LICENSE` 的版权署名，README 顶部徽章里的用户名/仓库名

## 常见问题

**依赖下载慢或超时**：把 `build.gradle` 里的 `mavenCentral()` 换成国内镜像即可（`paper-api` 只有 PaperMC 官方仓库有，镜像里没有，所以要保留）：

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

**下载 Gradle 失败**：wrapper 默认走华为云镜像（见 `gradle/wrapper/gradle-wrapper.properties`）。想换回官方源，把 `distributionUrl` 改成 `https://services.gradle.org/distributions/gradle-9.8.1-bin.zip`；CI 里已经在构建前自动换回官方源。

**`runServer` 启动后立刻退出**：看 `run/eula.txt` 是否还是 `eula=false`，改成 `true` 再启动。

**改了 `paper-plugin.yml` 或 `config.yml` 不生效**：`paper-plugin.yml` 需要重新 `./gradlew build` 再重启服务端；`config.yml` 在 `run/plugins/MCPlugin263/` 下，改完用 `/mcplugin reload` 热重载。注意默认配置只在文件不存在时才会释放，删掉该文件才会重新生成。

**提示 Java 版本不对**：确认 `gradle.properties` 的 `targetJavaVersion` 不低于服务端要求（26.3 为 25），并让本机装有对应 JDK。

## 参考链接

- [Paper 插件开发文档](https://docs.papermc.io/paper/dev/)
- [Paper 工程配置说明](https://docs.papermc.io/paper/dev/project-setup/)
- [Paper 服务端下载 / 构建列表](https://papermc.io/downloads/paper)
- [MiniMessage 文本格式](https://docs.advntr.dev/minimessage/format.html)
- [run-paper（本地测试服插件）](https://github.com/jpenilla/run-task)

## License

[MIT](LICENSE)
