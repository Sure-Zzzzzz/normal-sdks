# CHANGELOG 1.0.1

## 契约补齐（对齐 AKSK Server 实际 wire）

- `ApplicationAuthorizationResponse` 补 `dataGrantDocument` 字段：server 原样回显 DATA 授权文档，
  1.0.0 未建模导致 Feign 形态（宿主 ObjectMapper 严格模式）反序列化失败。
- `listClients` 批量形态独立成方法 `listClientsByClientIds(List<String>)`：携带 `clientIds` 时 server
  返回 `clients` 键值对信封（非分页形态），1.0.0 固定建模为 `PageResponse` 会静默解出空页；
  `ListClientsQuery` 相应收窄为分页字段（ownerUserId/type/page/size），新增 `BatchClientResponse` 模型。
- 接口由 20 方法扩为 21 方法（HTTP 端点仍 20 个，`listClients` 一端点双形态）。
