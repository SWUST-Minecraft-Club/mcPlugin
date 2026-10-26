# ZIP 中的第三方依赖

## Vault 1.7.3-b0-folia

- 作者：cereal、Sleaker、mung3r；Folia 分支维护者：SirBlobman。
- [上游发布](https://github.com/SirBlobman/Vault-Folia/releases/tag/v1.7.3-folia)；[对应源码](https://github.com/SirBlobman/Vault-Folia/tree/3597d9e2ae19a33d73f2028d92c95714cc04da6f)；[源码 ZIP](https://github.com/SirBlobman/Vault-Folia/archive/3597d9e2ae19a33d73f2028d92c95714cc04da6f.zip)。
- 许可证：LGPL-3.0-or-later，完整文本见 [Vault-LICENSE.txt](./Vault-LICENSE.txt) 及其引用的 [GPL 3.0](./Vault-GPL-3.0.txt)。Vault 作为独立 JAR 分发，没有修改或嵌入本仓插件。
- 下载地址、版本、SHA-256 和源码提交固定在 [vault-folia.json](./vault-folia.json)。2026-10-26 已核对上游发布文件与用户提供的 Folia Vault 附件字节一致。

打包脚本默认从锁定地址下载；离线使用 `--vault-jar` 传入同一文件，仍校验哈希。依赖校验失败会中止构建。
本目录只提交元数据和许可文本，Vault 运行 JAR 不入库。ArcMenu premium、CraftEngine 与 Minecraft 服务端核心不在此更新 ZIP 内。
