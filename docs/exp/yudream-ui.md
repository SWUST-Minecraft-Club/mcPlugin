# YudreamUI：原生界面与语义降级

## Custom payload 多写一层长度会让原始协议整体偏移

- **现象**：插件端收到的帧 magic 错误，所有握手/页面不可用。
- **根因**：Fabric custom payload 编解码已经提供边界；再按 ByteArray 编码前置 VarInt 长度，会与 Bukkit plugin messaging 的原始字节不一致。
- **规避/解决**：`UiPayload` 直接写/读剩余字节；应用层使用固定 20 字节头和有界分片，乱序、重复与边界由独立 Java 协议测试验证。
- **来源**：2026.10.27，`./fabric-26_3-mod/YudreamUI/versions/mc26_3/`、`protocol/`，真实 Folia/Fabric 联调。

## Java JNI 的 Modified UTF-8 不能直接作为 Unicode 字符串

- **现象**：中文之外的 Emoji 输入/状态保存出现异常或丢码。
- **根因**：JNI `GetStringUTFChars` 使用 Modified UTF-8，RmlUi 接收标准 UTF-8，补充平面字符表示不同。
- **规避/解决**：JNI 使用 UTF-16 chars 与标准 UTF-8 转换，保留 Unicode 码点；码点保存正确不代表所用字体覆盖该字形。
- **来源**：2026.10.27，`./fabric-26_3-mod/YudreamUI/native/bridge.cpp`；MinecraftMcp 中文/Emoji 提交测试。

## 热替换恢复滚动必须等待隐藏标签页完成布局

- **现象**：输入和标签保住了，当前隐藏/恢复页的滚动却回到 0。
- **根因**：新文档标签可见性恢复后尚未完成布局，内容高度未建立，此时设置 scrollTop 被裁为 0。
- **规避/解决**：恢复标签/控件状态、更新布局，再恢复滚动；原生回归测试与两组图片/字体真实热替换验证覆盖此顺序。
- **来源**：2026.10.27，`NativeEngineTest.hotReplacementRestoresScrollAfterHiddenTabLayout`；`./fabric-26_3-mod/YudreamUI/integration/verify.py`。

## 文本与纹理的实际边界要靠游戏截图确认

- **现象**：对话选项落在可视区域下方、长中文溢出；已释放的 UI 纹理仍可能被排队中的 GPU 帧引用。
- **根因**：Flex 容器缺少高度/min-width 约束，RmlUi 默认中文断行行为与网页预期不同；Minecraft 渲染提取与 GPU 完成不是同一时间。
- **规避/解决**：滚动父容器给定高度、min-height:0；中文使用 word-break:break-word，Flex 文本列 min-width:0。纹理回收等待游戏 GPU 完成栅栏，不以“下一 tick”当作完成。缩窗和截图均在真实 UiScreen 验证。
- **来源**：2026.10.27，`./fabric-26_3-mod/YudreamUI/runtime/src/main/resources/builtin/minecraft.rcss`、`McRenderer`；[RmlUi RCSS](https://mikke89.github.io/RmlUiDoc/pages/rcss.html)。

## Arc 的不可取消打开事件不能单独完成显示接管

- **现象**：监听旧菜单打开事件再开原生页面，旧 Arc 仍有机会继续打开。
- **根因**：当前 ArcMenuBeforeOpenEvent API 未提供取消契约。
- **规避/解决**：在本仓 MainMenu / Town 的显示调用点提供可选服务，先决定显示提供者，再打开原视图。业务指令和权限留在原插件；不把第三方配置规则直接提升为仓库规范。
- **来源**：2026.10.27，`./libs/arcmenu-api-3.2.0.jar` API 核对；`MenuPageRenderer`、`TownPageRenderer` 与隔离服接管测试。

## 第三方依赖启动失败不等于已经证明版本不兼容

- **现象**：TrMenu 3.12.6 在隔离服启动时缺 Kotlin210.Lazy，依赖仓库连接报 SSLHandshakeException。
- **根因**：动态依赖下载/引导未完成，未进入可用于验证完整菜单行为的状态。
- **规避/解决**：把桥标记实验/未验证，保留错误证据，验证不可用时 inventory 恢复；不能由此断言 TrMenu 与 Folia 26.3 不兼容，也不能把反射签名对上当作运行已通过。
- **来源**：2026.10.27，`./build/yudream-ui-research/trmenu/`、隔离夹具 `evidence/trmenu-bootstrap-original.log` 与 `TrMenuBridge`。

## 钱包扣款和背包发物无法作为同一持久事务

- **现象**：扣款后断线需要继续领取；交付中崩溃时无法仅看订单推断玩家有没有拿到物品。
- **根因**：Wallet 账本与 Minecraft 玩家存档由不同组件提交，没有共享事务。
- **规避/解决**：先持久化订单，再用固定业务号扣款，落盘 PAID 后交付；先写 DELIVERING 再变更背包并 saveData，最后 DELIVERED。DELIVERING 重启变 REVIEW，不自动重发；来源变化阻止自动扣款。币种和精度在建单前校验，避免配置错误留下无法确认的订单。
- **来源**：2026.10.27，`./Minecraft-26_3-plugin/YudreamUI/` ShopService / ShopOrders，订单恢复测试及真实测试 Wallet 扣款、满背包和余额不足验证。人工核对步骤见插件 README。

## 联调读取诊断快照与测试账本要等待对应的提交

- **现象**：点击成功却断言旧状态，或者 Wallet 已返回新余额但落盘文件仍是旧值。
- **根因**：诊断按秒生成；测试 Wallet 合并延迟写入；Town 也异步保存。固定短等待不能证明状态已更新。
- **规避/解决**：等待 revision、字段变化或持久文件预期值，而非只等待几个 tick；不为测试便利改用户的 Wallet 实现。MCP bearer token 不进入记录，只保留已清理的请求/结果。
- **来源**：2026.10.27，`./fabric-26_3-mod/YudreamUI/integration/verify.py`；本次隔离联调。
