# Wallet 本地多币种、Vault 与官网一次性迁入

## 把未校验的玩家纹理直接拼入 skull 会让整页打开失败

- **现象**：用户截图出现 `skull must be a texture hash or valid skin Base64`，主页、应用入口无法正常使用。
- **根因**：旧桥接直接转发玩家 textures Base64，默认模板也使用玩家名字构建头像；离线身份、第三方认证或损坏纹理未必满足 ArcMenu 3.2 的材质约束。
- **规避/解决**：解析 Base64 JSON，仅接收官方 textures.minecraft.net 的 texture 哈希；无效、缺失和第三方认证 URL 回退普通玩家头。10 页公共头像改用 `mm-player-material`。已安装的 YAML 不会随 JAR 升级覆盖，必须明确提供新模板及备份替换步骤。
- **来源**：2026-10-26，MainMenu `Beta_1_7_0`；`./Minecraft-26_3-plugin/MainMenu/src/main/java/com/example/mainmenu/arcmenu/HeadMaterial.java`；Folia 26.3 / ArcMenu 3.2 实测默认头、无效纹理、有效格式哈希及动态应用头。

## Wallet 菜单隔离层的构造器参数必须与真实声明完全匹配

- **现象**：MainMenu 没有可用钱包入口，软依赖初始化未能构建功能。
- **根因**：PlaytimeLedgerReader 的构造参数声明为 Plugin，反射却查询 WalletPlugin；反射查找不会按子类型匹配。注销时也错误使用 wallet:main，而真实 ID 为 wallet:wallet。
- **规避/解决**：反射查询 Plugin.class；资产启停 Store 与 Vault 共用同一个实例；注销真实功能 ID。保留反射隔离，避免缺少 MainMenu 时在类验证阶段触发强依赖。
- **来源**：2026-10-26，Wallet `Beta_0_6_0`；`./Minecraft-26_3-plugin/Wallet/src/main/java/com/example/wallet/menu/WalletMainMenuHook.java`。

## Vault 只提供标准单主币种接口，不替插件保存钱

- **现象**：仅安装用户提供的 Folia Vault 后，经济读取没有提供者；另装经济插件会形成不同于原 Wallet 的账本。
- **根因**：Vault Economy 是服务接口；标准方法没有币种参数。多币种不能通过注册多个同接口服务来获得可选币种路由。
- **规避/解决**：Wallet 持久保存配置币种，通过 vault.currency 暴露其中一个；其它币种保持 WalletApi。反射使用 Vault 实际类加载器的 Economy / EconomyResponse，动态代理注册到 ServicesManager，不复制 API 类。提供者名称 Wallet/code 让 Town 的来源身份带上实际币种。Town 切换到 economy.provider=vault 并重启，旧 Wallet 待处理业务不会自动重放。
- **来源**：2026-10-26，用户附件 Vault 1.7.3-b0-folia 的完整 Economy 方法签名；`./Minecraft-26_3-plugin/Wallet/src/main/java/com/example/wallet/local/WalletVaultBridge.java`；Town 来源约束见 [town-vault-economy.md](./town-vault-economy.md)。

## 一次性迁入完成标记要与所有余额一起提交

- **现象**：逐玩家入账后再写 config 布尔值会在崩溃后重复入账；边读边写会在后续网络失败时留下半份迁入。
- **根因**：金额变化和独立完成标记不在同一次持久提交中；Vault 本身也没有业务号幂等能力。
- **规避/解决**：暂停本地资金写入，先读本服已知 UUID 的所有配置币种；验证身份、金额、精度及重复映射后，将 IMPORT 余额记录和完成信息放在同一条带长度/CRC 的追加记录，force 后才向调用者报告成功。启动只裁去不完整末尾，完整 CRC 错误停止加载。提交不确定时停止写入，重启后核对状态，不能作为安全拒绝反复调用。API 业务号与内部 account:/site-import: 命名空间隔离。
- **来源**：2026-10-26，Wallet `Beta_0_6_0`；`./Minecraft-26_3-plugin/Wallet/src/main/java/com/example/wallet/local/LocalLedger.java` 和 `./Minecraft-26_3-plugin/Wallet/src/test/java/com/example/wallet/local/`；迁入范围与运维边界见 [Wallet README](../../Minecraft-26_3-plugin/Wallet/README.md)。
