# MainMenu、Town、Arc 商店接管

本次范围按用户确认：**mcPlugin 仓库中的 MainMenu、Town 和商店菜单**。没有修改共用 `./run/` 的运营数据或钱包；实机操作均在隔离夹具中完成。

## 具体映射

| 原入口 | YudreamUI 接管 | 保留的业务 |
| --- | --- | --- |
| `/cd`、MainMenu 主页 | 12 项分页的自适应卡片，图标/名称/说明读取功能注册表；更多操作列出右键、Shift、中键 | 功能注册、显示隐藏、排序、原 ClickType 回调和权限 |
| MainMenu 其它 9 页 | 从已安装 YAML 提取信息、条件与服务端路由，重排为卡片/按钮 | 管理、占位符、自定义功能、位置和原输入流程 |
| Town 26 页 | 语义信息卡、服务端指令、输入、确认和刷新 | 镇名草稿、邀请、Town 权限、成立/扩张/交易等全部业务 |
| Town 领地地图 | 7×7 状态网格、原购买/放弃与中心移动动作 | 冻结中心坐标、相连/权限/费用检查和 Town 确认页 |
| 玩家 `/arcmenu open shop[-分类]` 或 `/arc open ...` | Wallet 系统商店 | 新目录的服务器报价、Wallet 扣款和持久交付 |

原生网格目前**没有地形贴图**。Arc 世界内实体、3D 透视、动画、声音、手机侧卡、CraftEngine 自定义物品组件和任意未知动作不会自动翻译。首页使用功能注册表，自定义 Arc 首页装饰和布局不作为原生页面布局。

## 如何接入，而非拦截原始网络

MainMenu `Beta_1_10_0` 与 Town `Beta_0_11_0` 新增各自的公开 `*PageRenderer` 服务。原插件发布 META 后，先让服务接收页面源、按玩家解析器和动作回调；接管成功后关闭 Arc 的显示会话，刷新识别新会话。返回 false 才继续原 Arc 渲染。

ArcMenu 的 BeforeOpenEvent 在当前 API 中不可取消，单靠监听事件无法可靠阻止旧显示。因此这里改的是仓库内原插件的显示接入点。直接由其它插件 API 打开的 Arc 页面、控制台给玩家执行的 Arc 命令，以及未经过这些入口的任意菜单，**不在全局接管范围**。

当前 Town 仍需要 ArcMenu API 来生成和解析原 META、26 页源及部分输入/地图上下文。MainMenu 在 Arc 不可用时可由原生首页服务接入，但该独立路径没有周期刷新，管理页面仍可能回落到原背包管理器；本次实机接管测试保留了 ArcMenu。关闭 `takeover.enabled` 后重启，可恢复原显示路径。

支持的迁移动作仅为 `close`、`command: town ...`、`open: mainmenu:...` 和纯文本 `tell:`。音效/动画/refresh 不进入新界面。条件仅支持比较及 `&&`；嵌套未知动作、任意表达式和其它执行器拒绝接管并记录一次警告。按钮激活时重新解析状态、条件与原业务权限，不接受客户端传来的命令文本。

10 个 MainMenu 模板与 26 个 Town 模板已完成动作/条件结构检查；实际交互验证见 [VALIDATION.md](./VALIDATION.md)，不能等同于 36 页全部业务穷举。

## 商店迁移产物

- 可安装目录：[migration/shop-from-arcmenu.yml](./migration/shop-from-arcmenu.yml)。它也是首次安装释放的默认 `shop.yml`。
- 来源和哈希：[migration/report.json](./migration/report.json)。8 页去重为 **25 项**：装备 4、材料 4、珍品 6、工具 6、武器 5；0 项跳过。
- 小型开发演示：[examples/shop-demo.yml](./examples/shop-demo.yml)。包含面包/火把等四项，仅供隔离验证。

逐项核对 Arc 的固定 Shift 左键购买路径：`take-money: 1*单价` 和 `give-item: material:材质,amount:1`；只在两者均明确、无未知资金动作时导入。去重键是材质+价格。原买价不变，付款统一改为 Wallet，不执行 Arc `take-money`。

当前不迁入出售、脚本折扣、限购、库存或任意自定义物品元数据；不能把已迁入购买目录理解为所有 Arc 经济行为等价。默认 `coin` 是待核对的 Wallet code，不保证与本服原 Vault 币种一致。

### 对现有服务器应用

1. 安装新版本 MainMenu、Town 和 YudreamUI，保留原 Arc / Wallet 数据。
2. 先在隔离环境运行 `/yui import-shop <本服Wallet币种>`。插件备份原 `shop.yml` 并生成 `shop.imported.yml` 和 `migration/<时间>/report.txt`；不会热替换运行中的报价。
3. 对照报告检查价格、币种和所有跳过项。该命令只读取 `plugins/ArcMenu/menus/shop/` 与 `languages/zh_CN.yml`。
4. 停服备份，将确认后的候选替换 `plugins/YudreamUI/shop.yml`，重启。现有配置不会自动覆盖。
5. 分别用带 Mod 和无 Mod 客户端验证 `/cd`、`/town`、商店、实际测试币购买。先处理旧待核对订单，再改钱包来源。

本次已在隔离夹具应用 25 项目录，以 TEST 映射测试币种。生产目录仍需使用实际本服 code；不会自动兑换余额或向官网发起迁入。
