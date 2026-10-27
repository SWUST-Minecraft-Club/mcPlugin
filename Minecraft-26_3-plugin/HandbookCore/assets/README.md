# 手册素材

## 正在使用

| 文件 | 来源与用途 |
| --- | --- |
| `./book-v2.png` | Codex 内置 ImageGen 生成的透明 RGBA 完整书本基础材质。纸张、书脊、标题飘带、三个底部按钮和翻页装饰在同张原图中；中央留白，内容由运行时绘制 |
| `./bookmarks-source.png` | 同画风的独立书签图集：4 种颜色 × 普通/选中两态，生成器提供透明背景 |
| `./fonts/DroidSansFallbackFull.ttf` | Android Droid Sans Fallback，Google 2006–2010，Apache 2.0；源自 Debian `fonts-droid-fallback`，上游 Android frameworks/base |
| `./fonts/DejaVuSansMono.ttf` | DejaVu Sans Mono，补足 Droid 缺少的拉丁字母、数字及符号；Bitstream Vera 许可与 DejaVu 公有领域修改 |
| 构建生成的 `textures/font/vanilla-icons.png` | 从固定 SHA-1 的 Mojang 官方 Minecraft 26.3 client jar 提取物品缩略图，合并为共享图集；原版美术归 Mojang 所有，原生物品模型预览仍由客户端渲染 |
| `./verification/` | Folia 26.3 + Minecraft 26.3 专用测试客户端截图，非概念图 |

完整许可见 `./fonts/LICENSE.txt` 与 `./fonts/DejaVu-LICENSE.txt`，构建时随 CE 包附在 `licenses/` 中。
图片生成提示词保存在 [prompts.md](./prompts.md)。用户提供的 Patchouli 参考用于观察双页布局、书签和分页，未复制其贴图。

## 探索过程

`./concept-book.png`、`./book-source.png`、`./home-source.png` 为此前生成的设计探索图，保留作美术来源记录。
其中的固定分类插画不用于运行时，也不写入发布的 CE 包。

## 构建

- `compileBookTexture`：按原有 500×288 逻辑尺寸，以 4 列 × 3 行字形切分。源单元为 250×192，符合客户端单字形不超过 256×256 的限制；最终显示由下述紧凑任务生成。
- `compileBookmarks`：按原图等分网格寻找 alpha 轮廓，裁切为 42×32 的逻辑尺寸，保留四种颜色与两种状态，最终随书本一同缩放。没有几何重绘控件。
- `compileBookFont`：将两份字体绘成固定单元图集，保留正文/按钮高 12、标题高 14 的逻辑指标，输出字形支持位图和字体许可；未收录字符直接选择单独的 Unicode 回退字体。
- `compileVanillaIcons`：校验固定 Minecraft 26.3 官方 client jar 的 SHA-1 后，将 1508 个原版图标整理成 2048×768 图集，
  采用最近邻缩放、每项独立码点，生成同源字宽/边距数据。每个尺寸/基线只需一个 bitmap provider，避免逐图标分配完整 Unicode 索引表。
- `compileCompactFonts`：最终以 56.25% 输出，整张书图先编译为 280×162 的整数单元再切片，避免拼接黑缝；
  字体保留 16px 字形细节，使用 2048×4000 紧排图集与 9 种基线偏移，并共享相同 height/ascent 的 bitmap provider；
  紧凑字体不引用 Unicode 回退，避免各 FontSet 重复枚举整份字库；按源图非透明边界生成缩放 advance 指标。
- 近透明 bitmap 命中字形由构建生成，用于书内按钮的点击区域；它不代替可见书本美术。

运行时的图片、CE 图标、原版图标、文章文字、书签状态和页码都独立渲染。坐标、内边距和实际字形 advance 由
`BookCanvas`、`BookText`、`BookIcons` 共同计算，图集单元与字体指标随资源一起构建。

Beta_0_1_2 的 `compact-*.png` 为常见分辨率与自动 GUI 缩放的实机证据；旧截图保留作历史记录，详见验证文档。
