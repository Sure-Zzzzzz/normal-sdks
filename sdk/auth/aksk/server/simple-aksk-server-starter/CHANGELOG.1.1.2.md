# 1.1.2 版本变更 (2026-04-28)

## Bug 修复

### 1. 删除 Client 时不撤销关联 Token

**问题**：`deleteClient()` 直接删除 Client 记录，未先撤销该 Client 下的活跃 Token，导致孤立的 Token 仍可使用。

**修复**：`deleteClient()` 内部先调用 `revokeAllByClientId()` 撤销所有活跃 Token，再删除 Client 记录。

### 2. 重置 Secret 后页面数据不回填

**问题**：Admin 页面重置 Secret 后跳转到 `/admin/create-success`，但 `createSuccess()` 控制器未读取 URL 参数（`clientId`、`clientSecret`、`message`），导致页面字段空白。

**修复**：`createSuccess()` 新增 `@RequestParam` 读取 URL 参数并添加到 Model；页面标题根据 message 内容动态显示"Secret 重置成功"或"AKSK创建成功"。

### 3. Token 列表页 REVOKED 过滤后结果不验证

**问题**：REVOKED 状态过滤功能存在，但测试未验证过滤结果是否包含被撤销的 Token。

**修复**：补充验证逻辑，确保 REVOKED 过滤结果包含预期的 Token。

## 优化

### 1. Token 详情页撤销按钮状态完善

- REVOKED 状态的撤销按钮禁用（之前只禁用了 EXPIRED）
- EXPIRED 状态的 tooltip 提示更明确

### 2. Admin 首页和详情页断言增强

- 首页验证创建的 Client 出现在列表中
- 详情页验证 Client ID 和名称正确显示
- 删除操作验证 Client 确实被删除

### 3. Client 列表页 enabled 过滤增强

- 新增 `enabled=true` 过滤测试（之前只测试了 `enabled=false`）

### 4. create-success 页面动态标题

- 重置 Secret 时标题显示"✓ Secret 重置成功"
- 创建 AKSK 时标题显示"✓ AKSK创建成功"

## 测试覆盖

### 单元测试修复（全项目审计，27 个文件，50+ 处问题修复）

#### CRITICAL 修复（7 处）

| 文件 | 问题 | 修复 |
|------|------|------|
| `BatchRevokeServiceTest` | `assertTrue(count >= 0)` 恒真 | 创建真实 Token，验证撤销数量 = ACTIVE 数量 |
| `ResetSecretServiceTest` | 名字含 revokeTokens 但未创建 Token | 创建 Token 并验证撤销/不撤销结果 |
| `PlatformClientManagementTest` | scope 断言用了创建返回值而非数据库查询值 | 改用 `retrievedClientInfoResponse.getScopes()` |
| `TokenManagementServiceTest` | `testDeleteExpiredTokens` 恒真断言 | 先创建过期 Token，断言 `count >= 1` |
| `RestTemplateEndToEndTest` | `assertTrue(true)` 永远通过 | 改为 `assertThrows` + 验证 401 |
| `resource-server/AnnotationIntegrationTest` | if/else 同时接受 200 和 403 | 明确断言预期状态码 |
| `JwtIntegrationTest` | 4 个测试零/弱断言 | 添加具体字段验证 |

#### HIGH 修复（8 处）

| 文件 | 修复 |
|------|------|
| `ClientManagementIntegrationTest` | `createUserClient` 参数顺序修正 + DELETE 后验证 |
| `HttpSessionTokenManagerConcurrencyTest` | worker 线程内断言改用 Future.get() 在主线程断言 |
| `TokenManagementServiceTest` | `testQueryTokensByDataSource` 实际验证 MYSQL/BOTH 数据源标记 |
| `RevokeTokenServiceTest` | 二次撤销验证 metadata 未改变 |
| `TokenRevokedEventTest` | sleep 10s→3s + @AfterEach 完整清理 + CopyOnWriteArrayList |
| `SmartCachePubSubTest` | CacheInvalidationListener 缺失时 fail 而非 warn |
| `IntrospectLocalCacheHelperTest` | testMaxSizeEviction 验证早期条目被淘汰 |
| `EventPublishEndToEndTest` (x2) | Thread.sleep→CountDownLatch.await + CopyOnWriteArrayList |

#### MEDIUM 修复（15+ 处）

- **@AfterEach 缺失 authorization 清理**：9 个文件添加 `authorizationEntityRepository.deleteAll()`
- **Boolean NPE 风险**：IntrospectConfigTest 和 IntrospectAnonymousTest 中 `(Boolean) map.get()` → `assertEquals(true, map.get())`
- **CloseableHttpClient 资源泄漏**：IntrospectAnonymousTest 加了 try-finally close
- **AdminDisabledTest**：缩小状态码范围（去掉 401，404/403 足够）
- **UserClientManagementTest**：创建断言加了 clientName/clientType/ownerUserId/ownerUsername
- **RedisTokenManagerConcurrencyTest**：token 断言加了 `startsWith("eyJ")`；display name 修正
- **BothClientsCoexistTest**：移除未使用变量

#### LOW 修复（5 处）

- JwtTokenValidationTest / SimpleAkskExpressionExceptionTest / SimpleAkskSecurityExceptionTest：移除构造函数后恒真 `assertNotNull`
- PlatformClientManagementTest：`testRegenerateSecretKeySuccess` 验证新密钥与旧密钥不同且以 SK 开头
- TokenManagementServiceTest：移除未使用 `restTemplate`，异常时 throw 而非 return null

### 新增测试用例

| 测试方法 | 覆盖场景 |
|---------|---------|
| `testTokenDetailPageRevokedRevokeButtonDisabled` | REVOKED 状态撤销按钮禁用 |
| `testCreateSuccessPageWithUrlParams` | 重置 Secret 后 URL 参数回填到页面 |
| `testCreateSuccessPageDynamicTitle` | 重置 Secret 显示"Secret 重置成功"，创建 AKSK 显示"AKSK创建成功" |
| `testClientListEnabledFilterTrue` | enabled=true 过滤 |

## 版本兼容性

- 向下兼容所有 1.x 版本
- 无新增接口
- 数据库结构无变化
