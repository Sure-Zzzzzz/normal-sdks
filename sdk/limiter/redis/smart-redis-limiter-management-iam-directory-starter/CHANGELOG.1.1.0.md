# 1.1.0

## 能力

- **服务清单以 IAM 可信应用注册表为权威数据源**：目录提供方新增外层装饰
  `IamApplicationSmartRedisLimiterDirectoryProvider`，`listServices`/`findService` 的清单来源
  接入 IAM 应用目录（`IamApplicationClient.listApplications`，IAM 服务端 1.3.7 起）：
  - 清单为注册表全部启用应用；显示名取注册表 `application_name`；未声明限流语义的应用
    同样出现，资源列表为空（页面不可新建）。
  - 注册表停用或未登记的本地声明不出现并记录 WARN。
- **聚散容错分叉**：IAM 连通类故障（超时/连接拒绝/5xx）时回退宿主完整声明，输出与不引本件
  的形态完全一致，降级与恢复各记录一条日志；授权失败（401/403）与版本错配（404，服务端
  低于 1.3.7）异常原样上抛，不做静默回退。
- **注册表快照缓存**：60 秒内存快照，过期后单线程回源，其余请求沿用旧值。

## 依赖

- `simple-iam-aksk-resttemplate-client-starter` 1.0.1 → 1.0.2（新增 `IamApplicationClient`）。
- 调用方 AKP 在原有 `iam:user:api` + `iam:user` read 基础上追加
  `iam:application:api` + `iam:application` read（all）。

## 兼容

- USER 维度对象检索行为不变（内层装饰，异常透传语义）。
- 不引本件的宿主零影响；装饰后容器内目录提供方仍恰好一个 Bean。
- `IamApplicationRegistrySnapshot` 构造器公开（测试可指定 TTL），其余公开面无变化。
