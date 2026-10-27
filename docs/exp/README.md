# docs/exp/ — 经验之谈（坑与经验库）

这里沉淀开发与调试中踩过的坑、非显然的解决方案、值得复用的经验。
**本目录随 git 提交**，与 `docs/dev/` 规范互补：规范管"必须怎么做"，这里管"为什么、别再踩"。

## 何时必须写

- 排查超过半小时才定位的问题
- 因环境 / 版本 / 平台差异导致的坑（OS、JDK、依赖版本、网络、容器行为差异）
- 文档里没有、从源码或试错中得出的结论
- 踩过某个工具 / 框架的坑并找到规避方式

## 组织方式

- 按主题分文件：`paper-plugin.md`、`fabric-mod.md`、`build-and-repo.md` 等，按需新增
- 每条经验格式：

### 标题（一句话说清坑）

- **现象**：怎么表现出来的
- **根因**：为什么会这样
- **规避/解决**：正确做法
- **来源**：日期 + 任务 / 文件位置

- 经验稳定后，若属于"必须 / 禁止"级规则，提示沉淀到 `docs/dev/` 对应规范
  （规范引用本条，本条不删除——保留"为什么"）

## 条目

- [paper-plugin.md](./paper-plugin.md) — Paper/Folia 插件侧：ESC 按钮点击回调、材质兜底、时钟右键空气、位置冲突判定、软依赖的类加载隔离、dialog 物品正文尺寸
- [folia.md](./folia.md) — Folia 服务端：区域化调度器、背包/实体线程归属、`folia-supported`、反射兼容旧服务端
- [fabric-mod.md](./fabric-mod.md) — Fabric 模组侧：Mixin 不生效的排查与替代通道、注解桩手工编译
- [handbook-dialog.md](./handbook-dialog.md) — 双页手册：插件贡献与配置刷新、Dialog 内边距和小屏预算、逻辑坐标缩放、CE 字体合包时机、字库内存与笔画细节、翻页命中、客户端焦点框与居中
- [build-and-repo.md](./build-and-repo.md) — 构建与仓库环境：工具链 fork、下载镜像、绝对路径、产物可复现、Git URL 重写与认证
- [arcmenu-yml知识库.md](./arcmenu-yml知识库.md) — ArcMenu YAML 菜单配置知识库（AI 可直读的完整语法 / 动作 / 自检清单；基于 0.1.0 快照整理，运行时已升 3.2.0，以 <https://docs.myarc.icu> 为准）
- [town-transactions-migration.md](./town-transactions-migration.md) — 小镇支付与背包交付恢复、重叠迁移、CraftEngine 家具身份及异步投影清理
- [town-research.md](./town-research.md) — 科研跨政策到期分段计时、离线续研与星露谷经验接口共享类加载
- [town-vault-economy.md](./town-vault-economy.md) — Vault 调用意图与人工核对、经济来源隔离、可选 API 类加载及金额精度
- [town-menu-layout.md](./town-menu-layout.md) — Town 图标模型分类、ArcMenu 3.2 尺寸校准、余额边界与可读币名
- [mainmenu-arcmenu-migration.md](./mainmenu-arcmenu-migration.md) — 原生 YAML 主页、动态物品与空提示限制、配置不覆盖和 26.3 截图校准
- [mainmenu-sidecards.md](./mainmenu-sidecards.md) — 私有侧卡显示层、鼠标模式等效画布、稳定自动布局与潜行点击实测
- [arcmenu-item-angles.md](./arcmenu-item-angles.md) — UltimateShop-ArcMenu 1.0.0 的物品分类、GUI/FIXED、Y 轴旋转和深度参数，附字节码证据与手机界面验证边界
- [town-resource-packs.md](./town-resource-packs.md) — 小镇首次合包与托管、GitLab 原生分发、CE 配置阶段回执、地图图片锚点补偿
- [vehicles.md](./vehicles.md) — 双人载具：乘客退服删除根载具、Folia 停服上下文、分实体调度、碰撞坐标与模型/座位校准

- [wallet-local-vault.md](./wallet-local-vault.md) — Wallet 双来源路由、菜单监听器与分页、Vault 主币种、官网迁入提交边界、头像校验及反射入口修复

- [yudream-ui.md](./yudream-ui.md) — RmlUi 原生渲染、custom payload 字节边界、Unicode、热替换布局、GPU 回收、Arc 接管与 Wallet 交付恢复
