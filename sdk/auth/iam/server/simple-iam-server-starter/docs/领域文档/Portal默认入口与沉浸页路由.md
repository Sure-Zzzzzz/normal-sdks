# Portal 默认入口与沉浸页路由

本文是 IAM Server 1.1.0 对可信应用 Portal 行为的权威领域设计，覆盖默认入口、登录首页和沉浸页路由。菜单树、页面可见性投影的基础规则亦在本文说明；本文应与 Contract、schema、migration 和测试一起提交。

## 目标与边界

报表大屏需要在 Portal 内无边框展示，但“当前页面如何展示”和“用户首次进入哪里”是两件独立的事。1.1.0 固定为下列三层，任何一层都不能隐式替代另一层：

| 配置 | 归属 | 作用 | 数量 |
|---|---|---|---|
| `presentationMode` | PAGE 菜单节点 | 决定当前 URL 是否展示 Portal 顶栏和左侧栏 | 每个 PAGE 一个 |
| 应用默认入口 | 可信应用 Portal 配置 | 用户显式访问应用根路由时的入口 PAGE 与静态子路由 | 每个 Portal 应用至多一个 |
| Portal 登录首页 | Portal 全局设置 | 没有登录前目标地址时优先进入的应用 | 全 Portal 至多一个 |

`IMMERSIVE` 的语义仅为隐藏 Portal 顶栏与左侧栏。它不调用浏览器全屏、不改变会话、Token、页面权限或数据权限，也不能成为“登录后默认页”的隐含信号。微前端不得请求宿主去壳；Portal 仅根据 IAM 返回、且已经权限裁剪的 `menuTree` 与当前 URL 计算展示模式。

每个可信应用可以配置自己的默认入口，但不能各自争抢登录后第一屏。Portal 登录首页是显式选择的全局单例，不采用“最后保存者获胜”、按菜单顺序猜测或优先所有 `IMMERSIVE` 页面的规则。

本文不引入外部 URL 默认跳转、个人级首页、带查询参数的默认报表、浏览器 hash 路由解析或无限制的应用路由白名单。这些能力出现真实需求时另行评审。

## 配置模型与生命周期

### 稳定引用

菜单树保存使用完整快照替换：校验整棵树后删除旧行，再按父节点在前顺序插入新行。因此 `iam_trusted_application_menu.id` 是易变的自增行 ID，菜单保存后会变化，**不得**被默认入口或其他配置引用。

默认入口使用同一应用内唯一的菜单 `code` 作为语义键：

| 表 | 字段 | 说明 |
|---|---|---|
| `iam_trusted_application_portal` | `default_page_menu_code VARCHAR(64) NULL` | 默认入口所属 PAGE 的 `code` |
| `iam_trusted_application_portal` | `default_entry_path VARCHAR(255) NULL` | 相对 `route_prefix` 的静态入口路径；为空时取 PAGE `route` |
| `iam_trusted_application_portal` | `config_version BIGINT NOT NULL DEFAULT 0` | 完整 Portal 配置的乐观锁版本 |
| `iam_portal_setting` | `id INT NOT NULL PRIMARY KEY` | 单例行，固定为 `1`；与实体主键类型一致，不依赖 MySQL `CHECK` 的版本差异 |
| `iam_portal_setting` | `login_landing_application_id BIGINT NULL` | 无深链登录首页的应用，不直接引用菜单 |
| `iam_portal_setting` | `version BIGINT NOT NULL DEFAULT 0` | 全局登录首页的乐观锁版本 |

`menuCode` 在应用树内已经唯一，快照替换后仍可重新解析。若编辑菜单时删除、改名或将默认 PAGE 改为 GROUP，新的完整 Portal 配置必须在同一事务中选择新默认页或明确清空默认入口；不能留下失效 code。

延续 IAM 的关系一致性策略，默认 PAGE 归属、节点类型、全局首页应用启用状态等由服务事务校验，不用跨表外键表达。关闭 Portal 集成、清空默认入口、删除默认 PAGE 或将其改名时，如果该应用是全局登录首页，返回 `409 Conflict` 并要求先改选或关闭全局首页。删除整个可信应用是明确级联操作，服务端在同一事务内清除全局引用后再删除。

