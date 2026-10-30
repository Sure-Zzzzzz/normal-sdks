# simple-license-aksk-resttemplate-client-starter

License 客户端 RestTemplate 传输装配（javax 线，Spring Boot 2.7.9 / Java 8）：把 `LicenseServerClient` 业务接口映射到 License Server `/api/license/v1` 公开契约，传输与认证复用 AKSK 客户端生态的 `akskClientRestTemplate`（Bearer 注入由 AKSK 通道完成）。

## 解决什么、谁该接

- 你的宿主已在 AKSK 客户端生态内（启用 `io.github.surezzzzzz.sdk.auth.aksk...resttemplate.enable=true`），需要给业务提供 License 判定/占用/信任资料的 HTTP 通道。
- 引本件即获得 `LicenseServerClient` 默认实现；宿主自定义实现经 `@ConditionalOnMissingBean` 覆盖。

## 依赖坐标

```gradle
implementation 'io.github.sure-zzzzzz:simple-license-aksk-resttemplate-client-starter:2.0.0'
implementation 'io.github.sure-zzzzzz:simple-license-client-core:2.0.1'
// AKSK RestTemplate 底座由你的 AKSK 接入提供（本件对底座零类型 import，仅运行时复用其 Bean）
```

## 最小配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        license:
          client:
            enabled: true
            base-url: https://license.example.test
```

## 语义边界

- 传输固定按名注入官方 `akskClientRestTemplate`；缺少该认证底座时启动失败，宿主整体提供 `LicenseServerClient` 时默认装配让位。连接与超时由宿主的 AKSK 通道管理。
- 额度使用完整 `QuotaRequest`：产品、授权、对象种类、动作、指标、数量、稳定操作编号、预期授权修订以及资格或激活绑定全部透传。旧四参数入口缺少上下文，在发出 HTTP 前明确拒绝。
- 额度回执完整保留操作关联与 ACTIVE/RELEASED/EXPIRED 状态，严格反校创建请求的授权、指标、数量、种类、动作、修订和操作编号。幂等终态的 `isActive()` 为 false；续期只接受同一 ACTIVE 并发占用且围栏不回退，回执仍不能替代关联动作判定。
- 判定请求保留 `reservationReferences`、`businessOperationId` 与实际对象绑定；响应核对动作回显。调用方仍须按服务端回执执行业务去重与围栏检查，不能把判定允许当成扣减成功。
- 候选发现使用完整 `GrantSelectionRequest` 并返回 `GrantSelectionPage`，显式携带部署激活及页码/大小，严格校验分页元数据、模式和阶段。旧人员/服务入口自动获取所有页，旧部署入口因缺激活在 HTTP 前拒绝；候选发现不是原子快照或使用许可。
- 永久 TOTAL 额度的 `validUntil` 仅接收精确整数 `Long.MAX_VALUE` 标记，其他超安全整数及错误类型仍拒绝。
- 并发释放为 `DELETE /api/license/v1/quota-reservations/{id}`；续占为 `PUT /api/license/v1/quota-reservations/{id}/renewal`，正文只有 `expectedFence`。
- 信任资料先严格核对响应签发方、产品和整数类型，再交安装器验签与批准公钥。缺字段、小数、数字字符串、超安全整数或错误范围均拒绝；获取失败可保留最近有效快照。
- 官方传输不提供 HYBRID 租约任务与签名下载；历史两参数 `leaseRenew` 直接抛出 `UnsupportedOperationException`。人员代理证明与设备证明也不在当前值对象中，需要这些证明的调用由宿主完整实现。
- 认证、网络与协议错误传播为异常，不伪装业务拒绝。DEBUG 只记录调用阶段与固定操作名，默认关闭，不记录请求正文、凭据、认证头、密钥或完整 URL。

## jakarta 变体

Spring Boot 3.4.2 / Jakarta 线使用对应的 `simple-license-aksk-resttemplate-client-jakarta-starter`（同包名同语义，`LicenseClientJakartaTransportAutoConfiguration` 注册）。
