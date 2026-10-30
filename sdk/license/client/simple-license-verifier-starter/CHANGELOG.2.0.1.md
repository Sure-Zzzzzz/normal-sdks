# 2.0.1（2026-10-30）

## 类型

Patch / 持久状态丢失后的失败关闭修复

## 修复

- 状态文件已提交后缺失，新的 `FileStateStore` 实例拒绝按首次启动继续运行。
- 吊销快照已安装后缺失，新的 `FileRevocationStore` 实例拒绝按空仓继续运行。
- 吊销序号状态键改用长度分帧，避免编号包含点号时发生签发方/产品碰撞，并兼容读取旧键。
- 补充独立新实例重启后的状态文件丢失回归测试。

## 依赖

| 依赖 | 版本 |
| --- | --- |
| `smart-license-core` | `2.0.0` |
| `simple-license-client-core` | `2.0.1` |

## 新增测试

- `VerifierStarterTest`：覆盖状态文件丢失后新实例启动和已有实例写入的失败关闭。
- `VerifierStarterTest`：覆盖包含点号的签发方和产品编号不会共享吊销序号。
- `VerifierStarterTest`：覆盖首次持久化创建主状态文件和旁路锁文件，以及已有状态缺少旁路锁时的迁移。
- `FileRevocationStoreTest`：覆盖吊销快照丢失后新实例启动的失败关闭、已有快照缺少旁路锁时的迁移，并保留首次安装、并发安装和损坏快照断言。

## 验证

- `:sdk:license:client:simple-license-verifier-starter:clean :sdk:license:client:simple-license-verifier-starter:test --no-daemon`：38/38 通过。

## 兼容性

- 保持 Java 8 公开 API 与 Spring Boot 2.7.9 运行基线不变。
- 不新增运行时依赖；首次启动且尚未创建旁路锁文件的空仓仍可正常初始化。

## 升级指南

- 将依赖坐标从 `2.0.0` 更新为 `2.0.1`。
- 保留 `state-dir` 及其中的旁路锁文件；状态文件缺失时按失败关闭处理，并通过受控状态恢复流程恢复，不要直接删除整个目录后重新初始化。