### 管理写模型与并发

既有可信应用整体更新接口无法区分旧客户端的“未提交默认入口字段”和新客户端的“明确清空默认入口”。为兼容旧管理台，新增完整 Portal 配置端点：

```text
PUT /iam/admin/trusted-applications/{applicationId}/portal/configuration
```

它以完整快照更新 Portal 集成、`menuTree` 与默认入口：

```json
{
  "enabled": true,
  "entry": "https://portal.example.invalid/app/aksk/",
  "apiBase": "https://api.example.invalid",
  "menuTree": [],
  "defaultEntry": {
    "pageMenuCode": "operations-wall",
    "entryPath": "/wall/overview"
  },
  "configVersion": 7
}
```

`defaultEntry: null` 是明确清空。非 null 时，`pageMenuCode` 必须解析为本次 `menuTree` 中的 PAGE，`entryPath` 必须通过后文的路径校验。`configVersion` 条件更新不匹配时返回 `409`；管理台刷新详情后由管理员决定是否重做编辑，不能覆盖另一位管理员刚修改的菜单或入口。

可信应用详情增加只读 `defaultEntry` 与 `configVersion`。旧整体更新接口继续可用并保留它不认识的默认入口字段；若旧接口提交的 Portal 或菜单变更会使默认入口失效，整次请求返回 `409`。无论新旧入口，成功变更 Portal 配置都递增 `config_version`，防止新端基于过期详情保存。

全局登录首页使用独立端点：

```text
PUT /iam/admin/portal/login-landing
```

```json
{
  "applicationCode": "aksk",
  "version": 3
}
```

`applicationCode: null` 表示关闭。该端点只允许平台管理员 `ROLE_iam_admin` 调用；应用配置管理员只能修改其应用默认入口，不能把自己的应用设为所有用户的门户首页。IAM Admin 可以在可信应用编辑区把全局首页呈现为单选项，但它调用的仍是该 Portal 全局端点，不能存成每应用 boolean。

每次写入断言如下：

1. 目标应用存在、Portal 已启用；设置全局首页时，它已有合法应用默认入口。
2. 默认 PAGE 属于目标应用、类型为 PAGE，路由与入口路径均为合法应用内路径。
3. 默认 PAGE 或路径在菜单快照中被修改后仍与默认入口匹配；被全局首页引用的应用不得变为无默认入口。
4. 相同 Portal 配置或全局设置的并发写入，版本条件更新至多成功一个，失败方稳定得到 `409`。
5. 写入沿用管理审计，记录操作人、目标应用 ID、入口/全局首页变更标志和版本号；不记录完整入口路径、权限 JSON、Token 或 Cookie。

## 路由契约

默认入口是相对 `routePrefix` 的静态 history 路径，不是外部跳转器。以 AKSK 为例：应用根为 `/app/aksk`，菜单 PAGE `code=operations-wall` 的 `route=/wall`：

| 配置或访问地址 | 结论 |
|---|---|
| `entryPath=/wall` | 合法，应用根进入该 PAGE |
| `entryPath=/wall/overview` | 合法，大屏应用内默认子路由 |
| `/app/aksk/wall/region/east` | 合法，继承 `/wall` 的展示模式 |
| `/wallpaper` | 非法，不是 `/wall` 的分段子路径 |
| `/wall/../clients`、`/%2e%2e/clients` | 非法，拒绝目录穿越 |
| `https://other.example/wall`、`//other.example/wall` | 非法，拒绝跨域和协议相对路径 |
| `/wall?region=east`、`/wall#live` | 非法，查询串和片段不进入平台入口配置 |

服务端先拒绝空白、控制字符、反斜杠、scheme 和协议相对路径；对 percent-encoding 进行受限重复解码，拒绝任何一次解码后出现的 `.` / `..` 段、分隔符绕过、查询或片段字符；再规范化连续 `/`。入口路径必须等于 PAGE `route` 或以 `PAGE route + "/"` 开头。PAGE `route=/` 是唯一例外，可承载应用内任意绝对路径。规范化后的长度受数据库列长度限制。

