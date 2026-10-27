# HandbookCore 验证

## Beta_0_1_2：紧凑书本与字体资源

日期：2026-10-27。Minecraft / Folia **26.3**、Java 25，CraftEngine `26.9.2-folia26.3.1`。

### 构建与资源校验

- `./gradlew :HandbookCore:build` 成功，**27 项测试，0 失败，0 错误**。
- 保留原有 21 项测试，新增：293px 行宽与中途不溢出、18 条原生行、累计小数取整、动作与基线保留、
  罕见 Unicode 换行、共享正文位图与完整原版图标集、第三方 CE 字体网格和重载清理，共 6 项。
- 600 个中文字符仍排为 6 张纸，API 图片逻辑尺寸仍为 1..176 × 1..144；原 API jar 编译的测试插件继续注册 12 分类 / 96 篇文章。
- 4 个归档产物校验成功；安装 ZIP 的 **118 个文件**逐项 SHA-256 校验成功。
  独立的测试插件、测试文章和验证工具未包含在发布包中。

### 常见分辨率与交互

所有分辨率均以客户端 `client.state` 的实际窗口/帧缓冲尺寸为准，`guiScale: 0`（自动）。
画布净宽高为 293×162，包含原生内边距后的正文控件为 301×170；原 API 使用 520×288 逻辑坐标。

| 实际分辨率 | 自动 GUI / 有效 GUI 尺寸 | 结果 / 证据 |
| --- | --- | --- |
| 1280×720 | 3 / 427×240 | 无 MainMenuRepair：原生上下区域、144px 逻辑高度 CE 大图、完整书底和关闭按钮全部可见；[大图](./assets/verification/compact-1280-native-large.png)、[首页](./assets/verification/compact-home-1280.png) |
| 1366×768 | 3 / 456×256 | 无 MainMenuRepair：首页、边距、独立书签与书底完整可见；[首页](./assets/verification/compact-home-1366.png) |
| 1920×1080 | 4 / 480×270 | 安装 MainMenuRepair Beta_1_2_0：书本完整居中、隐藏焦点框和重复页脚；[首页](./assets/verification/compact-home-1920.png)、[图文](./assets/verification/compact-article-1920.png) |

1920×1080 与 1280×720 均复查文章 `1 / 3 → 2 / 3 → 1 / 3`、动态 CE 书签、分类 `1 / 2 → 2 / 2`、
输入「检索验证」得到 96 条结果并从 `1 / 16 → 2 / 16`、书内合上按钮返回游戏。
原版物品图标、CE 图片、独立书签选中态与文字均随整体比例缩放；[检索](./assets/verification/compact-search-1280.png)。

### 资源包重载与内存

- 1.5 GiB 堆的专用 Fabric 客户端完成新包加载、旧图标资源包升级到共享图集、再次更换测试图片高度后的整包重载，保持连接且可继续阅读。
- 中间方案曾在重载时 OOM。堆直方图定位到约三万个单字形 bitmap provider 各自分配完整 Unicode 索引表。
  合并 1508 个原版图标为共享图集后，该数组占用由约 538 MB 降到约 13 MB；完整 GC 后活跃对象总量由约 941 MB 降到约 324 MB。
- 正文字体保留 16px 原始笔画，紧排至 2048×4000，重复 height/ascent 共享 bitmap provider。
  未收录字符直接选单独的 Unicode 回退字体，保留原生 9px 宽；不为所有紧凑字体重复挂完整 Unicode 回退。
- 此结果仅覆盖上述客户端、测试包与数据规模，不代表任意插件数量或第三方资源总量的内存/性能保证。

本版继续只附一条可删除的更新日志。截图中的扩展分类、长文与大图来自测试贡献，未恢复内置玩法页面。
纯服务端的白框、警告图标与原生页脚仍由客户端决定；缩小尺寸本身不依赖 MainMenuRepair。

## Beta_0_1_1：移除内置玩法与升级迁移

日期：2026-10-26。Minecraft / Folia **26.3**、Java 25。

- `./gradlew :HandbookCore:build` 成功，**21 项测试，0 失败，0 错误**。
- 新增 6 项文件迁移测试：仅初始化一条日志、删除文件或整个目录后不补回、原文件完整备份、
  移除旧示例并保留日志、自定义文章及跨文件分类引用、已有空目录不补内容、一次性迁移、非法文件失败后可重试。
- 4 个归档产物 SHA-256 校验成功，安装 ZIP 内 24 个文件逐项校验成功。
  旧示例仅作为测试夹具保存，不包含在插件 jar 中。
- 专用 Folia 服从旧版四分类正文升级：核心输出 **1 分类 / 1 条目**，旧文件备份与迁移前逐字节一致。
- 原 API jar 编译的 TestPluginHandbook 仍可注册 **12 分类 / 96 条目**；与核心日志合计 **13 / 97**。
  暂移日志并执行 `/handbook reload` 后为 **12 / 96**，说明重载核心正文没有清理插件贡献。
- 保持日志缺失后正常停服、重启：核心输出 **0 / 0**，测试插件注册后仍为 **12 / 96**；
  日志文件没有重新生成，备份仍只有一份。验证后恢复仅含更新日志的正文并正常停服。

