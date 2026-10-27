# simple-feedback-iam-adaptor-starter 1.0.1

## 发布信息

- 版本：`1.0.1`
- 类型：Feature / Feedback server 1.0.1 适配
- 发布日期：2026-10-25

## 变更内容

- 将公开 `simple-feedback-server-core` 依赖固定到 1.0.1，保持 adaptor 与 Feedback server 的 SPI 契约一致。
- 保留 IAM 页面准入详情、用户联系提示和全局可信应用目录核对能力，出站便利性失败继续按约定降级。

## 依赖升级

| 依赖 | 版本 |
|---|---:|
| `simple-feedback-server-core` | 1.0.1 |
| `simple-iam-client-core` | 1.0.3 |
| `simple-iam-aksk-resttemplate-client-starter` | 1.0.2（由宿主显式提供） |

## 兼容性

- Spring Boot 2.7.9 / Java 8。
- 对接 `simple-feedback-server-starter:1.0.1` 与 `simple-iam-client-core:1.0.3`。

## 新增测试

- `IamAdaptorSpiTest`：覆盖 IAM 适配器的两个出站 SPI、失败降级和服务主体资料映射。

## 向后兼容性

- `AccessibleApplicationProvider` 与 `SubmitterProfileProvider` 的公开方法保持兼容；未引入本模块时，server-starter 的散形态降级行为不变。
- 需要页面准入详情的宿主必须使用 IAM client-core 1.0.3 及以上，并提供对应认证传输 Bean。
- 本模块不再向宿主传递 IAM 认证传输实现；宿主需显式引入兼容的认证传输 starter，现有 public 构造器仍使用可编译的 IAM client-core 类型。

## 升级指南

将 Feedback server 与本适配器一起升级到 1.0.1，并为 AKP 配置 `iam:user:api`、`iam:portal:api` 和 `iam:application:api` 及其最小 DATA 范围；不需要 IAM hint 时可不引入本模块。
