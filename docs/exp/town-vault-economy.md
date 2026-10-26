# Town 的 Vault Economy 接入与恢复

### Vault 没有幂等业务号，不能直接沿用 Wallet 的自动重发

- **现象**：经济提供者已经扣钱、回调却抛错时，Town 无法区分未扣款与回复丢失；重发会重复扣费。
- **根因**：标准 Vault Economy 只提供账户、金额与结果，没有业务号查询或幂等转账接口；Town 数据与外部经济账本也不共享事务。
- **规避/解决**：调用前持久保存意图，成功后保存回执；同一业务号、账户、来源和金额的成功记录直接重放。明确拒绝保存为 REJECTED；已进入资金调用的异常、部分转移或成功回执写盘失败保留 PENDING，不再自动调用。管理员核对外部账本后标记 success / failed / retry。只有能够确认资金调用尚未发生的调度失败才自动清除意图重试；备份必须同时保存 Town 和经济提供者账本。
- **来源**：2026-10-26，Town Beta_0_7_0；`./Minecraft-26_3-plugin/Town/src/main/java/com/example/town/economy/VaultLedger.java`、对应 JUnit 与 Folia 重启联调。[Vault Economy API](https://github.com/MilkBowl/VaultAPI/blob/master/src/main/java/net/milkbowl/vault/economy/Economy.java)；实机已确认“扣 5 后丢回复”跨重启仍只扣一次，人工成功核对仅补本地镇库。

### 切换经济提供者需要隔离币种和未完成交易

- **现象**：将 `economy.provider` 从 Wallet 改成 Vault，或更换 Vault 提供者，旧商铺、镇库与待结算交易仍在；若只改查询余额入口，会把旧款项结算到另一套账本。
- **根因**：货币名称相同不代表账本相同，Vault 还允许服务晚注册或被高优先级注册替换。
- **规避/解决**：配置 wallet / vault 明确二选一；本次启动绑定首次可用的提供者，不热切换。Vault 内部币种包含注册插件和提供者名，交易 payload 保存经济来源；旧版无标记交易只归 Wallet。新交易拒绝跨来源币种，恢复前检查来源。配置切换仅在重启生效，旧款项在原来源结清，不自动转换余额或订单。
- **来源**：2026-10-26，Town `./Minecraft-26_3-plugin/Town/src/main/java/com/example/town/hook/`、`Payment.withEconomy` 与 `EconomyBindingTest`；实际 Vault 晚注册及 Wallet 同时存在时不回退联调。

### 可选 Vault API 应使用实际插件的类加载器并遵守账户线程

- **现象**：复制 Economy API 或从 Town 类加载器直接引用它，可能找不到可选类，或拿到与 ServicesManager 注册类型不同的 Class；异步 IO 线程直接调用经济插件也可能违反 Folia 线程要求。
- **根因**：Paper 插件有独立类加载边界，Vault 是可选依赖；Folia 的玩家状态属于实体区域，经济接口本身没有规定所有提供者的线程模型。
- **规避/解决**：只在选择 Vault 时从 Vault 实际类加载器反射取得标准 API，不在 Town jar 内复制类；通过对应 Class 查 ServicesManager。使用 UUID 的 OfflinePlayer 方法，在线账户切入玩家实体调度、离线账户切入全局调度，台账 IO 仍走异步。提供者本身也须兼容目标 Folia，不能仅凭 Vault 的支持声明推断。
- **来源**：2026-10-26，用户提供的 Vault `1.7.3-b0-folia`、Folia 26.3 build 5 与可控提供者的区域线程断言；`./Minecraft-26_3-plugin/Town/src/main/java/com/example/town/hook/VaultHook.java`。[PaperMC Folia 调度说明](https://docs.papermc.io/paper/dev/folia-support/)。

### 整数台账转为 Vault double 时应校验实际精度

- **现象**：Town 使用整数分，提供者可能只支持整数货币或一位小数；超大金额也可能在 double 转换时丢失分位。
- **根因**：Vault 使用 double 金额并通过 fractionalDigits 报告精度，调用成功并不自动证明实际转移额等于请求。
- **规避/解决**：调用前检查 fractionalDigits 与整数分到 double 的往返精度，不兼容时明确拒绝；成功返回的 amount 必须匹配请求，失败返回非零转移额也不能当作安全拒绝。使用提供者货币名称显示余额，内部来源 ID 用于持久隔离。
- **来源**：2026-10-26，`./Minecraft-26_3-plugin/Town/src/main/java/com/example/town/economy/VaultMoney.java`、`VaultLedgerTest`；[Vault EconomyResponse 定义](https://github.com/MilkBowl/VaultAPI/blob/master/src/main/java/net/milkbowl/vault/economy/EconomyResponse.java)。