本版调整正文初始化与迁移；未重新拍摄布局截图。以下截图及视觉、分页交互记录属于 Beta_0_1_0。

## HandbookCore Beta_0_1_0 验证

日期：2026-10-26。构建与测试只覆盖 Minecraft / Folia **26.3**。

### 构建与自动化

`./gradlew :HandbookCore:build` 成功，**15 项测试，0 失败，0 错误**。
API jar、CE ZIP、安装 ZIP 和 SHA-256 与插件同时归档；安装 ZIP 中 24 个文件逐项校验成功。

覆盖：

- 600 个中文字符自动排成 6 张纸，拼回内容无丢失，单纸不超过 8 行。
- 显式换行、颜色、长标题省略、画布越界、插图越界。
- HTTP(S) 地址和指令控制字符校验，分页后的动作保留。
- 重复 id、跨 owner 注销、原子替换失败保留旧快照、分类权限、全文多词检索、不可变快照。
- YAML 未知块、缺失字段的文件名错误提示。
- 12 个独立贡献者、36 个分类、900 个文章全部可搜索；移除其中一个贡献者后保留 33 个分类、825 个文章。

这表示已覆盖上述数据规模，不代表无限插件数量或任意内容规模的性能保证。

### 实机环境

- 专用本机 Folia 26.3 build 5、Java 25；没有修改共用测试服。
- CraftEngine `26.9.2-folia26.3.1`，合并手册包后生成和校验资源包成功。
- Minecraft 26.3 / Fabric Loader 0.20.0 / Fabric API 0.165.0+26.3。
- MainMenuRepair `Beta_1_2_0`、MinecraftMcp `Beta_0_1_0`；1280×800、GUI 缩放 2、简体中文。
- MainMenu 归档 `Beta_1_6_0` 验证背包回退入口；本轮未重建 MainMenu，也未在 ArcMenu 手机界面实测入口。
- 独立 TestPluginHandbook 仅以 API jar 编译，注册 12 分类 × 每类 8 文章，含 CE 图、CE 物品与长文。
  临时测试插件不在发布包中。

### 实机结果

| 项目 | 结果 / 证据 |
| --- | --- |
| 首页 | 原版图标、双页居中、内边距和 4 分类布局正常；[截图](./assets/verification/home.png) |
| 分类翻页 | 第一页显示 6 条和 `1 / 2`，点击右箭头后显示剩余 2 条和 `2 / 2`；左箭头可返回；[第一页](./assets/verification/category-page-one.png)、[第二页](./assets/verification/category-page-two.png) |
| 首页分类容量 | 16 分类显示 4 页，右箭头切换分类与右侧书签组 |
| 长文续页 | CE 插图、物品入口与长文混排，显示 `1 / 3`，右箭头可继续；[图文](./assets/verification/article.png)、[续页](./assets/verification/article-next.png) |
| 检索 | 输入「检索验证」提交，96 个结果显示 `1 / 16`，可继续翻页；空结果显示说明；[截图](./assets/verification/search.png) |
| 动态书签 | CE 小图与原版 16px 图标均可显示，点击分类改变金扣选中态；[截图](./assets/verification/bookmark-selected.png) |
| 外链 | 点击 HTTP(S) 链接进入客户端确认页，未继续打开外部浏览器；[截图](./assets/verification/external-link-confirm.png) |
| 指令 | 点击「打开小镇分类」以玩家身份执行，实际跳到小镇分类 |
| CE 物品 | `internal:next_page_0` 实际显示箭头模型和「下一页」物品名；[截图](./assets/verification/ce-item-preview.png) |
| 缺失物品 | 不存在的 CE id 显示调用者给定的 DIAMOND；[截图](./assets/verification/missing-item-preview.png) |
| 内部链接 | 从测试文章打开农场生活指引成功 |
| 合上与 ESC | 两者均关闭书本，回到游戏 |
| 会话 | 切换页面后的旧 nonce 请求无效，不改变当前屏幕 |
| reload 失败 | 加入缺少 category 的 YAML，提示文件名并保留 4 分类、5 条目旧数据 |
| 贡献者清理 | 停用 fixture 后，16 分类/101 条目变成 4 分类/5 条目，其他配置内容仍可读 |
| MainMenu | 背包菜单中的 BOOK 入口点击可打开手册 |
| 无可选依赖 | 移出 CE 与 MainMenu 后独立启动成功，`status` 显示 CE false / 书页图片 false；文字首页 `第 1 页 / 共 4 页` 可切到第 2 页，原生文章从 `第 1 页 / 共 2 页` 切到第 2 页；[文字目录](./assets/verification/native-home.png)、[文字文章](./assets/verification/native-entry.png) |
| 字体与白框 | 中英文字体、数字、斜杠均有包内字形；点击外层不出现白框，原生灰色页脚隐藏；普通检索和物品 Dialog 保留原生布局 |

白框、整体居中与灰色页脚隐藏由可选 MainMenuRepair 客户端模组完成；纯服务端只能保留原生行为。
截图中的扩展测试文章并非正式玩法攻略；本历史版本自带的玩法示例已在 Beta_0_1_1 移除。
