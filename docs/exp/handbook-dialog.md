# Dialog 双页手册与 CraftEngine

## 小屏适配需要扣除原生 Dialog 的上下区域

- **现象**：将书本从 520×288 缩到 390×216 后，1280×720 自动 GUI 3 倍下仍会裁掉书底。
- **根因**：该分辨率的有效 GUI 只有 427×240，Dialog 还保留上下共 66px，正文控件自身另含上下各 4px 内边距。
  只按窗口宽度或书图高度选比例，无法保证底部分页可见。
- **规避/解决**：保留 520×288 逻辑画布与 API 图片尺寸，在最终输出时统一缩到 56.25%。净画布 293×162，
  18 条原生 9px 行，正文控件 301×170；字形、CE 图片、命中区域和坐标一起变换。
  同一原生行里的逻辑行通过 0..8 的基线偏移字体还原位置；按累计逻辑坐标取整并逐字补偿 advance，避免小数误差积累。
- **来源**：2026.10.27，HandbookCore `Beta_0_1_2` 的 `BookScale` / `BookCanvas`、26.3 官方 `DialogScreen` 字节码，
  1280×720 / 1366×768 / 1920×1080 自动 GUI 实机验证。MainMenuRepair 只负责可选的居中、轮廓与页脚修复。

## 字体 reference 不能消除整份 Unicode 回退的内存开销

- **现象**：为正文、标题、按钮生成九种基线字体并分别挂 Unicode 回退，1.5 GiB 堆客户端在加载或重载资源包时 OOM；
  将 Unicode provider 改成单份 reference 后仍然开销很大。另一次把中文字形预先降为 8px 的尝试破坏了笔画。
- **根因**：客户端每个 FontSet 仍会枚举其 provider 支持的字符，reference 共享 provider 不等于共享各 FontSet 的字符缓存；
  将原本 16px 的中文字形先降采样，再交给客户端显示，丢失的笔画无法恢复。
- **规避/解决**：正文与紧凑角色字体只包含 bitmap；不支持的字符由输出器直接选择单独的 `handbookcore:fallback`，保留 9px 原生字宽并参与换行。
  相同 height/ascent 的 bitmap provider 共享，31,910 个支持字符紧排成 2048×4000 图集，保留 16px 原始字形细节。
  修复后的 1.5 GiB 堆客户端完成初次加载与资源包重载；这只说明该验证包通过，不代表任意第三方资源包的内存保证。
- **来源**：2026.10.27，`compileBookFont` / `compileCompactFonts` / `BookScale`，26.3 客户端字体加载日志、OOM 记录和最终清晰截图。

## 大量单字形 bitmap provider 的空索引表会占满堆

- **现象**：已共享正文 bitmap 并独立 Unicode 回退后，1.5 GiB 客户端初次打开正常，整包重载仍然 OOM。
- **根因**：1508 个原版图标原先每个 provider 只有一个字符，大小图与九种基线副本合计约三万个 provider。
  26.3 的每个 bitmap provider 使用 `CodepointMap`，即使只有一个字形也分配完整顶层索引数组；
  `jcmd GC.class_histogram` 显示 30,892 个 `BitmapProvider$Glyph[][]` 单独占约 538 MB，未计行数组和字形对象。
- **规避/解决**：构建时将原版图标整理进 32px 单元的共享图集，保留全部独立码点与对应指标；
  每个尺寸和基线只使用一个 provider。合并的是图集存储，目录和书签仍能独立选择图标。
  对有大量图片的第三方 CE 包也应优先使用字符网格图集；字体引用共享并不能自动合并不同贴图 provider 的索引表。
  同一测试环境重载后，索引顶层数组降到 752 个、约 13 MB，完整 GC 后活跃对象总量由约 941 MB 降到约 324 MB；
  修复后的 1.5 GiB 堆客户端完成资源包重载。数据仅对应本次测试包。
- **来源**：2026.10.27，`compileVanillaIcons` / `CompactBookTest`，26.3 官方客户端 `BitmapProvider` / `CodepointMap`、专用客户端堆直方图。

## 书图缩放切片需要落在整数 GUI 像素

- **现象**：原先 125px 宽单元直接乘 56.25% 后，拼接边出现黑缝。
- **根因**：单元宽为 70.3125px，而客户端 bitmap advance 和定位分别取整，独立缩放的边无法精确接合。
- **规避/解决**：先把整张原图编译到 560×324 源图，再切成 140×108 单元，最终显示单元正好 70×54；
  整张书图 280×162，包含书签和边缘余量的逻辑画布输出为 293×162。定位与实际非透明边界测宽分别处理。
- **来源**：2026.10.27，`compileCompactFonts` / `BookCanvas` 与专用客户端拼接前后截图。

## CE 外部字体目录需要在缓存资源包事件注入

- **现象**：第三方插件注册的图片需要紧凑字体副本，只缩小手册坐标会让图片维持原尺寸；在生成结束事件添加字体目录又赶不上本轮合包。
- **根因**：CE 的字体配置读取和最终资源包生成是不同阶段，生成结束后包内文件已经确定。
- **规避/解决**：反射监听 `AsyncResourcePackCacheEvent`，从 `FontManager.loadedBitmapImages()` 快照生成九种基线副本，
  通过该事件的 `registerExternalResourcePack(Path)` 注入；保留原始 texture 引用和字符网格，仅改变副本的 height/ascent。
  每次从当前快照重建并清理旧测量缓存，避免已移除贡献留下字体。核心内建字体跳过精确 id 集合，不能跳过整个命名空间。
  事件不支持或合包失败时回退文字目录，升级材质后需要 `/ce reload all` 并让玩家加载新包。
- **来源**：2026.10.27，CraftEngine `26.9.2-folia26.3.1` 事件与 FontManager API，`CraftEngineBridge` / `CompactFontPack`；
  `fixture` 命名空间的 32px 图标与 144px 图片实机及第三方字体网格测试。

## 玩法指南由所属插件贡献，配置刷新时整批替换

- **现象**：小镇指南需要出现在全局手册中，成立人数、赠地和价格又由 Town 的运营配置决定。
- **根因**：把正文另写入核心的 `content/` 会形成独立副本，插件停用后仍可能留下文章，修改玩法配置也不会自动更新文案。
- **规避/解决**：Town 通过公开 `HandbookApi`，以自己为 owner 调用 `replace` 注册分类与 14 篇条目；启动和配置重载时从配置快照构造正文，停用时注销自己的贡献。HandbookCore 重载 YAML 不影响 Town owner。可选 API 引用放到外层探测成功后才调用的嵌套适配器，缺少 HandbookCore 时不加载正文类型。按钮使用玩家权限的 Command 内容块，收费和工程确认仍交给 Town 自己的流程。文字版从同一正文导出为默认配置快照，单测检查全部内链和实际分页后的正文完整性。
- **来源**：2026.10.26，Town `Beta_0_8_0`；`TownGuide` / `HandbookHook`，81 项单测与 Folia 26.3 普通玩家实机：缺核心启动、分类翻页、文章跳转、检索、领取工具、配置刷新及核心 YAML 重载。

## 正文宽度需要算上客户端内边距

- **现象**：所有行服务端都算成同样的净 advance，客户端仍把双栏换行挤乱。
- **根因**：26.3 的 `FocusableTextWidget` 正文左右各留 4px，传入 Dialog 的宽度不全是可绘文字宽度。
- **规避/解决**：Beta_0_1_0 的 520px 净画布传 `DialogBody.plainMessage(component, 528)`；Beta_0_1_2 的 293px 净画布传 301。
  每条换行前通过 space 字形归到同一净宽。
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
