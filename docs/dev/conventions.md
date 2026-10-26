# mcPlugin 开发规范（总纲）

> 适用范围：本仓库全部插件（`./Minecraft-26_3-plugin/`）与客户端模组（`./fabric-26_3-mod/`）。
> 目标：构建可复现、产物可追溯、插件间接口稳定；所有规则的细节只在本目录定义一次，其他位置只引用。

专项规范：

- [版本与产物规范](./版本与产物规范.md)（涨号规则、产物命名、留档、ChangeLog、发布流程）
- [功能插件接入规范](./功能插件接入规范.md)（以 MainMenu 为依赖的功能插件：依赖声明、注册 API、位置 / 排序 / 冲突）

## 1. 技术栈

| 层 | 选型 | 说明 |
| --- | --- | --- |
| 服务端平台 | Folia 26.3（Minecraft 26.3） | 插件 `api-version` 必须为 `26.3`，运行期由服务端提供 Folia API（Paper API 的超集） |
| 插件语言 | Java 25（本机 JDK 26 也可） | 字节码统一 `options.release = 25`；Folia 26.3 最低 Java 25 |
| 构建 | Gradle 9.8.1（仓库自带 wrapper）+ run-paper 3.1.0 | 根 `build.gradle` 统一配置；`runServer` 内部执行 Folia 任务 `runFolia` |
| 客户端模组 | Fabric（26.3），不用 fabric-loom | 官方客户端 jar 未混淆，直接编译；Mixin 注解用本仓库的注解桩 |
| 文本 | Adventure MiniMessage | 随 Paper/Folia API 内置，配置与消息统一用 MiniMessage |
| 产物 | jar，留档到 `./release/<项目名>/` | 命名、涨号与 ChangeLog 见[版本与产物规范](./版本与产物规范.md) |

## 2. 仓库结构与路径约定

```
./                                   # 仓库根，所有相对路径的基准
├── AGENTS.md                        # AI 协作入口：硬规则、必读清单、沟通字典、现状快照
├── docs/
│   ├── dev/                         # 规范层（提交）：本文件 + 专项规范
│   ├── exp/                         # 经验层（提交）：坑与经验库
│   ├── plan/                        # 计划层（不提交）：阶段计划与任务拆解
│   └── local.md                     # 机密（不提交）：本机路径 / 账密 / 代理 / token
├── Minecraft-26_3-plugin/<插件名>/   # Folia 插件（沿用 paper-plugin.yml），一个目录一个插件
├── fabric-26_3-mod/MainMenuRepair/  # Fabric 客户端模组（独立 Gradle 工程）
├── templates/plugin-template/       # newPlugin 脚手架模板
├── release/<项目名>/                 # 构建产物留档（提交进仓库）
├── run/                             # 共用的 Folia 测试服（不提交）
└── scripts/                         # 仓库维护脚本（如 rebuild-history.sh）
```

路径约定（硬规则）：

- 仓库内所有文件提到路径时**一律写仓库相对路径**（`./xxx/xxx`），禁止 `C:\...`、`D:/...` 这类绝对路径；
  换机器、换盘符、仓库改名后必须照样可用。
- 脚本必须自定位仓库根目录（`scripts/rebuild-history.sh` 由脚本所在位置往上推），不得写死仓库位置。
- 脚本的临时文件放系统临时目录（`${TMPDIR:-/tmp}`）或仓库内已 gitignore 的目录，不散落进源码树。
- Gradle / CI 里的路径写成相对工程根的形式（`rootProject.file('...')`、`./gradlew -p ./...`），不要改成绝对路径。
- 例外：插件运行期读写的路径相对**服务端根目录**（`plugins/<插件名>/config.yml`），测试服里就是 `./run/plugins/<插件名>/`。

## 3. Folia 插件编码约定

