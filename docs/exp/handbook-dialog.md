# Dialog 双页手册与 CraftEngine

## 玩法指南由所属插件贡献，配置刷新时整批替换

- **现象**：小镇指南需要出现在全局手册中，成立人数、赠地和价格又由 Town 的运营配置决定。
- **根因**：把正文另写入核心的 `content/` 会形成独立副本，插件停用后仍可能留下文章，修改玩法配置也不会自动更新文案。
- **规避/解决**：Town 通过公开 `HandbookApi`，以自己为 owner 调用 `replace` 注册分类与 14 篇条目；启动和配置重载时从配置快照构造正文，停用时注销自己的贡献。HandbookCore 重载 YAML 不影响 Town owner。可选 API 引用放到外层探测成功后才调用的嵌套适配器，缺少 HandbookCore 时不加载正文类型。按钮使用玩家权限的 Command 内容块，收费和工程确认仍交给 Town 自己的流程。文字版从同一正文导出为默认配置快照，单测检查全部内链和实际分页后的正文完整性。
- **来源**：2026.10.26，Town `Beta_0_8_0`；`TownGuide` / `HandbookHook`，81 项单测与 Folia 26.3 普通玩家实机：缺核心启动、分类翻页、文章跳转、检索、领取工具、配置刷新及核心 YAML 重载。

## 正文宽度需要算上客户端内边距

- **现象**：所有行服务端都算成同样的净 advance，客户端仍把双栏换行挤乱。
- **根因**：26.3 的 `FocusableTextWidget` 正文左右各留 4px，传入 Dialog 的宽度不全是可绘文字宽度。
- **规避/解决**：520px 净画布传 `DialogBody.plainMessage(component, 528)`；每条换行前通过 space 字形归到同一净宽。
  正文与图标必须使用客户端实际字形 advance，不能以 PNG 整体宽度代替非透明边界。
- **来源**：2026.10.26，HandbookCore `BookCanvas` / `HandbookViews#showBook`，官方 26.3 客户端 `FocusableTextWidget` 字节码与专用客户端截图。

## 大书本要切字形，图片尺寸与 advance 是两回事

- **现象**：大图单个字形加载失败；图标有透明边距时视觉偏心。
- **根因**：bitmap 字形单元上限为 256×256，整张图集可以更大；字形 advance 取最右侧非透明像素换算后的宽度再加 1。
  图片左侧透明边距也会参与视觉居中。
- **规避/解决**：完整书本原图切成 4×3 字形，单元 250×192，显示为 125×96，利用不同 ascent 叠回整图。
  图标记录 advance、height、bearing，先补偿左透明边距再居中。CE reload 后按 bitmap 定义身份清理旧测量缓存。
  任意 CE 插图使用显式宽高内容块预留行数，尺寸过大分图，不在正文路径猜测缩放。
