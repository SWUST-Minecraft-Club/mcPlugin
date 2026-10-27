# Wallet 本地多币种、Vault 与官网一次性迁入

## 跳过 UUID 不一致玩家时要丢弃整人的暂存余额与身份占用

- **现象**：官网按名字回退命中另一个游戏 UUID 时，单个玩家的身份错误会中止整批迁入。若仅将抛错改成跳过，多币种处理中较早记录的官网身份仍可能错误阻止后续正常玩家。
- **根因**：整批校验把可隔离的单人 UUID 错误也当成全局故障；官网身份去重表在整人通过校验前就已写入。
- **规避/解决**：按用户确认的策略，未绑定或 UUID 不一致时跳过该玩家全部币种并即时记录警告，保留正常账户继续处理。先暂存整人的余额，全部通过后才登记身份去重表；有效玩家间的重复网站身份、网络及金额/精度错误仍整批中止。成功后一次性标记照常完成，跳过账户需另行核对补款；全部跳过则不消耗迁入机会。日志包含玩家名和双方 UUID，不能只打印其中一方。
- **来源**：2026-10-26，用户报错截图与明确要求「跳过加报错，不要影响整体进程」；Wallet `Beta_0_6_2` 的 `SiteMigrationTest` 覆盖跨币种丢弃、身份占用释放、后续失败时仍保留警告、全跳过可重试、重启后禁止重复导入。[Java 25 Map 契约](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Map.html)与 [Consumer 回调契约](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/function/Consumer.html)用于核对暂存与日志回调行为。

## 继承默认配置节不代表 getKeys 能枚举默认币种

- **现象**：保留旧官网 `config.yml` 升级 Wallet 后，本地启动提示「币种不存在或未启用：coin」；新安装使用相同 JAR 正常。
- **根因**：JavaPlugin 将 JAR 配置绑定为 `FileConfiguration` 的 defaults。旧文件没有 `currencies` 时，`getConfigurationSection` 会基于默认节创建一个空节，而默认未开启复制默认值，`getKeys(false)` 不包含继承的 `coin`；原先仅判断节是否为 null 的兜底失效。
- **规避/解决**：调用前用 `contains("currencies", true)` 检查文件中真实存在的节，缺少时使用默认币种；显式空节、自定义币种和禁用状态保持原样。回归测试要模拟 `setDefaults`，不能只测试独立的 `YamlConfiguration`。继续使用旧官网余额需显式配置 `backend: website`，`assets.yml` 启用列表不会定义本地币种。
- **来源**：2026-10-26，Wallet `Beta_0_6_1`；发布 JAR + Folia API `26.3.build.5-alpha` 复现；`./Minecraft-26_3-plugin/Wallet/src/test/java/com/example/wallet/WalletConfigTest.java`。

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