应用内二级、三级路由不必逐一建立菜单节点。`/wall` 为 `IMMERSIVE` 时，`/wall/overview`、`/wall/realtime` 与 `/wall/region/east` 继承其模式。若 `/wall/settings` 应恢复标准 Portal 框架，可增加更具体的 PAGE `/wall/settings` 并设为 `STANDARD`；Portal 在当前用户可访问的 PAGE 中选择路由最长的匹配项。`/wall` 与 `/wall/settings` 的前缀重叠是合法配置，不能被菜单保存误判为重复路由。

Portal 只解析 history 路由。hash 路由子应用不能把 hash 中地址声明为平台默认入口；应迁移至 history 路由，或只让子应用自身在根页完成内部重定向。

## Portal 读模型与落点决议

保持 `GET /iam/web/portal/accessible-applications` 的既有列表响应不变。1.1.0 新增：

```text
GET /iam/web/portal/navigation-context
```

该端点返回权限裁剪后的应用、每个可用应用的默认入口，以及当前用户真正可使用时才返回的全局登录首页：

```json
{
  "applications": [
    {
      "applicationCode": "aksk",
      "routePrefix": "/app/aksk",
      "menuTree": [],
      "defaultEntry": {
        "pageMenuCode": "operations-wall",
        "path": "/wall/overview"
      }
    }
  ],
  "loginLandingApplicationCode": "aksk"
}
```

普通用户的 `defaultEntry` 仅当它引用的 PAGE 仍存在于权限裁剪后的 `menuTree` 时下发；全局登录首页仅当目标应用及其入口对当前用户均可用时下发。服务端不会把未授权页面路径交给前端“自行判断”。该读模型只提供路由候选，不授予权限；布局仍由 `menuTree` 的 `presentationMode` 推导。

落点按访问上下文分支：

```text
已验证的登录前目标地址 / 显式深链接
  -> 原样恢复

显式访问 /app/{application} 或 /app/{application}/
  -> 该应用可用默认入口
  -> 该应用稳定排序下的首个可访问 PAGE
  -> 无权访问状态（绝不跨应用跳转）

访问 Portal 根路径且无登录前目标
  -> 全局登录首页应用的可用默认入口
  -> 全部可访问应用中稳定排序下的首个 PAGE
```

登录前目标地址必须由现有登录状态机制以一次性、会话绑定的 state 保存并在恢复时校验：仅允许当前 Portal origin、以 `/app/` 为边界的规范化本地路径和原始查询串，拒绝外部 origin、协议相对地址、编码绕过和不属于已挂载应用的路径。查询串仅作为已验证深链的原样状态，不参与默认入口配置或 Portal 的二次跳转解释。URL fragment 不会进入 HTTP 请求；Portal 若需保留它，必须在发起登录前以同源 `sessionStorage` 结合一次性 state 保存并在回跳时重新校验。全局首页绝不能覆盖通过此校验的深链接。

“首个 PAGE”必须确定：先按 `applicationCode` 升序，再按菜单树 `sortOrder, code` 深度优先顺序选择；不得依赖未指定 SQL 排序、展示名称或浏览器对象遍历顺序。Portal 仅在精确进入应用根路径时应用默认入口。含子路径的直接访问、子应用内部导航、浏览器前进后退与登录回跳均保持原路径。单次导航的入口重定向最多一次；目标不存在、等于当前根路径，或子应用再次回到根路径时停止重试并按对应回退处理，防止 Portal 与子应用循环跳转。

## 授权、数据与运行一致性

默认入口和沉浸展示不是授权机制：

- IAM 的 `admitted`、`status` 和 `pagePermissions` 只决定 Portal 菜单和入口候选是否可见。
- 用户手工输入未显示子路由时，Portal 不扩大 `menuTree`；业务子应用仍必须依据 Token 中的页面权限强制页面/API 访问。
- AKSK 等资源服务按主体、应用授权投影和 DATA grant 执行数据过滤。普通用户进入同一个大屏路由也只能看到自己的数据范围，管理员的 `all=true` 不能由前端菜单继承。
- 需要独立管控的应用内子路由应具有独立 PAGE 权限并由资源服务强制校验；继承父路径的 `IMMERSIVE` 只继承样式，不继承权限。

