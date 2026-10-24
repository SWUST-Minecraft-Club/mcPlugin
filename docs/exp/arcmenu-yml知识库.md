# ArcMenu YAML 配置知识库（面向 AI 生成菜单）

> 适用范围：ArcMenu 当前 `0.1.0-SNAPSHOT` 源码（核对日期：2026-09-05）。  
> 用途：可直接交给大语言模型、RAG 知识库或人工配置者，用于生成、解释、审查和修复 ArcMenu YAML。  
> 本文只记录当前代码真正支持的配置，不把设想中的功能当成现有功能。

---

## 目录

- [0–4：AI 规则、文件体系、核心模型与菜单根结构](#0-给-ai-的最高优先级规则)
- [5：Frontend 视觉元素](#5-frontend视觉元素)
- [6–8：Backend、条件、占位符与状态](#6-backend点击区域)
- [9–10：动作语言与菜单事件](#9-动作语言)
- [11：完整双页面示例](#11-一套可直接落盘的完整示例)
- [12–13：动画与 Editor 模板](#12-animationsyml动画知识库)
- [14–17：config、offset、tooltip 与语言文件](#14-configyml全局输入配置)
- [18：校验、重载与发布](#18-加载校验与发布工作流)
- [19–20：AI 输出契约与最终自检](#19-ai-输出契约)
- [21–24：排错、动作速查、最小模板与结论](#21-常见错误与修复)

---

## 0. 给 AI 的最高优先级规则

生成 ArcMenu 配置时，必须遵守以下规则：

1. 菜单文件放在 `plugins/ArcMenu/menus/` 下，扩展名必须是小写 `.yml`。菜单加载器不会把 `.yaml` 当作菜单文件。
2. 只输出本文列出的字段、元素类型、动作、条件和占位符；不要根据其他菜单插件的经验发明字段。
3. YAML 禁止重复键。根对象和各类对象都采用严格字段校验，拼错字段不会被忽略，而会使校验失败。
4. 每个菜单必须显式写 `schema-version: 1`、`id` 和完整的 `canvas`。
5. 整个 `menus/` 目录只能有一个 `main-menu: true`。向已有菜单库新增文件时，默认生成 `main-menu: false`，除非用户明确要替换主菜单。
6. `id`、静态菜单目标、动画 ID、元素 ID、图片路径和命令名都必须按各自规则生成，不要使用中文 ID。
7. 当前 Paper 运行时只应生成 `right` 和 `shift-right` 点击入口；`all` 也能匹配当前点击。不要为现阶段菜单生成 `left`、`shift-left` 或单独的 `shift`。
8. 前端元素负责显示，后端区域负责命中。后端区域不会跟随前端组变换、旋转或动画，必须独立填写根画布坐标。
9. 动态内容用 `update` 刷新；`update: -1` 表示不周期刷新，正整数表示刷新间隔（tick）。`0` 和小于 `-1` 的值非法。
10. 颜色必须加引号。菜单元素颜色用 `'#RRGGBB'` 加独立的 `opacity`；Tooltip 背景用 `'#AARRGGBB'`。
11. 动作优先输出 YAML 列表，一项一个动作。不要生成 TrMenu 风格的 `[action] ...`，也不要把多动作挤进私有分隔符。
12. 对 `console`、`op`、跨服、经济、点券、物品发放等有副作用的动作，只在用户明确要求时生成；`op` 会临时以管理员权限执行命令，尤其需要谨慎。
13. 如果信息不足但用户要求直接生成，采用安全默认值，并把假设写在代码块前；如果用户要求“只输出 YAML”，则把假设写成 YAML 注释。
14. 输出多个文件时，必须分别标注文件路径并使用独立 YAML 代码块；不能把多个 YAML 根对象拼进同一个文件。
15. 生成后必须执行本文末尾的“AI 自检清单”，再给出最终答案。

### 0.1 AI 在生成前应取得的信息

最低限度需要知道：

- 是新建整套菜单，还是向现有菜单库追加菜单；
- 菜单 ID、入口方式，以及是否需要主菜单；
- 画布尺寸或期望布局；
- 每个可点击区域的动作、权限和拒绝反馈；
- 是否使用图片、PlaceholderAPI、Vault、PlayerPoints、CraftEngine 或跨服；
- 是否需要动画、持久数据或输入捕获；
- 用户服务器中已经存在的菜单 ID、开放命令和主菜单情况。

若只是追加一个菜单且上下文未知，安全默认是：

```yaml
main-menu: false
open-commands: []
```

不要擅自占用开放命令，也不要额外制造第二个主菜单。

---

## 1. 文件体系与依赖

### 1.1 AI 可以编写的 YAML

| 文件 | 用途 | 备注 |
|---|---|---|
| `plugins/ArcMenu/menus/**/*.yml` | 菜单文档 | 只加载小写 `.yml`，支持子目录 |
| `plugins/ArcMenu/animations.yml` | 过渡和元素轨道动画 | 全局动画目录 |
| `plugins/ArcMenu/templates/*.yml` / `*.yaml` | Editor 模板 | 模板可用两种扩展名；只是复制源，不是运行时引用 |
| `plugins/ArcMenu/config.yml` | 语言、快捷键和鼠标模式 | 插件全局配置 |
| `plugins/ArcMenu/offset.yml` | 菜单在玩家视野中的放置 | 分 touch/mouse 两套 |
| `plugins/ArcMenu/tooltip.yml` | Tooltip 全局样式 | 分 touch/mouse 两套 |
| `plugins/ArcMenu/languages/<locale>.yml` | 插件内部提示语言 | 不控制菜单正文 |

以下内容不要由菜单生成 AI 修改：

- `data.properties`：ArcMenu 的持久状态数据；
- 自动生成的资源包、索引和缓存；
- 插件二进制、服务端命令注册和其他插件配置。

### 1.2 依赖能力

| 依赖 | 类型 | 相关能力 |
|---|---|---|
| ProtocolLib | 必需 | 菜单交互和指针输入 |
| PlaceholderAPI | 可选 | 解析外部 `%placeholder%` |
| Vault + Economy 实现 | 可选 | money 动作 |
| PlayerPoints | 可选 | points 动作 |
| CraftEngine | 可选 | 自定义物品和资源相关能力 |

配置中引用可选依赖前，应先确认服务器已安装并正常提供对应服务。缺少 Vault Economy 或 PlayerPoints 时，相关动作只会警告并不产生实际余额变化。

---

## 2. ArcMenu 的核心模型

一个菜单由四层组成：

```text
菜单文档
├─ canvas：逻辑画布和世界缩放
├─ frontend：玩家看到的元素树
├─ backend：独立的矩形点击区域
└─ events：菜单打开/关闭时执行的动作
```

### 2.1 前端和后端不是同一个东西

- `frontend` 只渲染视觉元素。
- `backend` 只定义可交互的轴对齐矩形。
- 给矩形、文字或图片添加视觉元素，并不会自动产生点击区域。
- 后端的 `x`、`y` 是点击矩形中心点，不是左上角。
- 后端不继承前端组的位移、缩放、旋转，也不跟随元素动画。
- 前端元素的 `visible: false` 不会自动禁用后端点击。
- 同一点命中多个区域时，先选 `priority` 更大的区域；相同优先级按文档顺序决定。

因此，AI 设计按钮时应该同时生成一份视觉元素和一份同坐标、同尺寸的后端区域。若视觉按钮会旋转、缩放或移动，必须提醒用户后端仍停留在原位置。

### 2.2 坐标系

- 画布原点 `(0, 0)` 位于中心。
- `x > 0` 向右，`x < 0` 向左。
- `y > 0` 向上，`y < 0` 向下。
- `z > 0` 朝向玩家，用于深度分层。
- 菜单里的尺寸和坐标都是逻辑像素。
- `pixels-per-block` 把逻辑像素换算为 Minecraft 世界单位。

例如，画布宽 `320`，则左右边缘约为 `x = -160` 与 `x = 160`。一个宽 `100`、中心 `x = 0` 的后端区域覆盖 `[-50, 50]`。

### 2.3 颜色和文本

- 菜单几何元素：`color: '#RRGGBB'`，透明度另写 `opacity: 0..255`。
- Tooltip 全局背景：`background: '#AARRGGBB'`。
- 文本、聊天、标题、物品名称与 lore 使用传统 `&` 颜色代码，例如 `&a`、`&l`。
- ArcMenu 文本不是 MiniMessage。不要生成 `<green>文字</green>` 一类 MiniMessage 标签。
- YAML 中以 `#` 开头的颜色必须加引号，否则会被当成注释。

---

## 3. YAML 与标识符的严格规则

### 3.1 通用 YAML 规则

- 使用空格缩进，不使用 Tab。
- 建议统一使用 2 个空格缩进。
- 禁止重复键。
- 字符串若包含 `#`、`:`、`%`、`{}`、反斜杠、前导 `!` 或类似 YAML 特殊语法，优先加引号。
- 布尔值只能写 `true` 或 `false`。
- 整数不要写成带小数点的数。
- 静态配置中避免依赖 YAML 锚点、合并键或复杂标签；输出朴素 YAML。

### 3.2 菜单 ID、命令和资源 ID

菜单 ID、开放命令、动画 ID 等常用 ID 采用：

```regex
[a-z0-9][a-z0-9_-]*
```

合法示例：`main`、`shop-2`、`player_menu`。  
非法示例：`Main`、`菜单`、`-shop`、`shop.detail`。

开放命令还必须：

- 不带 `/`；
- 长度不超过 64；
- 一个菜单内不重复；
- 整个菜单目录内不与其他菜单重复；
- 不与 Bukkit 已注册命令或 fallback 命令冲突。

外部扩展路由使用资源 ID 形式：

```text
namespace:path
```

例如：`myplugin:profile`。

### 3.3 元素 ID

`frontend`、嵌套 `group.children` 和 `backend` 的键都是元素 ID，并共享一个菜单级全局唯一命名空间。

元素 ID：

- 不能为空；
- 不能包含控制字符；
- 不能包含 `/` 或 `\`；
- 在同一菜单中不能重复，即使位于不同 group；
- 建议只使用小写字母、数字和连字符，例如 `buy-button-bg`。

不要在 `frontend` 和 `backend` 中同时使用同一个 ID。推荐视觉元素加后缀 `-bg`、`-label`，后端区域使用语义 ID，如 `buy-button`。

---

## 4. 菜单文件完整结构

### 4.1 推荐骨架

```yaml
schema-version: 1
id: example-menu
permission: ''
open-commands: []
main-menu: false

canvas:
  width: 320
  height: 180
  pixels-per-block: 100
  distance: 3

frontend: {}
backend: {}

events:
  open: []
  close: []
```

`permission`、`open-commands`、`main-menu`、`canvas`、`frontend`、`backend` 和 `events` 在解析器中都有缺省处理，但 AI 应显式写出重要字段，避免默认值因加载流程而产生歧义。尤其是 `canvas.pixels-per-block`，始终明确填写。

### 4.2 根字段

| 字段 | 类型 | 默认值 | 规则 |
|---|---:|---:|---|
| `schema-version` | 整数 | 无 | 必须为 `1` |
| `id` | 字符串 | 无 | 必填，符合菜单 ID 规则，全目录唯一 |
| `permission` | 字符串 | `''` | 为空表示无菜单专属权限；打开者仍需 `arcmenu.use` |
| `open-commands` | 字符串或字符串列表 | `[]` | 注册为直接打开该菜单的命令，不能带 `/` |
| `main-menu` | 布尔 | `false` | 整个菜单库必须恰好一个为 `true` |
| `canvas` | 对象 | 有内部默认 | 推荐完整填写四项 |
| `frontend` | 对象 | `{}` | 视觉元素映射 |
| `backend` | 对象 | `{}` | 点击区域映射 |
| `events` | 对象 | `{}` | 只允许 `open`、`close` |

`permission` 非空时，玩家需要同时拥有 `arcmenu.use` 和该菜单权限。管理命令通常需要 `arcmenu.admin`。

### 4.3 Canvas

```yaml
canvas:
  width: 320
  height: 180
  pixels-per-block: 100
  distance: 3
```

四个字段都必须是大于 `0` 的有限数字。

| 字段 | 含义 | 推荐起点 |
|---|---|---:|
| `width` | 逻辑画布宽度 | `320` |
| `height` | 逻辑画布高度 | `180` |
| `pixels-per-block` | 每个世界方块对应的逻辑像素数 | `100` |
| `distance` |  authored 画布距离参数 | `3` |

`offset.yml` 中的实际放置距离会与这里的 `distance`、`pixels-per-block` 共同换算，以尽量保持菜单的视觉大小。

---

## 5. Frontend：视觉元素

`frontend` 是 `元素 ID -> 元素配置` 的对象：

```yaml
frontend:
  panel:
    type: rectangle
    width: 260
    height: 140
    color: '#111827'
    opacity: 235
```

### 5.1 所有视觉元素的公共字段

```yaml
type: rectangle
offset:
  x: 0
  y: 0
  z: 0
rotation:
  x: 0
  y: 0
  z: 0
scale:
  x: 1
  y: 1
visible: true
```

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `type` | 字符串 | 无 | 必填，见后文元素类型 |
| `offset` | 对象 | 三轴 `0` | 相对父组的位置 |
| `rotation` | 对象 | 三轴 `0` | 三轴旋转 |
| `scale` | 对象 | 各轴 `1` | 非模型元素用 x/y；item、block 可用 x/y/z |
| `visible` | 布尔 | `true` | 只控制视觉，不控制后端点击 |

静态 `scale` 的任一有效分量都不能为 `0`。需要隐藏元素时写 `visible: false`，不要用零缩放。

`group` 的变换会叠加到子元素。AI 应尽量让可点击按钮使用简单、可计算的变换；若需要精准交互，最好让按钮视觉和后端区域直接使用根画布坐标。

### 5.2 `group`：分组

允许字段：公共字段加 `children`。

```yaml
frontend:
  header-group:
    type: group
    offset:
      x: 0
      y: 55
      z: 1
    children:
      title:
        type: text
        content: '&f&l商店'
        size: 18
        alignment: center
```

规则：

- `children` 必须是对象，可以为空；
- 子元素 ID 仍要与整个菜单中的所有前端、后端 ID 全局唯一；
- 子元素坐标相对于组；
- group 只组织视觉，不会创建后端区域。

### 5.3 `rectangle`：实心矩形

```yaml
type: rectangle
width: 120
height: 34
color: '#2563EB'
opacity: 255
```

允许字段：公共字段加 `width`、`height`、`color`、`opacity`。

- `width`、`height`：必填且大于 `0`；
- `color`：默认 `'#FFFFFF'`，格式必须为 `'#RRGGBB'`；
- `opacity`：整数 `0..255`，默认 `255`。

### 5.4 `line`：线条

```yaml
type: line
width: 160
thickness: 2
color: '#60A5FA'
opacity: 220
```

允许字段：公共字段加 `width`、`thickness`、`color`、`opacity`。

- `width` 和 `thickness` 都必须大于 `0`；
- 颜色和透明度规则与 rectangle 相同。

### 5.5 `frame`：边框

```yaml
type: frame
width: 260
height: 140
thickness: 2
color: '#334155'
opacity: 255
```

允许字段：公共字段加 `width`、`height`、`thickness`、`color`、`opacity`。

约束：

```text
width > 0
height > 0
thickness > 0
2 × thickness < min(width, height)
```

### 5.6 `text`：文本

```yaml
type: text
content: '&e金币：&f%vault_eco_balance%'
size: 10
font: minecraft:default
opacity: 255
line-width: 180
alignment: center
update: 20
```

允许字段：公共字段加 `content`、`size`、`font`、`opacity`、`line-width`、`alignment`、`update`。

| 字段 | 默认 | 规则 |
|---|---:|---|
| `content` | 无 | 必填字符串；支持 ArcMenu 占位符和 PlaceholderAPI |
| `size` | `10` | 大于 `0` |
| `font` | `minecraft:default` | `namespace:path`，仅小写资源字符 |
| `opacity` | `255` | 整数 `0..255` |
| `line-width` | `200` | 大于 `0` 的整数 |
| `alignment` | `center` | `left`、`center` 或 `right` |
| `update` | `-1` | `-1` 或正整数 tick |

字体 ID 格式：

```regex
[a-z0-9_.-]+:[a-z0-9_./-]+
```

动态占位符需要随时间变化时，必须设置正数 `update`。例如余额每秒刷新一次可写 `update: 20`。

多行 text 必须让 YAML 产生真实换行。可使用双引号中的 `\n`，或使用块字符串；单引号中的 `\n` 会保留为反斜杠和字母 n：

```yaml
content: "&f第一行\n&7第二行"
```

或：

```yaml
content: |-
  &f第一行
  &7第二行
```

### 5.7 `image`：图片

推荐写法：

```yaml
type: image
source: /icons/coin.png
width: 24
height: 24
opacity: 255
update: -1
```

允许字段：公共字段加 `source`、`path`、`image`、`material`、`width`、`height`、`opacity`、`update`。

`source`、`path`、`image`、`material` 是兼容别名，必须且只能出现一个。AI 只生成规范字段 `source`。

图片路径规则：

- 必须以 `/` 开头；
- 指向 `plugins/ArcMenu/images/` 下的 PNG；
- 静态路径只使用小写字母、数字、下划线、点、斜杠和连字符；
- 禁止 `//`、`.` 路径段、`..` 路径段和目录逃逸；
- 静态图片必须真实存在，否则菜单集校验失败；
- 动态路径可以包含成对的 `%placeholder%`，运行时结果也必须解析为已存在的合法图片路径；
- `width`、`height` 可分别省略；省略的维度采用 PNG 自身像素尺寸；
- 若填写，`width`、`height` 必须大于 `0`；
- `opacity` 为 `0..255`；`update` 为 `-1` 或正整数。

动态图片示例：

```yaml
type: image
source: '/avatars/%player_name%.png'
width: 32
height: 32
update: 20
```

这要求每个可能解析出的玩家名图片已经存在，并符合小写路径限制。若玩家名可能含大写或特殊字符，不应直接采用这种方案。

资源限制：

- 最多 4096 张图片；
- 每张宽、高均为 `1..4096` 像素；
- 单张文件大小 `1 B..16 MiB`；
- 图片总大小不超过 `128 MiB`；
- 必须是有效 PNG；
- 新增或更改图片后使用 `/arcmenu reload all` 重建资源。

### 5.8 `item`：物品模型

```yaml
type: item
material: minecraft:diamond
context: GUI
scale:
  x: 1
  y: 1
  z: 1
```

允许字段：公共字段加 `material`、`context`。

- `material`：必填字符串，可指向原版或受支持的 CraftEngine 物品；
- `context` 默认 `GUI`；
- item 的 `scale` 支持 x/y/z，任一分量都不能为 `0`。

`context` 只允许以下值：

```text
GUI
HEAD
FIXED
GROUND
NONE
FIRSTPERSON_LEFTHAND
FIRSTPERSON_RIGHTHAND
THIRDPERSON_LEFTHAND
THIRDPERSON_RIGHTHAND
```

### 5.9 `block`：方块模型

```yaml
type: block
block-data: minecraft:stone
scale:
  x: 1
  y: 1
  z: 1
```

允许字段：公共字段加 `block-data`。

- `block-data`：必填字符串；
- block 的 `scale` 支持 x/y/z；
- AI 应显式写出 `z: 1`，不要依赖加载时自动补齐。

---

## 6. Backend：点击区域

后端是 `区域 ID -> 区域配置` 的对象：

```yaml
backend:
  buy-button:
    x: 0
    y: -35
    width: 120
    height: 34
    priority: 10
    tooltip:
      - '&f购买一颗钻石'
      - '&7价格：&e100 金币'
    update: 20
    condition: 'permission shop.buy && %vault_eco_balance% >= 100'
    actions:
      right:
        - 'take-money: 100'
        - 'give-item: material:minecraft:diamond,amount:1'
        - 'tell: &a购买成功。'
      shift-right:
        - 'tell: &eShift + 右键执行另一种操作。'
    deny:
      - 'tell: &c权限不足或余额不足。'
      - 'sound: minecraft:entity.villager.no-1-1'
```

### 6.1 区域字段

| 字段 | 类型 | 默认 | 规则 |
|---|---|---:|---|
| `x` | 数字 | `0` | 区域中心 x |
| `y` | 数字 | `0` | 区域中心 y |
| `width` | 数字 | 无 | 必填，大于 `0` |
| `height` | 数字 | 无 | 必填，大于 `0` |
| `priority` | 整数 | `0` | 重叠区域命中优先级，越大越优先 |
| `tooltip` | 字符串或字符串列表 | `[]` | 鼠标/触控指针悬停提示 |
| `update` | 整数 | `-1` | Tooltip 动态占位符刷新间隔 |
| `condition` | 字符串 | 无 | 整个区域的进入条件 |
| `actions` | 动作、动作列表或点击映射 | 空 | 点击动作 |
| `deny` | 动作或动作列表 | 空 | 区域 `condition` 不成立时执行 |

`condition` 控制整块区域。如果不成立，不执行 `actions`，改为执行区域级 `deny`。

### 6.2 当前可用的点击入口

规范生成方式：

```yaml
actions:
  right:
    - 'tell: &a普通右键'
  shift-right:
    - 'tell: &e潜行右键'
```

也可以让所有当前点击共享同一组动作：

```yaml
actions:
  all:
    - 'tell: &a已点击'
```

解析器能识别 `all`/`any`、`left`、`right`、`shift`、`shift-left`、`shift-right`、'middle'、'shift+middle'，也接受下划线别名
若不需要区分点击类型，`actions` 可直接是列表，等价于 `all`：

```yaml
actions:
  - 'tell: &a已点击'
  - 'sound: minecraft:ui.button.click-1-1'
```

### 6.3 条件动作组

在同一次点击或事件中，可以按条件分支：

```yaml
actions:
  right:
    - priority: 0
      condition: 'permission shop.vip'
      actions:
        - 'tell: &6VIP 购买通道'
      deny:
        - 'tell: &7你不是 VIP。'
    - priority: 10
      condition: '%player_world% == world'
      actions:
        - 'sound: minecraft:ui.button.click-1-1'
```

规范字段只有：

| 字段 | 含义 |
|---|---|
| `condition` | 可选条件；省略表示总是通过 |
| `priority` | 整数；越小越先执行，同值按文档顺序 |
| `actions` | 条件成立时的动作 |
| `deny` | 条件不成立时的动作 |

条件动作组至少要有 `actions` 或 `deny`。虽然解析器接受 `requirements`、`clicks` 等兼容别名，AI 只输出上表中的规范字段。

注意区分两个优先级：

- 后端区域的 `priority`：多个区域重叠时，越大越先命中；
- 条件动作组的 `priority`：同一反应中的多个组，越小越先执行。

### 6.4 Tooltip

```yaml
tooltip:
  - '&f传送菜单'
  - '&7右键打开'
update: -1
```

- 单个字符串表示一行；列表表示多行；
- 使用 `&` 颜色代码；
- 支持本文的占位符；
- 含动态值且需要持续变化时，设置正数 `update`；
- Tooltip 的位置、背景、换行和九宫格皮肤由全局 `tooltip.yml` 控制。

---

## 7. 条件语言

条件可用于：

- 后端区域的 `condition`；
- 条件动作组的 `condition`；
- 动作修饰符 `{condition=...}`；
- `{players=...}` 的在线玩家筛选。

### 7.1 基本条件

```text
true
false
permission shop.buy
perm shop.vip
not permission shop.admin
!permission shop.admin
```

权限条件中的权限名前导 `*` 会被忽略。规范写法仍是 `permission node`。

### 7.2 逻辑运算

```text
permission shop.use && %player_world% == world
permission shop.vip || permission shop.admin
!(permission shop.banned) && %player_x% >= 0
```

支持：

- `&&`：且；
- `||`：或；
- `!` / `not`：非；
- `(...)`：显式分组。

复杂条件必须加括号表达意图，不依赖读者猜测优先级。

### 7.3 集合条件

```text
all [permission shop.use; %player_world% == world]
any [%player_world% == world, %player_world% == world_nether]
```

- `all [...]`：所有子条件成立；
- `any [...]`：任一子条件成立；
- 列表不能为空；
- 支持逗号或分号分隔。

### 7.4 比较运算

支持：

```text
==
!=
>
<
>=
<=
is
is not
contains
```

示例：

```text
%vault_eco_balance% >= 100
%player_world% == world
%player_name% contains Alex
{data:tutorial-finished} is true
{meta:selected-page} != null
```

双方都能解析为数字时按数值比较；否则按不区分大小写的字符串比较。因此余额、坐标、计数等应与纯数字比较。

### 7.5 真值表达式的限制

条件也能接受某些裸表达式，但只建议用于占位符：

```text
%some_boolean_placeholder%
```

真值包括 `true`、`yes`、`on` 或非零数字。裸 `{data:key}` 不是可靠的布尔条件写法，应明确比较：

```text
{data:tutorial-finished} == true
```

不要写：

```text
{data:tutorial-finished}
```

可选的 `check ` 前缀会被识别，但 AI 不应生成多余前缀。

---

## 8. 占位符、参数与状态

### 8.1 展开顺序

字符串在运行时按以下顺序展开：

1. ArcMenu 状态函数；
2. `{0}`、`{1}` 等菜单参数；
3. ArcMenu 命名占位符；
4. 内置玩家占位符；
5. PlaceholderAPI（若已安装）。

后一步可以继续处理前一步生成的文本。AI 不应制造循环依赖。

### 8.2 状态作用域

| 作用域 | 读取 | 写入 | 生命周期 |
|---|---|---|---|
| META | `{meta:key}`、`{m:key}`、`%arcmenu_meta_key%` | `set-meta` / `remove-meta` | 每玩家、仅内存，重启丢失 |
| DATA | `{data:key}`、`{d:key}`、`%arcmenu_data_key%` | `set-data` / `remove-data` | 每玩家、持久化 |
| GLOBAL | `{globaldata:key}`、`{gdata:key}`、`{g:key}`、`%arcmenu_globaldata_key%` | `set-global-data` / `remove-global-data` | 全服共享、持久化 |

缺失状态读取为字符串 `null`。DATA 和 GLOBAL 保存到 `plugins/ArcMenu/data.properties`，写入会合并延迟约 20 tick 落盘。

设置多个键值：

```yaml
- 'set-meta: selected tab-1;opened true'
- 'set-data: tutorial-finished true;reward-claimed yes'
- 'set-global-data: event-state open'
```

删除参数是正则表达式，并对完整键名进行匹配：

```yaml
- 'remove-meta: selected'
- 'remove-data: tutorial-.*'
- 'remove-global-data: event-state'
```

正则由服务器执行。除非用户明确需要批量删除，AI 应生成精确键名，避免误删。

### 8.3 菜单参数

打开菜单时传入参数：

```yaml
- 'open: product-detail diamond 100'
```

目标菜单读取：

```yaml
content: '&f商品：&e{0} &7价格：&e{1}'
```

也可用：

```text
%arcmenu_args_0%
%arcmenu_args_1%
```

参数含空格时使用反引号包住单个参数：

```yaml
- 'open: product-detail `Diamond Sword` 1200'
```

反引号内的 `\s` 会转换为一个字面空格。缺失的 `{n}` 会保留原样，不会自动变成空字符串。

当前菜单参数还可以由动作改写：

```yaml
- 'set-args: profile `Alex The Builder` 3'
- 'clear-args:'
```

由菜单的自定义 `open-commands` 打开时，命令后面的参数会成为菜单参数。当前 `/arcmenu open <id>` 管理命令不会把额外参数传给菜单；需要参数时使用 `open:` 动作或菜单自己的开放命令。

### 8.4 内置玩家占位符

```text
%player_name%
%player_uuid%
%player_world%
%player_x%
%player_y%
%player_z%
```

坐标保留两位小数。其他 `%...%` 在安装 PlaceholderAPI 时交给 PlaceholderAPI 解析。

---

## 9. 动作语言

### 9.1 推荐结构

```yaml
actions:
  right:
    - 'tell: &a第一步'
    - 'sound: minecraft:ui.button.click-1-1'
    - 'open: next-menu'
```

每一项是一个动作字符串，形式为：

```text
动作名: 参数
```

无参数动作仍建议保留冒号：

```yaml
- 'close:'
- 'back:'
- 'return:'
```

解析器兼容动作别名、`_||_` 和 `&&&` 等紧凑序列，但 AI 必须输出规范动作名和 YAML 列表，以便审查和修改。

### 9.2 消息与展示动作

| 规范动作 | 参数 | 作用 |
|---|---|---|
| `tell` | 文本 | 给玩家发送聊天消息；字面 `\n` 或 `\r` 可分行 |
| `tellraw` | JSON 或 ArcMenu 富交互文本 | 发送 JSON/可交互消息 |
| `chat` | 文本 | 让玩家发出聊天消息 |
| `actionbar` | 文本 | 显示 ActionBar |
| `title` | 标题规格 | 显示标题和副标题 |
| `bossbar` | BossBar 规格 | 临时显示 BossBar |
| `sound` | 声音规格 | 向玩家播放声音 |

示例：

```yaml
- 'tell: &a欢迎，%player_name%！\n&7请选择一个项目。'
- 'actionbar: &e余额：%vault_eco_balance%'
- 'tellraw: {"text":"点击打开官网","color":"aqua","clickEvent":{"action":"open_url","value":"https://example.com"}}'
```

`tellraw` 若以 `{` 或 `[` 开头，会按 Adventure JSON 解析。否则可使用 ArcMenu 的内联交互片段：

```text
<显示文字@hover=悬停文字@suggest=/建议命令@command=/执行命令@url=https://example.com>
```

这不是 MiniMessage。单一片段可带一个或多个属性。

标题规格用反引号保护含空格文本：

```yaml
- 'title: `&6购买成功` `&f获得一颗钻石` 10 40 10'
```

顺序为：

```text
title subtitle fadeIn stay fadeOut
```

三个时间均为 tick；缺省值依次为 `15 20 15`。

BossBar 规格：

```yaml
- 'bossbar: `&e活动已开始` YELLOW PROGRESS 60'
```

顺序为：

```text
text color overlay stay
```

- `stay` 单位为 tick；
- 默认颜色 `WHITE`，默认样式 `PROGRESS`，默认持续 `15` tick；
- 分段样式可用 `SEGMENTED_6`、`SEGMENTED_10`、`SEGMENTED_12`、`SEGMENTED_20`。

声音规格：

```yaml
- 'sound: minecraft:ui.button.click-1-1'
- 'sound: minecraft:entity.player.levelup-0.8-1.2;minecraft:block.note_block.pling-1-1.5'
```

格式为 `声音名-音量-音调`，分号可分隔多个声音。音量必须不小于 `0`，音调会限制在 `0..2`。

### 9.3 命令、菜单与流程动作

| 规范动作 | 参数 | 作用 |
|---|---|---|
| `command` | 玩家命令 | 以玩家身份执行，前导 `/` 会去除 |
| `console` | 控制台命令 | 以控制台执行 |
| `op` | 命令 | 临时以 OP 权限执行，高风险 |
| `open` | 菜单 ID 和参数 | 打开另一个 ArcMenu 菜单或扩展路由 |
| `open-app` | `namespace:path` 和参数 | 打开已注册应用路由；只支持该规范拼写 |
| `connect` | 服务器名 | 通过代理连接到另一服务器 |
| `refresh` | 可选目标 | 刷新动态视觉/Tooltip |
| `play-animation` | 动画轨道 ID | 播放本菜单已绑定的 API 动画 |
| `stop-animation` | 动画轨道 ID | 停止轨道动画 |
| `delay` | tick | 让后续动作累计延迟 |
| `close` | 无 | 关闭菜单 |
| `back` | 无 | 返回上一菜单会话 |
| `return` | 无 | 终止当前动作链后续动作 |
| `reload-inventory` | 无 | 刷新玩家物品栏 |

示例：

```yaml
- 'command: spawn'
- 'console: give %player_name% minecraft:diamond 1'
- 'open: product-detail diamond 100'
- 'open-app: myplugin:profile %player_uuid%'
- 'connect: lobby'
- 'delay: 20'
- 'close:'
```

一个 `command`、`console` 或 `op` 参数内可用分号执行多条命令，但 AI 应优先拆成多条动作，减少转义和审计困难。

`open` 规则：

- 静态 ArcMenu 菜单 ID 必须存在于当前菜单目录；
- `namespace:path` 可作为外部扩展路由；
- 包含 `%` 或 `{` 的动态目标在运行时解析；
- ArcMenu 不支持 TrMenu 的 `open: menu:2` 容器页写法；每一页应是独立菜单文件，再用 `open` 跳转；
- `back` 返回当前会话的上一菜单；`close` 直接结束会话。

`refresh`：

```yaml
- 'refresh:'
- 'refresh: balance-text;buy-button'
```

- 无目标表示刷新所有动态节点；
- 静态目标必须是当前菜单存在的动态 `text` 元素 ID，或带动态 Tooltip 的后端区域 ID；
- 多目标用分号分隔；
- 动态目标允许占位符。

### 9.4 状态与参数动作

| 规范动作 | 参数格式 |
|---|---|
| `set-meta` | `key value;key value` |
| `remove-meta` | 一个或多个用 `;` 分隔的键正则 |
| `set-data` | `key value;key value` |
| `remove-data` | 一个或多个用 `;` 分隔的键正则 |
| `set-global-data` | `key value;key value` |
| `remove-global-data` | 一个或多个用 `;` 分隔的键正则 |
| `set-args` | 空格分隔参数，含空格项用反引号 |
| `clear-args` | 无参数 |

这些动作只做字符串赋值和删除，不支持算术表达式。不要生成 `set-data: count {data:count}+1` 并期待它自动计算。

### 9.5 经济与点券动作

| 规范动作 | 依赖 | 参数 |
|---|---|---|
| `give-money` | Vault Economy | 正数金额 |
| `take-money` | Vault Economy | 正数金额 |
| `set-money` | Vault Economy | 正数金额 |
| `give-points` | PlayerPoints | 正数，运行时转为整数 |
| `take-points` | PlayerPoints | 正数，运行时转为整数 |
| `set-points` | PlayerPoints | 正数，运行时转为整数 |

示例：

```yaml
- 'take-money: 100'
- 'give-points: 5'
```

金额必须大于 `0`。AI 不能仅凭一个前置余额条件就把“扣款”和“发物品”描述为数据库事务；动作按顺序执行，不提供跨插件原子回滚保证。

### 9.6 物品动作

#### `give-item` 与 `take-item`

格式：

```text
trait:value,trait:value;trait:value,trait:value
```

分号分隔多组物品，逗号分隔同一组的特征。支持的规范特征：

| 特征 | 含义 |
|---|---|
| `material` | 物品材质 |
| `amount` | 数量，发放时默认 1 并限制在 1..99 |
| `data` | 旧式 durability/data 匹配 |
| `model-data` | CustomModelData |
| `name` | 显示名称，支持 `&` 颜色 |
| `lore` | lore，字面 `\n` 分行 |
| `head` | 玩家头颅拥有者 |

发放示例：

```yaml
- 'give-item: material:minecraft:diamond,amount:3,name:&b任务钻石,lore:&7第一行\n&7第二行'
- 'give-item: material:minecraft:player_head,amount:1,head:%player_name%'
```

AI 必须为 `give-item` 显式提供 `material`；虽然缺省实现会落到 BEDROCK，但这不应当作正常配置。物品栏装不下的发放物会掉落到玩家位置。

移除示例：

```yaml
- 'take-item: material:minecraft:diamond,amount:3'
```

`take-item` 可以使用 `!trait` 反向匹配，但这类配置难审计，只有用户明确需要排除条件时才生成。多组移除按顺序处理，后续组失败时不保证回滚已经移除的前序物品。

#### `repair-item`

目标可用分号分隔：

```yaml
- 'repair-item: hand'
- 'repair-item: armor;offhand'
```

目标值：

```text
all
inv
armor
hand
mainhand
offhand
helmet
chestplate
leggings
boots
<数字槽位>
```

#### `enchant-item`

格式：

```text
目标 附魔 等级或等级范围
```

示例：

```yaml
- 'enchant-item: hand minecraft:sharpness 5'
- 'enchant-item: hand minecraft:unbreaking 1-3;helmet minecraft:protection 4'
```

范围为含两端的随机整数。附魔名可省略命名空间，但 AI 推荐写完整 `minecraft:...`。

### 9.7 输入捕获：`catcher`

当前跨版本实现只支持聊天输入 `CHAT`。规范结构：

```yaml
actions:
  right:
    - catcher:
        rename-stage:
          type: CHAT
          start:
            - 'tell: &e请在聊天框输入新名称；输入 cancel 取消。'
          cancel:
            - 'tell: &7已取消输入。'
          end:
            - 'tell: &a已收到：{meta:input}'
```

- `catcher` 下至少一个阶段；阶段 ID 是普通字符串键；
- `type` 当前只能为 `CHAT`；
- 使用规范字段 `start`、`cancel`、`end`；
- `before` 是 `start` 的兼容别名，`after` 是 `end` 的兼容别名，AI 不生成别名；
- 解析器还接受 `display`、`name`、`title`、`content`、`book`、`item-left`、`item-right`，但当前运行时模型没有对应语义，AI 不应生成；
- 捕获到的聊天输入写入玩家 META：通用键为 `{meta:input}`，阶段专属键为 `{meta:input-<stage-id>}`，例如 `{meta:input-rename-stage}`；
- 开始一个新的 catcher 时会清理旧的 `input` 与 `input-*` META；
- 玩家输入 `cancel`、`quit`、`end` 或 `q`（不区分大小写）会进入 `cancel`，这些词不能作为普通输入提交。

流程控制动作：

```yaml
- 'retype:'
- 'return:'
```

`retype` 让输入流程重新进入；`return` 停止当前动作链。

### 9.8 动作修饰符

动作字符串可带以下修饰符：

| 修饰符 | 作用 |
|---|---|
| `{delay=20}` 或 `<delay=20>` | 该动作延迟 20 tick |
| `{chance=0.5}` | 50% 概率执行，范围 `0..1` |
| `{condition=...}` | 条件成立才执行；只支持花括号形式 |
| `{players}` | 对所有在线玩家执行 |
| `{players=condition}` | 对满足条件的在线玩家执行 |

示例：

```yaml
- 'tell: &a延迟消息 {delay=20}'
- 'give-item: material:minecraft:diamond,amount:1 {chance=0.1}'
- 'tell: &6活动公告 {players=permission event.notice}'
```

修饰符会从动作正文中移除。不要在同一动作中重复同类修饰符。`{players}` 会扩大影响范围，只在用户明确要求广播或批量执行时生成。

---

## 10. 菜单事件

`events` 只允许 `open` 和 `close`：

```yaml
events:
  open:
    - 'sound: minecraft:ui.button.click-0.7-1.2'
    - 'set-meta: current-menu example-menu'
  close:
    - 'remove-meta: current-menu'
```

两者都使用与点击相同的反应结构，因此也可以使用条件动作组：

```yaml
events:
  open:
    - condition: '{data:first-open} != true'
      actions:
        - 'tell: &e这是你第一次打开菜单。'
        - 'set-data: first-open true'
  close: []
```

事件中调用 `open`、`back`、`close` 时要小心递归切换。AI 应避免让 A 的 `open` 事件打开 B、B 的 `open` 又打开 A。

---

## 11. 一套可直接落盘的完整示例

这套示例只依赖 ArcMenu 本体及其必需依赖，不使用图片、Vault、PlayerPoints、PlaceholderAPI 或 CraftEngine。它包含：

- 唯一主菜单；
- 自定义开放命令；
- 前端背景、边框、文本、物品模型和按钮；
- 后端点击区域、Tooltip、权限条件和拒绝动作；
- 页面跳转、参数、返回与持久状态。

如果把它加入已有菜单库，先把 `welcome.yml` 的 `main-menu` 改为 `false`，或者取消旧主菜单；整个目录必须恰好一个主菜单。

### 11.1 `plugins/ArcMenu/menus/welcome.yml`

```yaml
schema-version: 1
id: welcome
permission: ''
open-commands:
  - menu
main-menu: true

canvas:
  width: 320
  height: 180
  pixels-per-block: 100
  distance: 3

frontend:
  page-background:
    type: rectangle
    width: 280
    height: 150
    color: '#0F172A'
    opacity: 245
    offset:
      x: 0
      y: 0
      z: 0

  page-frame:
    type: frame
    width: 280
    height: 150
    thickness: 2
    color: '#334155'
    opacity: 255
    offset:
      x: 0
      y: 0
      z: 1

  page-title:
    type: text
    content: '&f&l欢迎使用 ArcMenu'
    size: 16
    font: minecraft:default
    opacity: 255
    line-width: 240
    alignment: center
    update: -1
    offset:
      x: 0
      y: 56
      z: 2

  player-info:
    type: text
    content: '&7玩家：&f%player_name%  &7世界：&f%player_world%'
    size: 8
    font: minecraft:default
    opacity: 230
    line-width: 240
    alignment: center
    update: 20
    offset:
      x: 0
      y: 37
      z: 2

  guide-card-bg:
    type: rectangle
    width: 112
    height: 58
    color: '#1D4ED8'
    opacity: 255
    offset:
      x: -64
      y: -8
      z: 2

  guide-card-icon:
    type: item
    material: minecraft:book
    context: GUI
    scale:
      x: 1
      y: 1
      z: 1
    offset:
      x: -94
      y: -8
      z: 3

  guide-card-label:
    type: text
    content: "&f&l阅读规则\n&b打开详细页面"
    size: 9
    font: minecraft:default
    opacity: 255
    line-width: 72
    alignment: center
    update: -1
    offset:
      x: -48
      y: -8
      z: 3

  reward-card-bg:
    type: rectangle
    width: 112
    height: 58
    color: '#047857'
    opacity: 255
    offset:
      x: 64
      y: -8
      z: 2

  reward-card-icon:
    type: item
    material: minecraft:emerald
    context: GUI
    scale:
      x: 1
      y: 1
      z: 1
    offset:
      x: 34
      y: -8
      z: 3

  reward-card-label:
    type: text
    content: "&f&l记录进度\n&a需要示例权限"
    size: 9
    font: minecraft:default
    opacity: 255
    line-width: 72
    alignment: center
    update: -1
    offset:
      x: 80
      y: -8
      z: 3

  close-button-bg:
    type: rectangle
    width: 70
    height: 22
    color: '#7F1D1D'
    opacity: 255
    offset:
      x: 0
      y: -57
      z: 2

  close-button-label:
    type: text
    content: '&f关闭'
    size: 8
    font: minecraft:default
    opacity: 255
    line-width: 60
    alignment: center
    update: -1
    offset:
      x: 0
      y: -57
      z: 3

backend:
  open-guide:
    x: -64
    y: -8
    width: 112
    height: 58
    priority: 10
    tooltip:
      - '&f阅读服务器规则'
      - '&7右键打开下一页'
    update: -1
    actions:
      right:
        - 'sound: minecraft:ui.button.click-0.8-1.2'
        - 'open: guide-detail rules'
      shift-right:
        - 'tell: &7这里不需要潜行，普通右键即可。'

  save-progress:
    x: 64
    y: -8
    width: 112
    height: 58
    priority: 10
    tooltip:
      - '&f记录教程进度'
      - '&7需要权限：arcmenu.example.reward'
    update: -1
    condition: 'permission arcmenu.example.reward'
    actions:
      right:
        - 'set-data: tutorial-finished true'
        - 'tell: &a教程进度已保存。'
        - 'sound: minecraft:entity.player.levelup-0.8-1.2'
    deny:
      - 'tell: &c你没有 arcmenu.example.reward 权限。'
      - 'sound: minecraft:entity.villager.no-1-1'

  close-menu:
    x: 0
    y: -57
    width: 70
    height: 22
    priority: 10
    tooltip: '&c关闭菜单'
    update: -1
    actions:
      right:
        - 'close:'

events:
  open:
    - 'set-meta: current-menu welcome'
  close:
    - 'remove-meta: current-menu'
```

### 11.2 `plugins/ArcMenu/menus/guide-detail.yml`

```yaml
schema-version: 1
id: guide-detail
permission: ''
open-commands: []
main-menu: false

canvas:
  width: 320
  height: 180
  pixels-per-block: 100
  distance: 3

frontend:
  detail-background:
    type: rectangle
    width: 280
    height: 150
    color: '#111827'
    opacity: 245
    offset:
      x: 0
      y: 0
      z: 0

  detail-frame:
    type: frame
    width: 280
    height: 150
    thickness: 2
    color: '#475569'
    opacity: 255
    offset:
      x: 0
      y: 0
      z: 1

  detail-title:
    type: text
    content: '&f&l指南页面：&e{0}'
    size: 15
    font: minecraft:default
    opacity: 255
    line-width: 240
    alignment: center
    update: -1
    offset:
      x: 0
      y: 54
      z: 2

  detail-body:
    type: text
    content: "&7一、尊重其他玩家。\n&7二、不要利用漏洞。\n&7三、遇到问题请联系管理员。"
    size: 9
    font: minecraft:default
    opacity: 245
    line-width: 230
    alignment: left
    update: -1
    offset:
      x: -108
      y: 15
      z: 2

  state-text:
    type: text
    content: '&7教程完成状态：&f{data:tutorial-finished}'
    size: 8
    font: minecraft:default
    opacity: 230
    line-width: 220
    alignment: center
    update: 20
    offset:
      x: 0
      y: -30
      z: 2

  back-button-bg:
    type: rectangle
    width: 88
    height: 24
    color: '#334155'
    opacity: 255
    offset:
      x: 0
      y: -57
      z: 2

  back-button-label:
    type: text
    content: '&f返回上一页'
    size: 8
    font: minecraft:default
    opacity: 255
    line-width: 80
    alignment: center
    update: -1
    offset:
      x: 0
      y: -57
      z: 3

backend:
  return-to-previous:
    x: 0
    y: -57
    width: 88
    height: 24
    priority: 10
    tooltip:
      - '&f返回上一页'
      - '&7不会直接关闭菜单'
    update: -1
    actions:
      right:
        - 'sound: minecraft:ui.button.click-0.8-1.0'
        - 'back:'

events:
  open:
    - 'set-meta: current-menu guide-detail'
  close:
    - 'remove-meta: current-menu'
```

### 11.3 示例落盘后的验证

```text
/arcmenu validate
/arcmenu reload
/arcmenu open welcome
```

然后测试：

1. 普通右键“阅读规则”；
2. 下一页的 `{0}` 应显示 `rules`；
3. 点击“返回上一页”；
4. 无权限点击“记录进度”应执行 `deny`；
5. 授予 `arcmenu.example.reward` 后再次点击，应持久化状态；
6. 关闭再打开，状态仍应存在。

---

## 12. `animations.yml`：动画知识库

### 12.1 根结构

```yaml
schema-version: 1

transitions: {}
tracks: {}
menus: {}
```

根字段只允许 `schema-version`、`transitions`、`tracks`、`menus`。`schema-version` 只能为 `1`。动画、过渡及菜单绑定 ID 使用小写 ID 规则。

动画分两类：

- `transitions`：菜单进入、退出和页面切换时自动执行的整体过渡；
- `tracks`：针对某个前端元素某个属性的关键帧动画。

后端点击区域不跟随轨道动画。菜单进入/切换/退出过渡进行期间，交互会暂停；普通元素轨道播放时不要假设点击框会移动。

### 12.2 Transitions

```yaml
transitions:
  slide-soft:
    enter:
      duration: 8
      easing: ease-out
      offset:
        x: -36
        y: 0
        z: 0
      scale:
        x: 0.9
        y: 0.9
    exit:
      duration: 6
      easing: ease-in
      offset:
        x: 0
        y: -28
        z: 0
      scale:
        x: 0.92
        y: 0.92
    switch:
      duration: 7
      easing: ease-out
      offset:
        x: 36
        y: 0
        z: 0
      scale:
        x: 0.94
        y: 0.94
```

每个过渡必须至少包含 `enter`、`exit`、`switch` 之一。每个阶段只允许：

| 字段 | 规则 |
|---|---|
| `duration` | 整数 `1..72000` tick |
| `easing` | `linear`、`ease-in`、`ease-out`、`ease-in-out` |
| `offset` | x/y/z，缺省为 0，绝对值不超过 100000 |
| `scale` | x/y，缺省为 1，非 0，绝对值不超过 1000 |

### 12.3 Tracks

```yaml
tracks:
  title-pulse:
    target: detail-title
    property: opacity
    duration: 24
    easing: ease-in-out
    loop: ping-pong
    trigger: open
    keyframes:
      - at: 0
        value: 120
      - at: 1
        value: 255

  button-bob:
    target: back-button-bg
    property: offset
    duration: 10
    easing: ease-in-out
    loop: ping-pong
    trigger: api
    keyframes:
      - at: 0
        value:
          x: 0
          y: -57
          z: 2
      - at: 1
        value:
          x: 0
          y: -52
          z: 2
```

轨道字段：

| 字段 | 规则 |
|---|---|
| `target` | 目标前端元素 ID |
| `property` | `offset`、`rotation`、`scale`、`content`、`opacity` |
| `duration` | 整数 `1..72000` tick |
| `easing` | 与 transition 相同 |
| `loop` | `once`、`repeat`、`ping-pong` |
| `trigger` | `open` 或 `api` |
| `keyframes` | 至少两个严格递增关键帧 |

关键帧规则：

- `at` 在 `0..1`；
- 第一帧必须精确为 `0`；
- 最后一帧必须精确为 `1`；
- 中间帧必须严格递增，不可重复；
- `offset`、`rotation` 的值为 x/y/z 对象，省略分量按 0；
- `scale` 的值至少明确写 x/y，非 0；只有 item/block 目标可以使用 z；
- `content` 的值为字符串，且目标必须是 text；
- `opacity` 的值为整数 `0..255`，且目标必须是 text。

同一菜单中，不能绑定两个 `trigger: open`、且目标和属性完全相同的轨道。

### 12.4 菜单绑定

```yaml
menus:
  guide-detail:
    transition: slide-soft
    tracks:
      - title-pulse
      - button-bob
```

- `menus` 的键必须是已存在菜单 ID；
- `transition` 必须是已定义过渡 ID；
- `tracks` 中每个轨道必须存在且不能重复；
- `trigger: open` 的轨道打开菜单时自动播放；
- `trigger: api` 的轨道用菜单动作手动播放：

```yaml
- 'play-animation: button-bob'
- 'stop-animation: button-bob'
```

静态动作目标必须是当前菜单已绑定的轨道。动态动画 ID 可以包含占位符，但最终仍需解析为合法、已绑定的轨道。

### 12.5 可与完整示例配套的 `animations.yml`

```yaml
schema-version: 1

transitions:
  page-slide:
    enter:
      duration: 8
      easing: ease-out
      offset:
        x: -30
        y: 0
        z: 0
      scale:
        x: 0.94
        y: 0.94
    exit:
      duration: 6
      easing: ease-in
      offset:
        x: 0
        y: -24
        z: 0
      scale:
        x: 0.96
        y: 0.96
    switch:
      duration: 7
      easing: ease-out
      offset:
        x: 30
        y: 0
        z: 0
      scale:
        x: 0.96
        y: 0.96

tracks:
  guide-title-pulse:
    target: detail-title
    property: opacity
    duration: 30
    easing: ease-in-out
    loop: ping-pong
    trigger: open
    keyframes:
      - at: 0
        value: 140
      - at: 1
        value: 255

menus:
  welcome:
    transition: page-slide
    tracks: []
  guide-detail:
    transition: page-slide
    tracks:
      - guide-title-pulse
```

---

## 13. Editor 模板 YAML

模板文件可用 `.yml` 或 `.yaml`，基本结构：

```yaml
schema-version: 1
id: action-card
frontend:
  card:
    type: group
    offset:
      x: 0
      y: 0
      z: 1
    children:
      card-background:
        type: rectangle
        width: 110
        height: 55
        color: '#365E91'
        opacity: 255
      card-label:
        type: text
        content: '&f按钮卡片'
        size: 8
        font: minecraft:default
        opacity: 255
        line-width: 90
        alignment: center
        update: -1
        offset:
          x: 0
          y: 0
          z: 1
```

模板根对象只允许：

- `schema-version`：只能为 `1`；
- `id`：模板 ID；
- `frontend`：必须恰好有一个根元素，且该根元素必须是 `type: group`。

模板会被 Editor 复制/实例化到菜单中，不是运行时 `include`，也不会与源模板保持联动。实例化时会给 ID 加前缀以避免冲突；最终元素 ID 长度不能超过 128。模板只封装前端视觉，不自动生成与按钮对应的后端区域。

---

## 14. `config.yml`：全局输入配置

规范完整示例：

```yaml
language:
  default: zh_CN
  follow-player-locale: true

shortcuts:
  shift-f: true

mouse:
  policy: player-choice
  default: touch
  sample-rate: 40
  cursor:
    interpolation-ticks: 0
    sensitivity-x: 2.0
    sensitivity-y: 2.0
    clamp-margin: 3.0
    size: 5.0
    z: 5.0
```

### 14.1 Language

| 字段 | 规则 |
|---|---|
| `language.default` | 默认 locale，必须有对应 `languages/<locale>.yml` |
| `language.follow-player-locale` | 布尔值；是否按客户端 locale 选择玩家提示语言 |

内置常见语言为 `zh_CN`、`en_US`。自定义 locale 文件名格式为两到三个小写语言字母、下划线、两个大写地区字母，例如 `ja_JP.yml`。语言文件用于 ArcMenu 自身提示，不会自动翻译菜单 `content` 或 Tooltip。

### 14.2 快捷键

```yaml
shortcuts:
  shift-f: true
```

`shift-f: true` 允许玩家用 Shift+F 切换唯一主菜单；设为 `false` 会保留原本的副手交换行为。若菜单目录没有且仅有一个 `main-menu: true`，快捷入口无法形成明确目标。

### 14.3 输入策略

`mouse.policy` 只生成：

```text
player-choice
force-touch
force-mouse
```

- `player-choice`：玩家可用 `/arcmenu mode touch|mouse` 选择；
- `force-touch`：强制触控/准星式指向；
- `force-mouse`：强制鼠标指针模式。

`force-crosshair`、`force-cursor` 等旧值仍有兼容读取，但 AI 不应生成。`mouse.default` 只生成 `touch` 或 `mouse`。

Cursor 字段限制：

| 字段 | 规则 |
|---|---|
| `sample-rate` | 整数 `20..60` |
| `interpolation-ticks` | 整数 `0..2` |
| `sensitivity-x` | 有限数字 `0.05..50` |
| `sensitivity-y` | 有限数字 `0.05..50` |
| `clamp-margin` | 有限数字且不小于 `0` |
| `size` | 有限数字且大于 `0` |
| `z` | 任意有限数字 |

鼠标图片固定为：

```text
plugins/ArcMenu/images/mouse/mouse.png
plugins/ArcMenu/images/mouse/choose.png
```

兼容字段 `glyph`、`color`、`opacity` 会被接受但已没有运行时语义，AI 不生成。

---

## 15. `offset.yml`：全局屏幕放置

规范示例：

```yaml
touch:
  distance: 0.65
  offset:
    x: 0.0
    y: 0.0
    z: 0.0

mouse:
  distance: 0.65
  offset:
    x: 0.0
    y: 0.0
    z: 0.0
```

根对象只允许 `touch` 和 `mouse`。每个模式允许：

- `distance`：`0.1..16`，默认 `0.65`；
- `offset.x/y/z`：各为 `-16..16` 的有限数字，默认 `0`。

方向：

- x 正数向右；
- y 正数向上；
- z 正数朝向玩家。

这份文件影响所有菜单，也影响通过鼠标模式打开的 Editor。ArcMenu 会根据菜单的 `pixels-per-block`、菜单 authored `distance` 与这里的实际放置距离换算缩放，目标是改变距离时仍保持接近一致的视觉大小。

---

## 16. `tooltip.yml`：全局 Tooltip 样式

### 16.1 基础样式

```yaml
touch:
  offset:
    x: 6
    y: 4.27
    z: 5
  anchor: top-left
  wrap: false
  size: 3.0
  line-width: 180
  background: '#D0101010'

mouse:
  offset:
    x: 6
    y: 4.27
    z: 5
  anchor: top-left
  wrap: false
  size: 4.375
  line-width: 180
  background: '#D0101010'
```

若省略 `mouse`，鼠标样式完整继承 `touch`。AI 为清晰起见可显式写出两节。

基础字段：

| 字段 | 规则 |
|---|---|
| `offset.x/y/z` | 有限数字 |
| `anchor` | `top-left`、`top-right`、`bottom-left`、`bottom-right` |
| `wrap` | 布尔值；`false` 时 backend tooltip 列表一项固定一行 |
| `size` | 大于 `0` 的有限数字 |
| `line-width` | 大于 `0` 的整数 |
| `background` | 必须加引号的 `'#AARRGGBB'` |

这里的 `background` 已含 alpha；不要再添加 `opacity`。

### 16.2 九宫格 Skin

可选 `skin` 完整结构：

```yaml
touch:
  offset:
    x: 6
    y: 4.27
    z: 5
  anchor: top-left
  wrap: false
  size: 3.0
  line-width: 180
  background: '#D0101010'
  skin:
    background: /ui/tooltip-background.png
    frame: /ui/tooltip-frame.png
    border: 8
    padding:
      left: 8
      right: 8
      top: 8
      bottom: 8
    min-size:
      width: 24
      height: 24
    size-adjust:
      width: 0
      height: 0
    offset:
      x: 0
      y: 0
      z: -0.25
    scale:
      x: 1.0
      y: 1.0
    text-offset:
      x: 0
      y: 0
      z: 0
    seam-overlap:
      x: 0
      y: 0
    glyph-offset:
      x: 0
      y: 0
    column-offset:
      left: 0
      center: 0
      right: 0
    row-offset:
      top: 0
      center: 0
      bottom: 0
```

Skin 规则：

- `background`：必填的图片虚拟路径；
- `frame`：可选图片虚拟路径；
- `border`：大于 `0` 的整数；
- `padding.left/right/top/bottom`：非负整数；旧式 `x/y` 仅兼容读取，AI 不生成；
- `min-size.width/height`：至少 `2 × border + 1`；
- `size-adjust.width/height`：调整后也不能让最小尺寸小于 `2 × border + 1`；
- `offset`、`text-offset`：x/y 为菜单逻辑单位，z 为深度单位，均须有限；
- `scale.x/y`：大于 `0`；
- `seam-overlap.x/y`：非负；
- `glyph-offset.x/y`、`column-offset.*`、`row-offset.*`：有限数值微调。

`seam-overlap`、`glyph-offset`、列偏移和行偏移采用 Tooltip 字体像素，并随 `size` 与 `scale` 缩放。Skin 图片必须先放入 `images/`，再执行 `/arcmenu reload all`。

---

## 17. 语言文件

语言文件路径：

```text
plugins/ArcMenu/languages/<locale>.yml
```

作用是覆盖 ArcMenu 自己的命令反馈、错误提示和运行时提示。它不是菜单内容的多语言系统；菜单中的 `content`、Tooltip 和动作消息仍由菜单作者自行设计。

AI 修改语言文件时应：

- 保持现有键层级不变；
- 所有叶子值使用非空字符串；
- 只翻译值，不翻译键；
- 缺失键会走已加载的默认/内置回退，不要凭空发明业务键；
- 文件名使用合法 locale，例如 `zh_CN.yml`、`en_US.yml`。

---

## 18. 加载、校验与发布工作流

### 18.1 常用命令

| 命令 | 用途 |
|---|---|
| `/arcmenu validate` | 只校验菜单、动画、模板及相关配置；不发布，不写默认值 |
| `/arcmenu reload` | 校验并发布菜单、动画和配置；写入菜单默认值并关闭当前菜单会话；不重建图片资源 |
| `/arcmenu reload all` | 完整重载并重建图片/九宫格资源及模板；新增图片后使用 |
| `/arcmenu resources` | 重建资源包并校验当前文档 |
| `/arcmenu open <id>` | 打开指定菜单进行运行时测试 |
| `/arcmenu preview <id> frontend` | 管理员只读预览前端 |
| `/arcmenu preview <id> backend` | 管理员只读预览后端，点击只报告命中，不执行动作 |
| `/arcmenu list` | 查看已发布菜单 |
| `/arcmenu templates` | 查看模板 |
| `/arcmenu animations` | 查看动画 |
| `/arcmenu animate <id>` | 播放当前菜单绑定的 API 轨道 |
| `/arcmenu stop-animation <id>` | 停止轨道 |
| `/arcmenu mode touch\|mouse` | 玩家选择输入模式（策略允许时） |
| `/arcmenu close` | 关闭当前菜单 |
| `/arcmenu status` | 查看运行状态 |

### 18.2 推荐发布顺序

普通 YAML 修改：

```text
1. 备份现有文件
2. 写入或替换 .yml
3. /arcmenu validate
4. 修完所有错误后再次 validate
5. /arcmenu reload
6. /arcmenu preview <id> frontend
7. /arcmenu preview <id> backend
8. /arcmenu open <id> 做真实点击测试
```

涉及新增/变更图片或 Tooltip skin：

```text
1. 放入合法 PNG
2. 写 YAML
3. /arcmenu validate
4. /arcmenu reload all
5. 让客户端使用新资源包后测试
```

菜单目录采用整库事务式发布：任何一个菜单、静态跳转、开放命令或动画绑定校验失败时，新的菜单集不会部分上线，先前已发布的菜单集继续保留。不要误以为“只有坏文件没生效，其他文件已经更新”。

### 18.3 全目录级校验

单个 YAML 语法正确仍可能整库失败。AI 必须检查：

- 所有菜单 ID 唯一；
- 恰好一个 `main-menu: true`；
- 所有 `open-commands` 全局唯一且不与服务器命令冲突；
- 所有静态 `open` 菜单目标存在，或是合法扩展路由；
- 所有静态 `refresh` 目标存在且可刷新；
- 所有静态图片存在且符合资源约束；
- `animations.yml` 中菜单、过渡、轨道、目标元素的引用全部存在；
- 同一菜单中前端、嵌套组和后端 ID 全局唯一。

空白、只含空格或只含注释的菜单文件在 reload 流程中可能被按文件名补基础内容，但 AI 不应依赖自动补全。始终生成完整、可审查的文档。

---

## 19. AI 输出契约

### 19.1 生成新菜单

回答结构应为：

1. 一句话列出依赖和假设；
2. 文件路径；
3. 完整 YAML 代码块；
4. 若有多个文件，重复“路径 + 完整代码块”；
5. 最后给出 `validate`、`reload` 与测试命令；
6. 列出仍需用户提供的图片文件名或权限节点。

不要只输出局部片段，除非用户明确要求“只改这一段”。

### 19.2 修改已有菜单

AI 必须先读取完整旧文件及同目录相关菜单，再做以下检查：

- 保留不相关配置和注释；
- 不改变现有主菜单归属，除非用户要求；
- 不重复现有元素 ID 或开放命令；
- 调整视觉按钮坐标时同步检查后端区域；
- 修改/删除元素时同步检查动画 target 和 `refresh` 引用；
- 修改菜单 ID 时同步修改 `open`、动画菜单绑定和外部调用；
- 若无法看到整个目录，明确声明“只能做单文件语法审查，不能保证目录级引用和唯一性”。

### 19.3 修复报错

收到 ArcMenu 校验错误时：

1. 根据错误路径定位具体文件、对象和字段；
2. 只修复被证实的问题及其直接引用；
3. 返回完整修正文件或最小 diff，按用户要求选择；
4. 重新执行目录级检查；
5. 不通过删除条件、权限或动作来掩盖错误。

### 19.4 可直接交给模型的系统提示词

```text
你是 ArcMenu YAML 配置助手。你必须以《ArcMenu YAML 配置知识库》为唯一语法依据，不能套用 ChestCommands、DeluxeMenus、TrMenu 或其他菜单插件的字段。

生成规则：
1. 菜单使用 schema-version: 1，文件扩展名为小写 .yml。
2. 只使用知识库列出的根字段、元素类型、动作、条件和占位符；禁止发明能力。
3. 整个菜单目录必须恰好一个 main-menu: true。向现有目录追加时默认 false。
4. 前端视觉与后端点击区分别生成，后端使用根画布中心坐标，不继承前端变换或动画。
5. 当前只生成 right、shift-right 或 all 点击入口。
6. 菜单元素颜色写成带引号的 '#RRGGBB' 并单独写 opacity；Tooltip 背景写 '#AARRGGBB'。
7. update 只能为 -1 或正整数，不能为 0。
8. 动作用规范动作名和 YAML 列表，不使用 [action] 语法或紧凑分隔符。
9. console、op、经济、点券、物品和全体玩家动作只有在需求明确时才生成。
10. 输出前执行知识库中的完整自检。如果缺少目录上下文，要说明无法保证主菜单、命令、引用和 ID 的全局唯一性。

输出格式：先用一句话说明假设和外部依赖；然后按“文件路径 + 完整 YAML 代码块”输出每个文件；最后给出 validate、reload 和人工点击测试步骤。若用户要求只输出 YAML，把假设写成 YAML 注释，不要输出其他文字。
```

### 19.5 推荐的用户需求模板

```text
请为 ArcMenu 生成菜单。

场景：
菜单 ID：
是新菜单库还是追加到现有库：
是否主菜单：
开放命令：
画布尺寸：
页面与按钮：
每个按钮的普通右键动作：
每个按钮的 Shift+右键动作：
权限及拒绝提示：
需要显示的动态占位符：
图片路径（如果有）：
外部依赖（PAPI/Vault/PlayerPoints/CraftEngine/代理）：
是否需要动画：
现有菜单 ID、主菜单和开放命令列表：

请按 ArcMenu 知识库输出完整文件，并在输出前执行自检。
```

---

## 20. AI 最终自检清单

生成任何配置后逐项检查：

### 20.1 YAML 层

- [ ] 使用 2 空格缩进，没有 Tab。
- [ ] 没有重复键。
- [ ] 所有颜色都已加引号。
- [ ] 含冒号、`#`、特殊符号或动作表达式的字符串已正确引用。
- [ ] 布尔、整数、数字类型没有误写成不合适的字符串。
- [ ] 代码块中的内容是完整 YAML，不含解释性自然语言。

### 20.2 菜单根层

- [ ] 文件扩展名是小写 `.yml`。
- [ ] `schema-version` 为 `1`。
- [ ] 菜单 `id` 合法且全目录唯一。
- [ ] 整个菜单库恰好一个 `main-menu: true`。
- [ ] 开放命令不带 `/`、不重复、不与服务端命令冲突。
- [ ] `canvas` 四项均大于 0 且已明确填写。

### 20.3 前端层

- [ ] 每个元素都有合法 `type`。
- [ ] 前端、嵌套 group、后端 ID 全局唯一。
- [ ] width/height/thickness/size/line-width 均为正数。
- [ ] frame 厚度小于宽高的一半。
- [ ] 静态 scale 没有任何 0 分量。
- [ ] item/block 明确写了 scale.z。
- [ ] text 的 alignment 只用 left/center/right。
- [ ] font 为合法 namespace:path。
- [ ] image 只写 source，一个路径源，不混用别名。
- [ ] 静态图片已存在，路径小写且无遍历段。
- [ ] 动态文本或图片需要更新时，update 为合理正数。

### 20.4 后端层

- [ ] 每个可点击视觉按钮都有单独 backend 区域。
- [ ] 后端 x/y 是中心坐标，width/height 大于 0。
- [ ] 后端位置未错误套用前端 group 的相对坐标。
- [ ] 动画/旋转视觉不会被误认为点击区同步移动。
- [ ] 只生成 right、shift-right 或 all。
- [ ] 重叠区域 priority 已明确且符合预期。
- [ ] 区域 condition 不成立时有合适的 deny 反馈。
- [ ] Tooltip 动态值需要刷新时写了 update。

### 20.5 行为层

- [ ] 所有动作都是真实存在的规范动作。
- [ ] 动作按执行顺序排列。
- [ ] delay 单位是 tick。
- [ ] 条件有必要的括号和明确比较。
- [ ] 裸 `{data:key}` 已改为明确比较。
- [ ] `open` 的静态菜单目标存在，且没有使用 `menu:2` 容器页写法。
- [ ] `refresh` 静态目标是可刷新的 text 或 Tooltip 区域。
- [ ] `op`、console、广播、余额和物品副作用符合用户明确需求。
- [ ] 余额/物品组合没有被错误宣称为原子事务。
- [ ] catcher 只用 CHAT 和 start/cancel/end。
- [ ] remove-state 的参数被当作正则审查过。

### 20.6 动画与全局层

- [ ] 动画时长为 `1..72000`。
- [ ] keyframe 首尾为 0 和 1，中间严格递增。
- [ ] content/opacity 只指向 text。
- [ ] scale.z 只用于 item/block。
- [ ] 动画菜单、轨道、过渡、元素引用全部存在。
- [ ] API 轨道已绑定到调用它的菜单。
- [ ] 新增图片后提示使用 `/arcmenu reload all`。
- [ ] 普通 YAML 变更提示先 validate 再 reload。

---

## 21. 常见错误与修复

| 现象或错误 | 常见原因 | 修复 |
|---|---|---|
| 菜单文件完全没加载 | 使用了 `.yaml` 或大写扩展名 | 改为小写 `.yml` |
| “不支持的字段” | 字段拼写错误，或套用了其他插件语法 | 删除/改为本文规范字段 |
| 颜色后内容消失 | `#RRGGBB` 未加引号，被 YAML 当注释 | 写 `color: '#RRGGBB'` |
| 不能发布整个菜单集 | 任意一个文件、静态引用或动画绑定失败 | 查看第一处校验错误，修复后重新 validate |
| 主菜单错误 | 没有或有多个 `main-menu: true` | 整个目录保留且仅保留一个 |
| 开放命令注册失败 | 重复、带 `/`、非法字符或撞 Bukkit 命令 | 改用唯一的小写命令名 |
| 看得到按钮但点不到 | 没有 backend，或坐标按左上角理解 | 添加独立后端区域，x/y 写中心点 |
| 按钮动画后命中错位 | 后端不跟随视觉动画 | 保持交互视觉静止，或接受固定命中区并调整设计 |
| `visible: false` 后仍能点击 | visible 只影响视觉 | 用后端 condition 禁用，或移除该区域 |
| 左键动作不触发 | 当前运行时只运输右键点击 | 改为 right/shift-right/all |
| 动态文字不变化 | `update: -1` 且没有 refresh | 设置正 update，或在状态改变后执行 refresh |
| 图片找不到 | 路径大小写、扩展名、资源未重建或文件不存在 | 使用小写 PNG 路径并 `/arcmenu reload all` |
| 图片配置报 source 冲突 | 同时写了 source/path/image/material | 只保留 `source` |
| `update: 0` 报错 | 0 非法 | 改为 `-1` 或正整数 |
| 跳转页不存在 | 把页码写成 `menu:2`，或目标 ID 未建文件 | 每页独立 `.yml`，用真实菜单 ID 跳转 |
| `/arcmenu open` 参数没进入 `{0}` | 管理命令当前忽略额外参数 | 用 `open:` 动作或该菜单的开放命令传参 |
| `{data:key}` 条件异常 | 裸状态函数不适合作为真值条件 | 写 `{data:key} == true` 等明确比较 |
| money/points 无变化 | 对应可选依赖或经济服务不存在 | 安装并确认 Vault Economy / PlayerPoints |
| `consol` 无法识别 | 拼写错误 | 使用 `console` |
| `[console] command` 无法识别 | 使用了 TrMenu 动作前缀语法 | 改为 `'console: command'` |
| content 动画校验失败 | 目标不是 text | 把 target 改为 text 元素 |
| 动画动作找不到 | 轨道没绑定当前菜单，或 ID 拼错 | 在 `animations.yml -> menus` 绑定并核对 ID |
| Tooltip skin 变形 | border、padding、min-size 关系不合法 | 保证最小宽高至少 `2 × border + 1` |

---

## 22. 规范动作名速查

AI 生成时只使用本表左列；右列仅帮助读懂旧文件，不应优先输出。

| 规范名 | 常见兼容别名 |
|---|---|
| `tell` | message、msg、talk |
| `tellraw` | json |
| `command` | cmd、player、execute |
| `console` | 无；`consol` 明确非法 |
| `op` | operator |
| `chat` | send、say |
| `actionbar` | action |
| `title` | subtitle、send-title |
| `bossbar` | send-bossbar |
| `sound` | play-sound |
| `open` | gui、menu、trmenu |
| `open-app` | 无，只接受规范写法 |
| `connect` | bungee、server |
| `refresh` | update、icon-refresh、icon-update |
| `play-animation` | animation、animate |
| `stop-animation` | cancel-animation |
| `delay` | wait |
| `set-meta` | set-temp、set-var、set-variable |
| `remove-meta` | remove-temp、remove-var、rem-meta、del-meta |
| `set-data` | 复数兼容形式 |
| `remove-data` | rem-data、del-data |
| `set-global-data` | set-g-data |
| `remove-global-data` | remove-g-data、rem-g-data、del-g-data |
| `set-args` | set-arg、set-arguments |
| `clear-args` | clear-arg、del-args、rem-args |
| `reload-inventory` | reload-inv、rl-inventory |
| `give-money` | add-money、deposit-money、give-eco、give-coin |
| `take-money` | remove-money、withdraw-money、take-eco、take-coin |
| `set-money` | modify-money、set-eco、set-coin |
| `give-points` | give-point、add-points、deposit-points |
| `take-points` | take-point、remove-points、withdraw-points |
| `set-points` | set-point、modify-points |
| `give-item` | add-item |
| `take-item` | remove-item |
| `repair-item` | 复数兼容形式 |
| `enchant-item` | 复数兼容形式 |
| `retype` | repeat、re-enter、enter、type |
| `close` | shut、force-close、silent-close |
| `back` | 无 |
| `return` | break |

兼容别名用于迁移旧配置；新知识库和 AI 输出统一使用规范名，能显著降低歧义。

---

## 23. 最小可用菜单模板

当用户只要求“先生成一个能打开的空菜单”时，使用：

```yaml
schema-version: 1
id: new-menu
permission: ''
open-commands: []
main-menu: false

canvas:
  width: 320
  height: 180
  pixels-per-block: 100
  distance: 3

frontend:
  background:
    type: rectangle
    width: 280
    height: 150
    color: '#111827'
    opacity: 245
    offset:
      x: 0
      y: 0
      z: 0

  title:
    type: text
    content: '&f&lNew Menu'
    size: 16
    font: minecraft:default
    opacity: 255
    line-width: 240
    alignment: center
    update: -1
    offset:
      x: 0
      y: 55
      z: 1

backend: {}

events:
  open: []
  close: []
```

若这是一个全新的菜单目录，而不是追加菜单，则再把唯一入口菜单改成 `main-menu: true`。不要在未知目录上下文中默认设为 true。

---

## 24. 结论性约束

ArcMenu 配置最重要的不是“YAML 能解析”，而是四种一致性同时成立：

```text
字段与类型一致
        ↓
前端视觉与后端命中一致
        ↓
跨文件 ID、跳转、图片和动画引用一致
        ↓
动作副作用与服务器实际依赖一致
```

AI 如果无法取得全目录、图片清单或依赖状态，只能保证单文件语法和局部逻辑，不应声称配置已经可以整库发布。最终判断以 `/arcmenu validate` 的结果和实际右键测试为准。



## 25. 本仓联调实测：校验器报错速查（Town 生成器，2026-10-25）

以下均为 `ArcMenuApi.menus().reload()` 实测返回的错误原文与修法（生成器：`Minecraft-26_3-plugin/Town/arcmenu/MenuBuilder`）。

### 25.1 `xxx.tooltip: Must be a string or a list of strings`

- **现象**：backend 命中区的 `tooltip` 写成了对象（`tooltip: {tooltip: [...]}`，助手函数二次包裹）。
- **修法**：`tooltip` 字段直接放字符串或字符串数组；组装层拿到 `{tooltip:[...]}` 时先展平再挂。

### 25.2 `frontend.xxx.condition: Unsupported field`

- **现象**：给 frontend `text` 节点加 `condition` 想做文案切换。
- **根因**：frontend 文本节点在当前阶段不支持 `condition`；条件分支只作用于 backend 命中区。
- **修法**：文本用中性静态文案，把条件放在并排的两个 backend 命中区上（各自带 condition 与动作）。

### 25.3 `Duplicate element ID: xxx (must be unique across frontend, backend, and all groups)`

- **现象**：frontend 文本节点与 backend 命中区用了同一个 id（如 `b-leave`）。
- **根因**：id 的唯一性是**跨 frontend/backend/所有分组全局**的，不是各节内部唯一。
- **修法**：命名分层，如文本 `xxx-t`、命中区 `xxx`（本仓 `button()` 助手即是此约定）。

### 25.4 `xxx.submit: Must reference an existing backend click region`

- **现象**：Arcitx 文本输入节点的 `submit` 指向 `go-submit`，但按钮区域实际 id 是 `go`。
- **修法**：`submit` 必须精确指向一个**已存在且带 actions** 的 backend 命中区 id。

### 25.5 启动日志与 reload 报告的时序差

- **现象**：每次重启，ArcMenu 在 M4 资源包 / startup 阶段报上一轮的旧错误，随后 Town 重写菜单并 API reload 成功，日志看起来自相矛盾。
- **根因**：ArcMenu 自身 enable 早于 Town；它读到的是磁盘上**上一轮**生成的 yml。Town 的生成发生在自己 enable 阶段，之后 `reload()` 才是本轮文件的权威结果。
- **修法**：判断菜单是否合法只看 Town 打印的 reload 报告（"共 N 个，无错误"）；重启一次后 startup 阶段读到的就是已修正文件。

### 25.6 `Ambiguous plugin name 'Town' for files ...`（runServer 双 jar）

- **现象**：Folia 报插件名歧义：`build/libs/Town-xxx.jar` 与 `plugins/Town-xxx.jar` 同时存在。
- **根因**：`:Town:runServer` 会自动把 `build/libs` 的产物注入服务端 plugins；手动再拷贝一份同名 jar 就撞名。
- **修法**：用 runServer 测试时**不要**手动往 `run/plugins/` 放本插件 jar，让任务注入；只有用别的启动方式才手动部署。

### 25.7 Arcitx 输入实测需先按 Esc 才能提交（3.2，2026-10-25）

- **现象**：Arcitx 输入框里按 Enter、或直接点 `submit` 指向的按钮都无效果；先按 Esc 退出编辑态后再点才生效。
- **根因**：3.2 运行时的 Arcitx 编辑态会吞掉后续点击/回车，第一次交互只是结束编辑。
- **规避**：不用 Arcitx。用 `ArcMenuTextInputOptions.builder().title(...).maxLength(...)` + `api.textInput().request(plugin, player, options, listener)`，
  在 `onComplete` 里判断 `ArcMenuTextInputEndReason.SUBMITTED` 后直接执行业务（回调线程需自行 `Threads.forPlayer` 切回玩家区域线程）。

### 25.8 光标大小与跟手感是 ArcMenu 全局配置，与菜单缩放无关

- **现象**：菜单整体缩小（如 70%）后显得光标巨大、拖动不灵敏。
- **根因**：光标由 ArcMenu `config.yml` 的 `mouse.cursor.size`（逻辑高度）、`sensitivity-x/y`、`sample-rate` 控制，
  与各菜单的 `pixels-per-block` / 缩放完全独立。鼠标模式光标本身走客户端 Mod 本地渲染（最跟手），touch/vision 是另外两种可选模式。
- **规避**：按 70% 菜单的经验配 `size: 3.0`、`sensitivity-x/y: 3.0`、`sample-rate: 60`；策略 `player-choice` + `default: mouse`，
  插件侧不要每次打开菜单都 `setPointerMode` 强切，否则会覆盖玩家自选模式。

### 25.9 第三方插件只能增量合并全局 animations.yml

- **现象**：想给自家菜单加过渡/轨道动画，但 `plugins/ArcMenu/animations.yml` 是全库唯一文件，
  里面已有默认菜单的大量绑定（实测 4000+ 行）；整份覆盖会让其它菜单动画全丢。
- **规避**：用 YamlConfiguration 读取后只写自己的键（`transitions.<id>`、`tracks.<id>`、`menus.<菜单id>`），
  已存在则跳过；首次写入前把原文件备份成 `animations.town-backup.yml` 之类，再随 `menus().reload()` 生效。
  校验是整库事务式的，合并写错会导致整套菜单不发布，所以合并后必须看 reload 报告。
