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

## Git URL 重写会改变推送认证的主机

- **现象**：正常的 GitHub 远端在推送时变成代理地址，报无法读取 Username 或 HTTP 403；本机对 GitHub 的凭据实际可用。
- **根因**：全局 `url.<代理前缀>.insteadOf` 改写传输 URL，而凭据按主机匹配；代理主机与 GitHub 主机的认证不是同一项。本次添加反向重写后，原始 GitHub URL 仍先匹配原规则，未能绕过代理。
- **规避/解决**：先区分 `git config --get-all remote.origin.pushurl` 的原始地址与 `git remote get-url --push --all origin` 的实际地址，再核对重写的配置作用域。临时直推时，只在该次进程中让 `GIT_CONFIG_GLOBAL` 指向空配置，并通过进程配置保留原有 `credential.helper`；否则绕过重写时也会失去全局认证配置。凭据可用性通过捕获 `git credential fill` 的结果判断，只记录布尔状态，禁止打印或提交密码。推送后分别核对主仓与镜像的 `main`。
- **来源**：2026-10-26，MainMenu `Beta_1_6_0` 推送；代理传输失败，临时绕过全局重写后两个 origin push 地址均成功。代理地址、凭据和本机配置位置均不在本条记录中展开。

## 本地附件和历史留档不能代替 CI 的当次构建输出

- **现象**：手机/钱包打包脚本最初要求传入本机 Vault 附件，并只读取 `./release/`；云端没有附件路径，构建遗漏时也可能取到历史 JAR。
- **根因**：本地交付输入没有公开、可校验的来源；仓库留档和本次构建输出没有区分。
- **规避/解决**：核对用户附件与 SirBlobman/Vault-Folia `v1.7.3-folia` 上游发布文件，SHA-256 均为 `140b9e75cd5371280f4bbf1cc68dc733ce7b8ffe3ea273f01c4da831022d4dd8`。依赖锁固定 URL、哈希和源码提交，不提交运行 JAR。CI 显式使用 `--artifacts build`，缺少本次输出或内嵌版本不符即失败；离线留档模式必须显式选择。ZIP 固定条目时间与权限，校验全部条目后另写外层 SHA-256。
- **来源**：2026-10-26，CI 与插件 ZIP 发布；`./scripts/package-mainmenu-wallet.py`、`./scripts/dependencies/vault-folia.json`、`./.github/workflows/build.yml`、`./.github/workflows/release.yml`。来源链接和许可文本随包分发。