菜单、默认入口、应用启停或授权变更后，Portal 下次读取 `navigation-context` 必须重新决议，浏览器缓存不是事实源。IAM 双实例部署下，入口配置和可访问菜单不能只依赖本地进程缓存；如使用缓存，写入成功必须使两实例下一次读取得到同一版本。Portal 的“应用已刷新”提示只能促使重新读取并按当前 URL 重算，不能将正在使用子路由的用户强制送到新的默认入口。

## 升级、兼容与验收

全新 1.1.0 环境的 `docs/schema.sql` 必须包含全部默认入口字段与 `iam_portal_setting`。从 1.0.0 升级的唯一脚本 `docs/migration/V1.0.0__to__V1.1.0__portal_menu_tree.sql` 在同一文件中完成菜单树、展示模式、默认入口和单例设置表的升级。

存量应用的应用默认入口和全局登录首页初始都是 null，保持升级前行为；不得根据创建时间、菜单排序或 `IMMERSIVE` 自动挑选某个大屏。内置 IAM 是唯一的受控例外：新安装，以及菜单精确匹配 1.0 官方七项默认菜单的升级实例，启动引导会将 `dashboard` 写为 `IMMERSIVE`，使总览页无边框展示；其下钻的用户、组织、角色、可信应用和站内信 PAGE 仍为 `STANDARD`，路由切换后立即恢复门户壳。任何手工菜单调整均视为自定义配置并保持原展示模式。迁移必须验证前提，重复执行应有明确的幂等结果或失败提示，不能制造第二个单例设置行。

部署时 IAM Server、IAM Admin、Portal 可分步升级：新 Portal 调用 `navigation-context` 得到 404 时退回 `accessible-applications` 与旧落点逻辑；旧 Portal 忽略新增字段继续消费 `menus`。缺失 `presentationMode`、默认入口或全局首页时分别按 `STANDARD`、无默认入口处理，不能白屏。

验收至少覆盖：

| 场景 | 断言 |
|---|---|
| 两个应用各有默认入口 | 各自根路由进入各自入口；仅全局单例应用作为无深链登录首页 |
| 登录前深链 | `/app/aksk/wall/region/east` 登录后原样恢复 |
| 沉浸子路由与标准覆盖 | 大屏子路由继承 `IMMERSIVE`；更具体且可访问的 `STANDARD` PAGE 恢复 Portal 框架 |
| IAM 仪表盘下钻 | `/app/iam` 隐藏 Portal 壳；进入 `/app/iam/users` 等标准管理页立即恢复壳，仪表盘内抽屉操作保持当前页面 |
| 普通用户撤权 | 不下发失效入口和全局首页；Portal 稳定回退，资源 API 仍返回 403 |
| 路径攻击 | 外部 URL、协议相对 URL、反斜杠、查询/片段与多重编码目录穿越均返回 400 |
| 菜单快照替换 | 删除或改名默认 PAGE 未同步改入口时事务回滚；不使用易变菜单行 ID |
| 并发管理员 | Portal 配置、全局首页版本冲突稳定返回 409，后写者不覆盖前写者 |
| 应用生命周期 | 停用前先改选全局首页；删除时同事务清除引用；历史损坏配置失败关闭 |
| 双实例与版本组合 | 两实例读取一致；全量建库、1.0 -> 1.1 升级、前端新旧组合均按兼容规则工作 |

## 五项自审

1. **覆盖度**：包含多应用竞争、应用内路由、深链、回退、路径攻击、菜单生命周期、并发、双实例与升级。
2. **断言严谨性**：默认入口只能来自权限裁剪后的 PAGE；菜单 ID 不作为稳定引用；Portal 只决定外壳，业务服务端仍是权限和数据范围的最终门禁。
3. **开发规范**：保持 Java 8 / Spring Boot 2.7.9 基线，不引入生产依赖；Contract、schema、迁移、错误形态、中文注释与端到端测试必须同步交付。
4. **下一版本视角**：不提前加入个人首页、外部跳转、动态筛选参数或无限路由声明；如出现需求，需在不破坏本路径与权限契约的前提下单独设计。
5. **中文注释与文档**：实现中保留“菜单快照 ID 不稳定”“默认入口不是开放重定向”“深链优先”“沉浸模式不等于权限”“全局首页只允许平台管理员修改”的中文边界说明。