- **来源**：2026.10.26，HandbookCore 资产任务、`CraftEngineBridge#metrics`、`BookIcons`；
  [CE 图片文档](https://xiao-momi.github.io/craft-engine-wiki/configuration/image/) 与官方客户端 BitmapProvider。构建源固定 26.3 client SHA-1 `e877b6a07acd633fb3bb475002175cec036e7b87`。

## 透明 space 命中区在此布局无法翻页

- **现象**：书底箭头有正常图形，点击没有 `_nav` 请求；正文链接可以点击。
- **根因**：本次多行覆盖布局使用仅有 advance 的 space 字形作命中区，客户端没有将该段识别为期望的可点文字区域。
  不能由这次实验推导所有 space 字形在所有 UI 中都不可点击。
- **规避/解决**：命中区改成 alpha=1 的近透明 bitmap 字形，按按钮宽度铺到实际点击位置。
  页码采用包内按钮字体，明确显示 `当前 / 总数`；首页、目录/检索和文章分别计算当前视图的页数。
- **来源**：2026.10.26，`BookCanvas#hit`、`HandbookViews#footer`。实机点击从 6 条条目的 `1 / 2` 切到剩余 2 条的 `2 / 2`，另验证反向翻页、长文和 96 条检索结果。

## Droid Fallback 名称不表示含全部英文与数字

- **现象**：中文大小正确，数字/英文偏小、页码斜杠难辨。
- **根因**：选用的 DroidSansFallbackFull 字体 `canDisplay` 对 ASCII 数字和拉丁字母返回 false；客户端继续落到 unihex。
- **规避/解决**：构建逐字选择 Droid 与 DejaVuSansMono，保留两者许可；生成支持位图用于服务端测宽，避免客户端 fallback 与服务端计算不一致。
  中文、数字和英文字形都在固定 16px 单元中绘制，正文固定 advance 13、标题 15。
- **来源**：2026.10.26，`compileBookFont`、`book-font.bin`、最终页码与混排实机截图。

## CE Adventure 组件不能直接强转 Paper 组件

- **现象**：通过 CE 的 image API 得到组件，却无法作为 Paper Adventure Component 使用。
- **根因**：当前 CE 将 Adventure 重定位到了自己的包名。
- **规避/解决**：可选依赖桥接反射访问 CE `Image#componentAt`，用 CE `AdventureHelper#componentToJson` 序列化，
  再由 Paper `GsonComponentSerializer` 解析。物品走 `CraftEngineItems.byId(...).buildBukkitItem(player)`，失败用调用者给的原版 ItemStack。
- **来源**：2026.10.26，`CraftEngineBridge`；CraftEngine `26.9.2-folia26.3.1` 实机图片与自定义物品模型预览。

## 外层白框与整体居中需要客户端协作

- **现象**：点到整本书的外层出现白框；只移动文字后 scissor 裁掉书本顶部；原生灰色关闭按钮破坏书本视觉。
- **根因**：`FocusableTextWidget#extractWidgetRenderState` 按焦点状态绘制轮廓，服务端 Dialog API 没有关闭该轮廓的字段；
  正文在 `DialogScreen.bodyScroll` 中，移动文字不会同步其裁剪区域。
- **规避/解决**：可选客户端 MainMenuRepair 识别 `handbookcore` 字体，通过 `@Redirect` 只替换渲染路径里的焦点判断，
  实际焦点、键盘与点击不变；递归找到滚动容器内的正文后居中整个 bodyScroll，隐藏书内已有替代按钮的原生页脚。
  纯服务端保留原生轮廓与布局，要在部署文档里说明边界。
- **来源**：2026.10.26，MainMenuRepair `Beta_1_2_0`，`HandbookFocusMixin` / `HandbookDialogLayout`，
  26.3 `DialogScreen` / `FocusableTextWidget` 官方字节码；居中后正文 x56/y52，点击后无外框的实机截图。

## 删除默认正文要同时处理旧文件与重新释放逻辑

- **现象**：替换 jar 中的示例不会清理服务端已经生成的旧玩法；按文件是否存在调用 `saveResource`，还会在管理员删除正文后重新生成。
- **根因**：默认资源与运行期内容是两份文件，文件缺失无法区分首次安装和运营者主动删除。
- **规避/解决**：用数据目录内的初始化标记记录首次释放。核心只附一条更新日志；旧 `content/server.yml` 的三个示例 id 仅迁移一次，
  修改前完整备份到 `content/` 之外，避免备份又被递归读取。保留其他自定义文章，并扫描全部正文的分类引用，保留仍被使用的旧分类。
  迁移直接处理 YAML；完整的物品与 API 定义校验仍由正文加载阶段完成。Folia 26.3 的 `Material#isItem` 需要运行期注册表，
  因此纯文件迁移测试不应构建 ItemStack，也不应为了脱离服务器测试而削弱材质校验；最终载入在专用 Folia 服验证。
- **来源**：2026.10.26，HandbookCore `Beta_0_1_1`，`ContentBootstrap` / `ContentLoader` / `ContentBootstrapTest` 与专用验证服。

## 注解桩保留级别应与真实注解一致

- **现象**：`@Redirect` 编译成功但静默不生效，白框仍存在。
- **根因**：手写 Redirect 注解桩缺少 `@Retention(RUNTIME)`，Mixin 运行期找不到注入声明。
  旧 `fabric-mod.md` 将所有 Mixin 注解概括成 RUNTIME 不准确，真实 `@Mixin` 使用 CLASS，注入注解 Redirect 使用 RUNTIME。
- **规避/解决**：按实际 sponge-mixin jar 的注解签名与 retention 维护各个桩，不能统一改一种 retention；
  注入仍使用 `require=0`，目标变化时允许增强失效。必要时用 `javap -v` 检查生成 class 的 RuntimeVisible/InvisibleAnnotations。
- **来源**：2026.10.26，MainMenuRepair `src/mixin-stubs/.../Redirect.java` 与运行中的 sponge-mixin 0.17.5+mixin.0.8.7；实机修复白框。