- **Folia 声明**：每个 `paper-plugin.yml`（或旧式 `plugin.yml`）必须写 `folia-supported: true`，否则 Folia 会拒绝加载。
- **命令写在代码里**：在 `onEnable` 监听 `LifecycleEvents.COMMANDS` 注册 Brigadier 命令，不在 `paper-plugin.yml` 写 `commands:`。
- **`folia-api` 必须 `compileOnly`**：运行期由服务端提供；用 `implementation` 会打进 jar 造成类冲突。
- **`api-version` 与游戏版本同步**：填 `26.3`，升级时与 `gradle.properties` 的 `minecraftVersion` 一起改。
- **文本用 MiniMessage**：配置与消息里的 `<yellow>` 等标签直接生效；旧版 `&a` 需显式用 `LegacyComponentSerializer` 转换。
- **优先 Paper/Folia API 而非 NMS**：读写方块 / 实体 / 数据包先查 Paper 文档；官方 API 确实覆盖不到才考虑 paperweight-userdev。
- **插件间依赖**：`paper-plugin.yml` 里声明 `dependencies.server.<插件>.load: BEFORE`，编译期用
  `compileOnly project(':MainMenu')`；**不要 `implementation`**。功能插件写法见[功能插件接入规范](./功能插件接入规范.md)。
- **配置释放**：默认配置只在目标文件不存在时释放；新增键要有兜底值。关键 UI 元素（菜单材质等）宁可写死在代码里，
  避免旧配置缺键时被兜底成错误的值（见 [exp/paper-plugin.md](../exp/paper-plugin.md)）。
- **持久化**：玩家可管理的数据写 `getDataFolder()`（服务端根目录下 `plugins/<插件名>/`），不要写绝对路径。
- **线程（Folia 区域化调度）**：**禁止使用 `Bukkit.getScheduler()`**（Folia 上不可用）。
  - 全局任务（遍历在线玩家、广播、与位置无关的维护）用 `Bukkit.getGlobalRegionScheduler()`；
  - 世界 / 方块 / 区块任务用 `Bukkit.getRegionScheduler()`；
  - 玩家 / 实体相关任务（打开背包、改背包内容、发消息、传送、改实体状态）用 `player.getScheduler()`；
  - 纯 IO / 网络用 `Bukkit.getAsyncScheduler()`。
  - 背包与实体状态只能在该实体所属区域线程操作；已在该线程时直接执行，不要多绕一 tick。
  - 跨区域线程共享的集合用并发容器或显式加锁；不要假设“主线程”存在。
  - 详见 [exp/folia.md](../exp/folia.md)。
- **权限**：新增命令 / 管理界面必须登记权限节点，并在 `paper-plugin.yml` 里写清默认值。
- **测试插件**：联调用的临时插件命名 `TestPlugin*`，不参与产物留档、不随 Release 发布。

## 4. Fabric 客户端模组约定

- **不引入 fabric-loom，不 remap**：26.3 客户端未混淆，编译只需官方客户端 jar + `src/mixin-stubs/java` 注解桩。
- 客户端 jar 由 `downloadMinecraftClient` 从 `gradle.properties` 的 `client_jar_url` 下载到 `build/minecraft/`，
  不要提交进仓库。
- Mixin 注入一律 `require = 0`：目标方法变化时模组不崩溃，只是该条改动失效。
- 关键逻辑优先用 Fabric API 屏幕事件等官方通道实现，Mixin 只做增强（原因见 [exp/fabric-mod.md](../exp/fabric-mod.md)）。
- 纯客户端模组：`fabric.mod.json` 的 `environment` 为 `client`，不要求服务端装任何东西（除非文案里写明依赖 MainMenu）。

## 5. 新增插件与模板

- 用 `./gradlew newPlugin -PpluginName=名字 [-PpackageName=包名]` 生成；脚手架会替换
  `ExamplePlugin` / `exampleplugin` / `com.example.exampleplugin` 三处占位符并搬好包目录。
- 目录名、`paper-plugin.yml` 的 `name` / `main`、Java 包名三者必须一致；插件名只能字母 / 数字 / 下划线且字母开头。
- 模板的 `paper-plugin.yml` 已带 `folia-supported: true`，新插件不要删掉这一项。
- 同名命令与权限节点不要和现有插件重复（脚手架只改权限前缀，命令字面量要自己改）。
- 新增插件后 `./gradlew listPlugins` 能列出即注册成功（`settings.gradle` 自动发现）。

