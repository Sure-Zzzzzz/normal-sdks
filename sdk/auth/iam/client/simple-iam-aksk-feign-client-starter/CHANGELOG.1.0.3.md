# 1.0.3

## 发布信息

- 类型：Feature / 契约对齐（simple-iam-client-core 1.0.4，server 1.3.8）
- 基线版本：1.0.2

## 变更

- 新增 Portal 可信应用路由投影客户端实现：`GET /iam/api/portal/applications`。
- 请求支持 `page`、`size` 和可选 `catalogRevision`；响应解析目录 revision、路由来源、Portal 配置版本和受控转发策略。
- 旧可信应用三字段目录客户端保持原路径和 wire 形态。
- 传输、认证头、连接池和凭据边界沿用既有 AKSK 底座，本模块不自建令牌链或记录敏感数据。

## 兼容性

- 需要 Server `1.3.8+` 以及 AKP 对应 API + DATA 授权；旧 Server 上新增端点返回未提供或权限错误，既有接口不变。
- SB2 与 Jakarta 线仅在各自宿主兼容矩阵内使用；四件均依赖 client core `1.0.4`。
