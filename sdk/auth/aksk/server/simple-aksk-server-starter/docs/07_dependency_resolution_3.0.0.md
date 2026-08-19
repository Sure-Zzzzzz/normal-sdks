# Simple AKSK Server Starter 3.0.0 依赖解析验证

## 发布前源码组合

在 `simple-aksk-server-core:3.0.1` 尚未被 Central 实证解析前，Starter 保持已发布的 Core `3.0.0` 坐标，仅对尚未发布的 Server Core 使用工程依赖：

```gradle
api 'io.github.sure-zzzzzz:simple-aksk-core:3.0.0'
api project(':sdk:auth:aksk:server:simple-aksk-server-core')
```

不得把同一上游模块同时声明为 project 与远程坐标。

## Central 可解析后的收口

当 Core `3.0.0` 与 Server Core `3.0.1` 均通过 Central 实证解析后，同时改为：

```gradle
api 'io.github.sure-zzzzzz:simple-aksk-core:3.0.0'
api 'io.github.sure-zzzzzz:simple-aksk-server-core:3.0.1'
```

随后运行完整 Starter 测试，再建立仓库外的干净消费者。

## 干净消费者验证

消费者目录不能引用本工程 source、`project(...)` 依赖或预置本地发布仓库。验证顺序：

1. 仅声明 Starter `3.0.0`、Core `3.0.0`、Server Core `3.0.1` 与公开运行时依赖。
2. 刷新远程依赖并确认依赖图没有工程路径或本地快照。
3. 编译并启动最小 Spring Boot 2.7.x 应用。
4. 使用全新数据库和 Redis 完成一个 Client 的应用授权配置、显式准入、Token 签发及受保护内省状态验证。
5. Server audit listener `3.0.0` 发布后，额外验证其可选坐标解析，并只确认提交后 Handler 接收事件类型、原因和脱敏状态。
6. 不在终端、日志或验收报告输出任何认证材料、Token 或完整响应。

该验证仅证明消费解析与最小运行闭环；不执行 publish、upload、tag 或 push。
