# 2.0.0

## 新增

- 发布 License 离线验证 Starter，装配 `smart-license-core` 的验证引擎、默认时钟、信任资料仓、状态文件仓和吊销资料仓。
- 提供 `LicenseTrustMaterialInstaller`，对清单和全部吊销分片完成验签、摘要、作用范围、序号及快照元数据校验后再安装。
- 提供 `TrustedSubjectProvider`、`TrustStore`、`StateStore` 和 `RevocationStore` 等替换端口，宿主可接入真实主体、设备和持久化能力。

## 兼容性

- 传递 `io.github.sure-zzzzzz:smart-license-core:2.0.0`。
- 传递 `io.github.sure-zzzzzz:simple-license-client-core:2.0.1`。
- 运行基线为 Spring Boot 2.7.9，公开 Java API 按 Java 8 编写。
