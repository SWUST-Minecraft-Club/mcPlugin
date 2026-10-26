# 构建与仓库环境：坑与经验

## 仓库改名 / 换盘符后，写死绝对路径的脚本直接失效

- **现象**：仓库目录由 `mcPligin` 改名为 `mcPlugin` 之后，`scripts/rebuild-history.sh` 一执行就在 `cd` 处失败 ——
  里面的 `REPO="D:/code/Plugin/mcPligin"` 指向了不存在的目录。
- **根因**：脚本把仓库位置写死成绝对路径；仓库一改名、换盘符、换机器就必然失效。
- **规避/解决**：仓库内一律相对路径（`./xxx/xxx`）；脚本自定位仓库根目录：

  ```bash
  REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # 脚本在 ./scripts/ 下
  RB="${TMPDIR:-/tmp}/mcplugin-rebuild"                     # 临时工作区，不写死盘符、不落在仓库里
  ```

  该约定已升格进 `docs/dev/conventions.md` §2，全仓路径也在 2026.10.18 统一为相对形式。
- **来源**：2026.10.18「路径统一」任务；`./scripts/rebuild-history.sh`。

## Gradle 工具链（toolchain）fork 的 worker 在部分机器上起不来

- **现象**：启用 java toolchain 后构建直接失败（工具链 worker 进程无法启动），换一台机器又正常。
- **根因**：Gradle fork 出来的 toolchain worker 在部分机器 / 环境下起不来。
- **规避/解决**：**本机 JDK 不低于目标版本时直接用当前 JDK 编译**，字节码版本交给 `options.release = 25` 控制；
  只有本机 JDK 太旧时才启用 toolchain（`settings.gradle` 里的 foojay 插件会自动下载对应 JDK）。
  这样既避免 fork worker，也保证不同机器产出一致的字节码版本。
- **来源**：2026.10.18，MainMenu `Beta_0_2_1` 起的构建方式；`./build.gradle` 的 `subprojects` 段。

## 下载慢 / 超时：mirror 策略

- **现象**：下载 Gradle 发行包、Maven 依赖、Fabric 相关构件时速度很慢或超时（详见 `fabric-mod.md` 的 loom 条目）。
- **根因**：默认源在部分网络环境下被限速。
- **规避/解决**：
  - Gradle wrapper 默认走华为云镜像（`./gradle/wrapper/gradle-wrapper.properties`）；CI 在构建前用 `sed` 换回官方源。
  - Maven 依赖慢时可在根 `build.gradle` 里加阿里云镜像；`folia-api` / `paper-api` **只有 PaperMC 官方仓库有**，镜像必须保留。
  - 客户端 jar 直接从 Mojang 官方 `piston-data` 地址下载，不经过 Fabric 的 Maven。
- **来源**：`./.github/workflows/build.yml`、`./.github/workflows/release.yml`、README「常见问题」。

## 构建产物是可复现的：重复构建不会污染 `./release/`

- **现象**：担心反复构建会改写 `./release/<项目名>/` 里的留档 jar，让 git 工作区变脏。
- **根因**：Gradle 的 jar 任务输出稳定 —— 相同输入产出相同字节。
- **规避/解决**：构建前记下 `git status`，构建后确认仍为空即可；重建脚本（`./scripts/rebuild-history.sh`）补档同样安全。
  2026.10.18 实测：脚本重建出的历史 jar 与仓库里已提交的同名 jar 字节一致。
- **来源**：2026.10.18 环境验证；`./scripts/rebuild-history.sh`、`./build.gradle` 的 `archiveArtifact`。

### 本机装了 JDK 25 但 Gradle 工具链找不到（foojay 远程解析失败）

- **现象**：`compileJava` 报 `Cannot find a Java installation ... matching: languageVersion=25`，并伴随 foojay resolver `Remote host terminated the handshake`。
- **根因**：Gradle 工具链自动探测只扫标准目录与 JAVA_HOME；本机 JDK 25 装在自定义目录（如 `D:/dev/envs/jdk-25.x`），foojay 远程下载又被网络环境拦掉。
- **规避/解决**：命令行显式注入 `-Dorg.gradle.java.installations.paths=<本机JDK25目录>`；不写进仓库配置（机器相关路径不入库）。
- **来源**：2026.10.24，Town 首次构建。

## 官方客户端依赖下载应有输出目录之外的校验缓存

- **现象**：把已下载的客户端和库预先放在 `build/` 后，首次执行下载任务仍可能清理输出再重新下载；慢速连接下，大文件重复请求耗时很长。
- **根因**：`build/` 是可清理的任务输出目录，不能兼作持久下载缓存。单连接大文件下载还会被部分网络环境限速。
- **规避/解决**：MinecraftMcp 将官方清单、客户端和编译库按 SHA-1 放进项目 `./.gradle/minecraft-downloads/`，下载任务校验后复制至输出；只取编译实际引用的库，有限并行下载，支持传入已有官方客户端与缓存齐全后的 `--offline` 构建。缓存、第三方 jar 和本机路径不提交。
- **来源**：2026.10.26，MinecraftMcp 首次构建；`./fabric-26_3-mod/MinecraftMcp/versions/mc26_3/build.gradle`。

### 用户级 Gradle 代理失效 → 所有远程仓库 TLS 握手失败（直连却通）

- **现象**：`Could not GET https://repo.maven.apache.org/...`，`Remote host terminated the handshake`；`curl` 直连同地址返回 200。
- **根因**：`~/.gradle/gradle.properties` 里配置的本地代理（127.0.0.1:7897）进程活着但隧道握手失败；Gradle 走该代理全军覆没，curl 没走代理反而通。
- **规避/解决**：注释掉代理两行并加 `systemProp.http.nonProxyHosts=*` 旁路（原配置备份 `gradle.properties.bak-*`），`./gradlew --stop` 后重跑；网络恢复后可还原。
- **来源**：2026.10.24，Town 首次构建。