## 6. Git 规范

- 分支：`main` 保持可构建；较大改动可用 `feat/xxx`、`fix/xxx`。
- **远端与镜像**：`origin` 指向主仓 `zhou-BZDDX/mcPlugin`，并**配置了两个 push 地址**（主仓 + 社团镜像
  `SWUST-Minecraft-Club/mcPlugin`），所以直接 `git push` 会一次推到两边；`swust` 是镜像的独立远端，只用于核对镜像是否同步。
  推送后确认两边都在同一提交：`git ls-remote origin main`、`git ls-remote swust main`。
- 提交信息用中文，格式 `类型: 描述`，类型取 `feat` / `fix` / `docs` / `refactor` / `chore` / `release`。
- 不提交：`./run/`、`./build/`、IDE 目录、`docs/plan/`、`docs/local.md`、真实凭证。
- 例外：`./release/**/*.jar` 必须提交（产物留档）；`gradle/wrapper/gradle-wrapper.jar` 必须提交。
- 提交前自查：无调试日志、无注释掉的死代码、无无主 TODO；构建产物与源码一致（重新构建后 `git status` 应为空）。

## 7. 文档与计划

- 文档分五层：入口 `AGENTS.md`、规范 `docs/dev/`、经验 `docs/exp/`、计划 `docs/plan/`（不提交）、机密 `docs/local.md`（不提交）。
- **一处定义**：一条规则只在一处定义，其他位置引用不复制。
- **沉淀闭环**：踩坑先写 `docs/exp/`（现象 / 根因 / 规避 / 来源）；稳定成「必须 / 禁止」级后升格进本目录，
  规范引用 exp 条目，条目不删除（保留"为什么"）。
- 长期事实进 `docs/`，阶段过程进 `docs/plan/`，教训进 `docs/exp/`。
- 结构性调整（目录、模块、导航、发布方式）完成后，同步更新 `AGENTS.md` 的「现状快照」行。

## 8. 规范来源与时效（自 2026-10-26 起）

- 开发前应查阅当前目标平台、语言和 API 的适用官方文档、规范及迁移说明，并遵守与任务相关的 API 契约、安全要求和兼容性要求。
- **禁止**仅因某项做法出现在外部插件文档、示例或其他仓库，就将其直接写成本仓硬规范。外部资料用于核实技术事实和适用标准；是否采纳其他项目的架构选择或流程约定，必须有本仓明确决策或可复核的验证依据。
- 技术栈、模块位置、当前构建行为等描述事实的内容，与「必须 / 禁止」规则分开写；不得把当前实现状态自动推导成长期约束。
- 当前开发与测试基线固定为 Minecraft / Folia **26.3**，由根目录 `gradle.properties` 的 `minecraftVersion` 控制。日常开发和验证以 26.3 为准，不得静默切换基线；其他版本未实际验证前，不得声称已支持。
- 以 26.3 为当前目标，同时控制版本耦合，让后续升级（例如 26.4）尽量只需小范围修改。优先使用公开、受支持的 API；将确有版本差异的代码集中在清楚的适配点，并记录版本依赖和验证范围。**不为尚未采用的版本预先增加多版本兼容层或猜测性抽象。**
- 新版本可用时，对照官方发布说明、API 和迁移资料评估升级成本与影响；若能以小代价升级，则同步更新版本配置、依赖、CI、资源和测试基线，并在目标版本完成构建及必要验证后再切换。升级前仍以 26.3 为当前基线。
- 新增硬规则时，写清适用范围、触发条件、来源或验证依据及生效版本/日期；来源不明或尚未验证的内容标为待确认，不得表述为已生效规则。
- 规则变更只在本目录的对应位置定义；入口文件和 README 只链接到定义处。稳定规则新增或调整后，同步维护 `AGENTS.md` 的现状快照日期及受影响内容。
