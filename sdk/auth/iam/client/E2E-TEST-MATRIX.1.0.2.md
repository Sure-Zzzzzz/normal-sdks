# IAM openapi client 四线端到端测试矩阵（1.0.2）

## 结论

四个传输件在各自支持的 Spring Boot 大版本上，**十接口 48 方法全部覆盖、四线全绿**；模块单测的 wire 契约断言同步补齐六族+applications（此前仅 users/departments/open-roles）。覆盖范围仅 SB 2.7 与 SB 3.x 两条线；更老的 Spring Boot / Spring Cloud 版本**不在兼容与测试范围**。

| 传输线 | 模块 | Spring Boot | 模块单测（含六族契约） | 全族端到端（48 方法） |
| --- | --- | --- | --- | --- |
| RT-SB2（javax） | simple-iam-aksk-resttemplate-client-starter 1.0.2 | 2.7 | 通过 | **ALL CLANS PASSED** |
| Feign-SB2（javax） | simple-iam-aksk-feign-client-starter 1.0.2 | 2.7 | 通过 | **ALL CLANS PASSED** |
| RT-SB3（jakarta） | simple-iam-aksk-resttemplate-client-jakarta-starter 1.0.2 | 3.x | 通过 | **ALL CLANS PASSED** |
| Feign-SB3（jakarta） | simple-iam-aksk-feign-client-jakarta-starter 1.0.2 | 3.x | 通过 | **ALL CLANS PASSED** |

四线消费同一契约源 `simple-iam-client-core:1.0.3`（十接口 48 方法），对位 server `simple-iam-server-starter` 1.3.7 的 32 个 openapi 路径。

## 端到端断言链（四线同构，方法级覆盖）

每线一个 JavaExec runner（test/demo 下 `IamClientE2eFullRunner` / `IamFeignClientE2eFullRunner`，断言失败即抛、非抽样），按族覆盖全部 48 方法：

| 族 | 方法数 | 覆盖内容 |
| --- | --- | --- |
| users | 13/13 | 分页/详情/角色清单/准入双端点（读）＋ 写生命周期（建→角色挂/摘→改→停/启→重置密码→删→404） |
| departments | 5/5 | 列/读＋临时部门建→改→删→404 |
| open-roles | 13/13 | 读链4（目标应用/清单/组织目录/成员）＋建/列/读＋规则三态（404 态→If-Match 首写→回读→二写递增→删→404）＋挂载/摘除（revision 递增实证） |
| applications | 1/1 | 目录分页（Spring Page wire） |
| messages | 3/3 | 发信/按人查信/批次详情 |
| sessions | 2/2 | 按人查会话/踢会话（被踢会话从列表消失实证） |
| permissions | 1/1 | 权限清单 |
| role-catalog | 1/1 | 角色目录 |
| user-groups | 8/8 | 目录/建/读/改/成员加/查/移/删＋删后 404＋目录收敛 |
| statistics | 1/1 | counts/stats 嵌套 wire 解析 |

如实注明两点：
- E2 踢会话的"踢除实证"在本轮四线均真实执行（admin 存活会话被踢除后验证消失；后续线次若会话列表为空则走空分支——同一端点同一语义）。
- open-roles 的角色本体无删除契约（设计即如此），E2E 建立的角色由测试后清理承担（联调库 SQL 清，非 client 能力缺失）。

## 模块单测契约层

- users/departments/open-roles 三族：既有 wire 契约断言（mock server，URL/HTTP 动词/请求体字段/双分页形态/428 透传）。
- 六族+applications：本轮新增 `shouldCallSixClanEndpointsWithContractFields`（RT 双线 MockRestServiceServer、Feign 双线 JDK HttpServer 桩），17 方法逐一断言路径/动词/body/响应解析。

## 测试环境形态

- server：simple-iam-local-runtime 部署 1.3.7（单实例），AKSK 侧 3.x 单实例供令牌。
- 认证链：真实 AKP client_credentials 令牌（非 mock）——Redis Route 数据源 → smart-cache L1/L2 → TokenManager → 底座 RestTemplate/Feign 拦截器。
- AKP 授权：iam 应用下十码＋openrole 八码＋DATA（iam:user 读写、iam:application 等 read、open 族读写，all=true）。
- 凭据：gitignored `application-local.yml`（模板 `application-local.yml.example`），不落仓。

## 复跑方式

```bash
# 仓库根目录；init 脚本注册四线各一个 runIamClientE2eFull 任务
gradle -I D:/code/local-test-fixtures/iam_client_full_e2e.init.gradle \
  :sdk:auth:iam:client:simple-iam-aksk-resttemplate-client-starter:runIamClientE2eFull \
  :sdk:auth:iam:client:simple-iam-aksk-feign-client-starter:runIamClientE2eFull \
  :sdk:auth:iam:client:jakarta:simple-iam-aksk-resttemplate-client-jakarta-starter:runIamClientE2eFull \
  :sdk:auth:iam:client:jakarta:simple-iam-aksk-feign-client-jakarta-starter:runIamClientE2eFull
```

前置：local-runtime 两服务在跑、本机 Redis 可达、各模块 test resources 已放 `application-local.yml`（AKP 凭据）、Gradle 8.5+ 与 JDK 17。

## 测试中发现并处置的缺陷（均已闭环，属 1.0.2 首发内容）

1. **statistics wire 嵌套与平铺模型不符**：server 统计端点 wire 为 `{counts:{...},stats:{...}}` 嵌套，RT 线按平铺解析抛协议异常。处置：client-core 模型注释钉 wire 形态、RT 双线分层读取、Feign 双线 wire 模型嵌套内部类。
2. **Feign wire 模型不容忍未知字段**：Spring Page wire 携带 `pageable/sort` 等扩展字段，Feign 数据绑定默认严格模式直接反序列化失败。处置：两线全部 wire 模型加 `@JsonIgnoreProperties(ignoreUnknown = true)`（模块 `compileOnly jackson-annotations`，POM 保持零依赖）。
3. **HTTP 动词与 server 契约不符（三处，全部四线同错）**：users `updateUser`/departments `updateDepartment` 用 PATCH（契约 PUT）；users `assignRole` 用 PUT（契约 POST）；openrole `mountOpenRoleToDepartment` 发 `{}` 请求体（契约要求空请求体）。处置：四线对位修复＋删除 RT 支撑层废料 patch 方法；全量动词×路径审计对齐 yaml 后无残留差异。
4. **契约文档误导**：`putOpenRoleRule`/`deleteOpenRoleRule` javadoc 写"null=首写"，实际服务端写操作一律要求 If-Match（带引号强 ETag，版本源=角色行 revision）；`dataGrantTemplate` 空对象服务端拒收（null=不携带）。处置：client-core 与 Feign 接口 javadoc 钉死真实语义。
5. **Feign 线测试配置缺装配链**：test `application.yml` 为空壳（无 redis route / aksk.client / cache 键），令牌链条件装配不满足。处置：补齐与 RT 线同构的完整链（jakarta 双线同补）。

## 观察项（非缺陷，如实记录）

1. 踢会话端点会对该主体存量 OAuth2 授权行做同步级联清理（逐条 DB+缓存）。长期联调积累数千条授权行时，单次踢会话可达分钟级（实测约 5100 条、约 6 分钟清完，此后恢复毫秒级）。属存量数据量问题；若生产预期单主体授权行数量级大，建议关注该端点的清理策略。
2. 受委托角色族的固定目标应用不可为内置应用（iam/aksk 等，服务端 409 语义）；E2E 以非内置应用为目标。
