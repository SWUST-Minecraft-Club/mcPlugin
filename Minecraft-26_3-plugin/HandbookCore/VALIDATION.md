# HandbookCore Beta_0_1_0 验证

日期：2026-10-26。构建与测试只覆盖 Minecraft / Folia **26.3**。

## 构建与自动化

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

## 实机环境

- 专用本机 Folia 26.3 build 5、Java 25；没有修改共用测试服。
- CraftEngine `26.9.2-folia26.3.1`，合并手册包后生成和校验资源包成功。
- Minecraft 26.3 / Fabric Loader 0.20.0 / Fabric API 0.165.0+26.3。
- MainMenuRepair `Beta_1_2_0`、MinecraftMcp `Beta_0_1_0`；1280×800、GUI 缩放 2、简体中文。
- MainMenu 归档 `Beta_1_6_0` 验证背包回退入口；本轮未重建 MainMenu，也未在 ArcMenu 手机界面实测入口。
- 独立 TestPluginHandbook 仅以 API jar 编译，注册 12 分类 × 每类 8 文章，含 CE 图、CE 物品与长文。
  临时测试插件不在发布包中。

## 实机结果

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
截图中的扩展测试文章并非正式玩法攻略，正式默认正文为可编辑示例。
