# normal-sdks 编码规范

> **适用范围**：本规范适用于 normal-sdks 下的所有 SDK 模块开发

## 目录

- [1. 命名规范](#1-命名规范)
  - [1.1 包名规范](#11-包名规范)
  - [1.2 类名规范](#12-类名规范)
  - [1.3 常量命名](#13-常量命名)
  - [1.4 工具类命名](#14-工具类命名)
- [2. Lombok 使用规范](#2-lombok-使用规范)
  - [2.1 强制使用 Lombok](#21-强制使用-lombok)
  - [2.2 Properties 类](#22-properties-类)
  - [2.3 枚举类](#23-枚举类)
  - [2.4 异常类](#24-异常类)
  - [2.5 测试类](#25-测试类)
  - [2.6 其他常用注解](#26-其他常用注解)
- [3. 模块结构规范](#3-模块结构规范)
  - [3.1 Package Marker（必需）](#31-package-marker必需)
  - [3.2 自定义组件注解（必需）](#32-自定义组件注解必需)
  - [3.3 标准目录结构](#33-标准目录结构)
  - [3.4 数据类包规范](#34-数据类包规范)
- [4. 配置类规范](#4-配置类规范)
  - [4.1 全局 Properties 类（单一）](#41-全局-properties-类单一)
  - [4.2 AutoConfiguration 类](#42-autoconfiguration-类)
- [5. 常量规范](#5-常量规范)
  - [5.1 常量类结构](#51-常量类结构)
  - [5.2 ErrorCode 常量](#52-errorcode-常量)
  - [5.3 ErrorMessage 常量](#53-errormessage-常量)
  - [5.4 枚举规范](#54-枚举规范)
- [6. 自定义异常规范](#6-自定义异常规范)
  - [6.1 基础异常类](#61-基础异常类)
  - [6.2 具体异常类](#62-具体异常类)
  - [6.3 异常使用示例](#63-异常使用示例)
- [7. Gradle 构建规范](#7-gradle-构建规范)
  - [7.1 主 Gradle（根目录 build.gradle）](#71-主-gradle根目录-buildgradle)
  - [7.2 子模块 Gradle](#72-子模块-gradle)
  - [7.3 版本文件](#73-版本文件)
- [8. 硬编码零容忍规范](#8-硬编码零容忍规范)
  - [8.1 禁止事项](#81-禁止事项)
  - [8.2 例外情况](#82-例外情况)
- [9. 代码注释规范](#9-代码注释规范)
  - [9.1 类注释](#91-类注释)
  - [9.2 方法注释（公共方法必须）](#92-方法注释公共方法必须)
  - [9.3 字段注释](#93-字段注释)
- [10. 配置文件示例](#10-配置文件示例)
  - [10.1 敏感配置管理](#101-敏感配置管理)
- [11. 中间件相关规范](#11-中间件相关规范)
  - [11.1 Redis规范](#111-redis规范)
  - [11.2 MySQL规范](#112-mysql规范)
- [12. 测试规范](#12-测试规范)
  - [12.1 测试目录结构](#121-测试目录结构)
  - [12.2 TestApplication 规范](#122-testapplication-规范)
  - [12.3 测试类规范](#123-测试类规范)
  - [12.4 测试数据和配置规范](#124-测试数据和配置规范)
  - [12.5 日志和断言规范](#125-日志和断言规范)
  - [12.6 Gradle 依赖配置](#126-gradle-依赖配置)
  - [12.7 测试覆盖要求](#127-测试覆盖要求)
  - [12.8 数据库测试规范](#128-数据库测试规范)
- [13. 总结](#13-总结)
  - [核心原则](#核心原则-1)
  - [检查清单](#检查清单)

---

## 1. 命名规范

### 1.1 包名规范

**全局规则**：
- **全部使用单数形式**（主体代码和测试代码统一遵循）

**主体代码包名**：
- 基础包路径：`io.github.surezzzzzz.sdk.{domain}.{module}`
- 工具类包名：使用 `support`，不使用 `util`

**测试代码包名**：
- 测试包路径：`io.github.surezzzzzz.sdk.{domain}.{module}.test`
- 测试用例包路径：`io.github.surezzzzzz.sdk.{domain}.{module}.test.cases`

**示例**：
- ✅ 主体：`io.github.surezzzzzz.sdk.auth.aksk.configuration`
- ✅ 主体：`io.github.surezzzzzz.sdk.auth.aksk.support`
- ✅ 测试：`io.github.surezzzzzz.sdk.auth.aksk.test`
- ✅ 测试用例：`io.github.surezzzzzz.sdk.auth.aksk.test.cases`
- ❌ `io.github.surezzzzzz.sdk.auth.aksk.configurations`（使用了复数）
- ❌ `io.github.surezzzzzz.sdk.auth.aksk.util`（应使用 support）

### 1.2 类名规范
- **全部使用单数形式**
- Package Marker 命名：`{模块名}Package`
- 自定义注解命名：`{模块名}Component`
- 配置类命名：`{模块名}Properties`, `{模块名}Configuration`
- 常量类命名：`{模块名}Constant`
- 示例：
  - ✅ `SimpleAkskPackage`, `SimpleAkskComponent`
  - ✅ `SmartKeywordSensitiveProperties`
  - ✅ `RegisteredClient`, `ClientType`
  - ❌ `RegisteredClients`, `ClientTypes`

### 1.3 常量命名
- 全部大写，下划线分隔：`ALL_UPPER_SNAKE_CASE`
- 示例：`DEFAULT_TOKEN_EXPIRY`, `CONFIG_PREFIX`, `CLIENT_TYPE_PLATFORM`

### 1.4 工具类命名
- **类名使用 `Helper` 后缀，不使用 `Util` 后缀**
- **包名使用 `support`**（见 1.1 包名规范）
- 示例：
  - ✅ `StringHelper`, `DateHelper`, `ValidationHelper`
  - ❌ `StringUtil`, `DateUtil`, `ValidationUtil`

---

## 2. Lombok 使用规范

### 2.1 强制使用 Lombok

**核心原则**：
- ✅ **强制使用 Lombok 注解**，禁止手动编写 getter/setter/toString/equals/hashCode 等方法
- ✅ **统一代码风格**，减少样板代码，提高可维护性
- ❌ **禁止手动编写**任何 Lombok 可以自动生成的方法

**Gradle 依赖**：
```gradle
dependencies {
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // 测试依赖
    testCompileOnly 'org.projectlombok:lombok'
    testAnnotationProcessor 'org.projectlombok:lombok'
}
```

---

### 2.2 Properties 类

**必需注解**：`@Data`

**说明**：
- `@Data` 自动生成 getter/setter/toString/equals/hashCode
- Properties 类通常需要完整的 POJO 功能

**示例**：
```java
@Data
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.auth.aksk")
public class SimpleAkskProperties {
    /**
     * 是否启用
     */
    private boolean enabled = true;

    /**
     * Token 过期时间（秒）
     */
    private long tokenExpiry = 3600;
}
```

---

### 2.3 枚举类

**必需注解**：`@Getter`

**说明**：
- 枚举类只需要 getter，不需要 setter
- 使用 `@Getter` 为枚举字段自动生成 getter 方法

**示例**：
```java
@Getter
public enum ClientType {
    /**
     * 平台客户端
     */
    PLATFORM("platform", "平台客户端"),

    /**
     * 第三方客户端
     */
    THIRD_PARTY("third_party", "第三方客户端");

    private final String code;
    private final String description;

    ClientType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    // fromCode 和 isValid 方法需要手动编写
    public static ClientType fromCode(String code) {
        // ...
    }
}
```

---

### 2.4 异常类

**必需注解**：`@Getter`

**说明**：
- 异常类通常只需要 getter，不需要 setter
- 异常字段在构造时设置，之后不应修改

**示例**：
```java
@Getter
public class SimpleAkskException extends RuntimeException {
    private final String errorCode;

    public SimpleAkskException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleAkskException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
```

---

### 2.5 测试类

**必需注解**：`@Slf4j`

**说明**：
- 测试类必须使用 `@Slf4j` 进行日志输出
- 禁止使用 `System.out.println()`

**示例**：
```java
@Slf4j
@SpringBootTest(classes = SimpleAkskTestApplication.class)
class ClientManagementServiceTest {

    @Test
    @DisplayName("测试客户端注册")
    void testRegisterClient() {
        log.info("开始测试客户端注册");
        // 测试逻辑
        log.info("客户端注册成功: {}", client);
    }
}
```

---

### 2.6 其他常用注解

**@Builder**：
- 用于需要构建器模式的类
- 适用于参数较多的不可变对象

```java
@Builder
@Getter
public class ClientRegistrationRequest {
    private final String clientId;
    private final String clientSecret;
    private final ClientType clientType;
}
```

**@NoArgsConstructor / @AllArgsConstructor**：
- `@NoArgsConstructor`：生成无参构造函数
- `@AllArgsConstructor`：生成全参构造函数
- 通常与 `@Data` 或 `@Builder` 配合使用

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientInfo {
    private String clientId;
    private String clientName;
    private ClientType clientType;
}
```

**@RequiredArgsConstructor**：
- 为 `final` 字段生成构造函数
- 适用于依赖注入场景

```java
@Service
@SimpleAkskComponent
@RequiredArgsConstructor
public class ClientManagementService {
    private final ClientRepository clientRepository;
    private final TokenGenerator tokenGenerator;
}
```

---

## 3. 模块结构规范

### 3.1 Package Marker（必需）

**位置**：模块根包下

**命名规则**：`{模块名}Package`（如 `SimpleAkskPackage`, `SmartKeywordSensitivePackage`）

```java
package io.github.surezzzzzz.sdk.{domain}.{module};

/**
 * {模块名} Package Marker
 *
 * @author surezzzzzz
 */
public interface {模块名}Package {
}
```

**示例**：
```java
package io.github.surezzzzzz.sdk.auth.aksk;

/**
 * Simple AKSK Package Marker
 *
 * @author surezzzzzz
 */
public interface SimpleAkskPackage {
}
```

**用途**：作为 `@ComponentScan` 的锚点，确保精准扫描。

---

### 3.2 自定义组件注解（必需）

**位置**：`annotation/{模块名}Component.java`

**命名规则**：`{模块名}Component`

```java
package io.github.surezzzzzz.sdk.{domain}.{module}.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {模块名} Component Annotation
 *
 * @author surezzzzzz
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface {模块名}Component {
}
```

**用途**：标记模块内部组件，配合 `@ComponentScan.Filter` 实现精准扫描，避免扫描到不相关的 Bean。

**使用示例**：
```java
@SimpleAkskComponent
@Service
public class ClientManagementService {
    // ...
}
```

---

### 3.3 标准目录结构

```
{模块名}-starter/
├── build.gradle                                        ← 子模块构建（仅写依赖）
├── version.properties                                  ← 版本文件
├── src/main/java/io/github/surezzzzzz/sdk/{domain}/{module}/
│   ├── {模块名}Package.java                            ← Package 标记（必需）
│   ├── annotation/
│   │   └── {模块名}Component.java                      ← 自定义注解（必需）
│   ├── configuration/
│   │   ├── {模块名}Properties.java                     ← 全局配置（单一）
│   │   └── {模块名}Configuration.java                  ← 自动配置
│   ├── constant/
│   │   ├── {模块名}Constant.java                       ← 常量
│   │   ├── ErrorCode.java                              ← 错误码
│   │   ├── ErrorMessage.java                           ← 错误信息
│   │   ├── {业务枚举1}.java                            ← 业务枚举
│   │   └── {业务枚举2}.java                            ← 业务枚举
│   ├── exception/
│   │   ├── {模块名}Exception.java                      ← 基础异常
│   │   ├── ConfigurationException.java                 ← 配置异常
│   │   └── {业务异常}.java                             ← 其他业务异常
│   ├── service/                                        ← 业务服务（标记自定义注解）
│   ├── repository/                                     ← 数据访问（可选）
│   ├── model/                                          ← 数据模型（可选）
│   ├── support/                                        ← 工具类（可选，使用Helper后缀）
│   └── ...
├── src/main/resources/
│   └── META-INF/
│       └── spring.factories                            ← Spring Boot 自动配置
```

**实际示例**：

`simple-aksk-starter`:
```
simple-aksk-starter/
├── src/main/java/io/github/surezzzzzz/sdk/auth/aksk/
│   ├── SimpleAkskPackage.java
│   ├── annotation/SimpleAkskComponent.java
│   ├── configuration/
│   │   ├── SimpleAkskProperties.java
│   │   └── SimpleAkskConfiguration.java
│   ├── constant/
│   │   ├── SimpleAkskConstant.java
│   │   ├── ErrorCode.java
│   │   ├── ErrorMessage.java
│   │   ├── ClientType.java                     ← 枚举
│   │   └── GrantType.java                      ← 枚举
│   └── ...
```

`smart-keyword-sensitive-starter`:
```
smart-keyword-sensitive-starter/
├── src/main/java/io/github/surezzzzzz/sdk/sensitive/keyword/
│   ├── SmartKeywordSensitivePackage.java
│   ├── annotation/SmartKeywordSensitiveComponent.java
│   ├── configuration/
│   │   ├── SmartKeywordSensitiveProperties.java
│   │   └── SmartKeywordSensitiveConfiguration.java
│   ├── constant/
│   │   ├── SmartKeywordSensitiveConstant.java
│   │   ├── ErrorCode.java
│   │   ├── ErrorMessage.java
│   │   ├── MaskType.java                       ← 枚举
│   │   └── SensitiveOrgType.java               ← 枚举
│   └── ...
```

---

### 3.4 数据类包规范

SDK开发中常用的数据类包括：`entity`、`model`、`request`、`response`。各包职责明确，命名规范如下：

#### 3.4.1 entity包 - 数据库实体

**用途**：专门用于数据库实体映射（JPA、MyBatis等ORM框架）

**命名规范**：
- 类名必须使用 `Entity` 后缀
- 例如：`UserEntity`、`ClientEntity`、`RegisteredClientEntity`

**示例**：
```java
package io.github.surezzzzzz.sdk.auth.aksk.server.entity;

@Entity
@Table(name = "registered_client")
public class RegisteredClientEntity {
    @Id
    private String id;
    private String clientId;
    private String clientSecret;
    // ...
}
```

#### 3.4.2 model包 - 业务模型

**用途**：用于业务逻辑层的领域模型、数据传输对象

**命名规范**：
- 不强制后缀，根据业务语义命名
- 例如：`TokenInfo`、`QueryIntent`、`Expression`、`FieldMetadata`

**示例**：
```java
package io.github.surezzzzzz.sdk.auth.aksk.server.model;

public class TokenInfo {
    private String accessToken;
    private String tokenType;
    private Integer expiresIn;
    // ...
}
```

#### 3.4.3 request包 - API请求参数

**用途**：用于API接口的请求参数封装

**命名规范**：
- 类名必须使用 `Request` 后缀
- 例如：`QueryRangeRequest`、`CreateUserRequest`、`LoginRequest`

**包位置**：
- 推荐放在 `controller` 包或 `endpoint` 包下
- request/response与controller紧密关联，放在一起更直观

**示例**：
```java
package io.github.surezzzzzz.sdk.prometheus.api.controller.request;

public class QueryRangeRequest {
    private String query;
    private Long start;
    private Long end;
    private String step;
    // ...
}
```

#### 3.4.4 response包 - API响应结果

**用途**：用于API接口的响应结果封装

**命名规范**：
- 类名必须使用 `Response` 后缀
- 例如：`QueryRangeResponse`、`UserInfoResponse`、`LoginResponse`

**包位置**：
- 推荐放在 `controller` 包或 `endpoint` 包下
- request/response与controller紧密关联，放在一起更直观

**示例**：
```java
package io.github.surezzzzzz.sdk.prometheus.api.controller.response;

public class QueryRangeResponse {
    private String status;
    private Data data;
    // ...
}
```

#### 3.4.5 包组织建议

根据模块复杂度，可以选择以下组织方式：

**方式1：controller包下组织**（推荐）
```
sdk/{domain}/{module}/
├── entity/
│   ├── UserEntity.java
│   └── ClientEntity.java
├── model/
│   ├── TokenInfo.java
│   └── ClientInfo.java
└── controller/
    ├── UserController.java
    ├── AdminController.java
    ├── request/
    │   ├── CreateUserRequest.java
    │   └── LoginRequest.java
    └── response/
        ├── UserInfoResponse.java
        └── LoginResponse.java
```

**方式2：endpoint包下组织**（适用于使用endpoint命名的模块）
```
sdk/{domain}/{module}/
├── entity/
│   ├── UserEntity.java
│   └── ClientEntity.java
├── model/
│   ├── TokenInfo.java
│   └── ClientInfo.java
└── endpoint/
    ├── UserEndpoint.java
    ├── AdminEndpoint.java
    ├── request/
    │   ├── CreateUserRequest.java
    │   └── LoginRequest.java
    └── response/
        ├── UserInfoResponse.java
        └── LoginResponse.java
```

**选择建议**：
- request/response优先放在controller或endpoint包下，与API接口定义放在一起
- entity包保持独立，用于数据库实体映射
- model包保持独立，用于业务逻辑层的领域模型

---

## 4. 配置类规范

### 4.1 全局 Properties 类（单一）

**命名规则**：`{模块名}Properties`

**要求**：
- 每个模块**有且仅有一个** Properties 类
- 所有默认值**必须引用常量**，不能硬编码
- 配置前缀引用常量
- 使用嵌套静态类组织配置结构

```java
package io.github.surezzzzzz.sdk.{domain}.{module}.configuration;

import io.github.surezzzzzz.sdk.{domain}.{module}.constant.{模块名}Constant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * {模块名} Properties
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties({模块名}Constant.CONFIG_PREFIX)
@ConditionalOnProperty(prefix = {模块名}Constant.CONFIG_PREFIX, name = "enable", havingValue = "true")
public class {模块名}Properties {

    /**
     * 是否启用（默认 false）
     */
    private boolean enable = false;

    /**
     * 服务器配置
     */
    private ServerConfig server = new ServerConfig();

    /**
     * 某个配置项（默认值引用常量）
     */
    private int someValue = {模块名}Constant.DEFAULT_SOME_VALUE;

    @Data
    public static class ServerConfig {
        /**
         * 服务端口（默认值引用常量）
         */
        private int port = {模块名}Constant.DEFAULT_SERVER_PORT;
    }
}
```

---

### 4.2 AutoConfiguration 类

**命名规则**：`{模块名}Configuration`

**要求**：
- 使用 Package Marker + 自定义注解实现精准扫描
- 配置前缀引用常量
- 使用 `@ConditionalOnProperty` 控制启用/禁用
- **Bean 定义策略**：
  - 优先使用自定义注解（`@{模块名}Component`）标记业务类，通过 `@ComponentScan` 自动扫描
  - 仅在无法使用自定义注解时（如第三方类、需要复杂初始化逻辑的类），才在 Configuration 类中使用 `@Bean` 方法定义

```java
package io.github.surezzzzzz.sdk.{domain}.{module}.configuration;

import io.github.surezzzzzz.sdk.{domain}.{module}.{模块名}Package;
import io.github.surezzzzzz.sdk.{domain}.{module}.annotation.{模块名}Component;
import io.github.surezzzzzz.sdk.{domain}.{module}.constant.{模块名}Constant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * {模块名} Auto Configuration
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({模块名}Properties.class)
@ComponentScan(
    basePackageClasses = {模块名}Package.class,
    includeFilters = @ComponentScan.Filter({模块名}Component.class)
)
@ConditionalOnProperty(prefix = {模块名}Constant.CONFIG_PREFIX, name = "enable", havingValue = "true")
public class {模块名}Configuration {

    @Bean
    @ConditionalOnMissingBean
    public SomeService someService({模块名}Properties properties) {
        log.info("Creating SomeService bean");
        return new SomeService(properties);
    }
}
```

**注册到 Spring Boot**：

`src/main/resources/META-INF/spring.factories`：
```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
io.github.surezzzzzz.sdk.{domain}.{module}.configuration.{模块名}Configuration
```

---

## 5. 常量规范

### 5.1 常量类结构

**命名规则**：`{模块名}Constant`

**要求**：
- Final class，private 构造函数
- 所有常量 `public static final`
- **绝对不能有硬编码字符串或魔法数字**
- 使用分组注释组织常量
- String 模板使用 `%s`, `%d`, `%.1f%%` 等格式占位符

```java
package io.github.surezzzzzz.sdk.{domain}.{module}.constant;

/**
 * {模块名} Constants
 *
 * @author surezzzzzz
 */
public final class {模块名}Constant {

    private {模块名}Constant() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置相关常量 ====================

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.{domain}.{module}";

    /**
     * 默认某值
     */
    public static final int DEFAULT_SOME_VALUE = 100;

    // ==================== 业务相关常量 ====================

    /**
     * 某业务常量
     */
    public static final String BUSINESS_CONSTANT = "value";

    // ==================== 模板常量 ====================

    /**
     * 模板：某格式 "prefix-%s-%s"
     * 参数: param1, param2
     */
    public static final String TEMPLATE_SOME_FORMAT = "prefix-%s-%s";
}
```

**实际示例**：
```java
// SimpleAkskConstant.java
public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.aksk";
public static final int DEFAULT_TOKEN_EXPIRY = 3600;
public static final String TEMPLATE_USER_CLIENT_ID = "user-%s-%s";

// SmartKeywordSensitiveConstant.java
public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.sensitive.keyword";
public static final String DEFAULT_MASK_CHAR = "*";
public static final int MIN_KEYWORD_LENGTH = 1;
```

---

### 5.2 ErrorCode 常量

**要求**：
- Final class，private 构造函数
- 错误码使用字符串格式：`"CONFIG_001"`, `"BIZ_001"`
- 分组管理

```java
package io.github.surezzzzzz.sdk.{domain}.{module}.constant;

/**
 * Error Code Constants
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置错误 ====================

    /**
     * 配置验证失败
     */
    public static final String CONFIG_VALIDATION_FAILED = "CONFIG_001";

    // ==================== 业务错误 ====================

    /**
     * 某业务错误
     */
    public static final String BUSINESS_ERROR = "BIZ_001";
}
```

---

### 5.3 ErrorMessage 常量

**要求**：
- 错误信息可包含格式占位符
- 与 ErrorCode 一一对应

```java
package io.github.surezzzzzz.sdk.{domain}.{module}.constant;

/**
 * Error Message Constants
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置错误 ====================

    public static final String CONFIG_VALIDATION_FAILED = "配置验证失败";

    // ==================== 业务错误 ====================

    public static final String BUSINESS_ERROR = "业务错误：%s";
}
```

---

### 5.4 枚举规范

**要求**：
- 统一提供 `code` 和 `description` 字段
- 提供静态方法：`fromCode()`, `isValid()`, `getAllCodes()`
- 使用 Lombok `@Getter`
- `toString()` 返回 code

```java
package io.github.surezzzzzz.sdk.{domain}.{module}.constant;

import lombok.Getter;

/**
 * {枚举名} Enum
 *
 * @author surezzzzzz
 */
@Getter
public enum {枚举名} {

    /**
     * 启用
     */
    ENABLED("enabled", "启用"),

    /**
     * 禁用
     */
    DISABLED("disabled", "禁用");

    private final String code;
    private final String description;

    {枚举名}(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 类型代码
     * @return 枚举，如果不存在返回 null
     */
    public static {枚举名} fromCode(String code) {
        if (code == null) {
            return null;
        }
        for ({枚举名} type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断类型代码是否有效
     *
     * @param code 类型代码
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有有效的类型代码
     *
     * @return 类型代码数组
     */
    public static String[] getAllCodes() {
        {枚举名}[] types = values();
        String[] codes = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            codes[i] = types[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
```

**实际示例**：
```java
@Getter
public enum ClientType {
    PLATFORM_LEVEL("platform", "平台级"),
    USER_LEVEL("user", "用户级");

    private final String code;
    private final String description;

    // ... fromCode(), isValid(), getAllCodes()
}
```

---

## 6. 自定义异常规范

### 6.1 基础异常类

**命名规则**：`{模块名}Exception`

**位置**：`exception/{模块名}Exception.java`

```java
package io.github.surezzzzzz.sdk.{domain}.{module}.exception;

import lombok.Getter;

/**
 * {模块名} Exception Base Class
 *
 * @author surezzzzzz
 */
@Getter
public class {模块名}Exception extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public {模块名}Exception(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public {模块名}Exception(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
```

---

### 6.2 具体异常类

**位置**：`exception/{具体异常名}Exception.java`

```java
package io.github.surezzzzzz.sdk.{domain}.{module}.exception;

/**
 * {具体异常} Exception
 *
 * @author surezzzzzz
 */
public class {具体异常}Exception extends {模块名}Exception {

    public {具体异常}Exception(String errorCode, String message) {
        super(errorCode, message);
    }

    public {具体异常}Exception(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
```

**常见异常类型**：
- `ConfigurationException`：配置异常
- `ValidationException`：验证异常
- 业务相关异常（如 `ClientException`, `MaskException`）

---

### 6.3 异常使用示例

```java
import io.github.surezzzzzz.sdk.{domain}.{module}.constant.ErrorCode;
import io.github.surezzzzzz.sdk.{domain}.{module}.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.{domain}.{module}.exception.{具体异常}Exception;

public class SomeService {

    public void validate(String value) {
        if (value == null) {
            throw new {具体异常}Exception(
                ErrorCode.VALIDATION_FAILED,
                String.format(ErrorMessage.VALIDATION_FAILED, "value")
            );
        }
    }
}
```

---

## 7. Gradle 构建规范

### 7.1 主 Gradle（根目录 build.gradle）

**职责**：
- 全局版本控制
- 统一依赖管理
- Maven 发布配置
- 从子模块的 `version.properties` 读取版本

**关键配置**：
```groovy
subprojects {
    // 检查是否有 src 目录，没有就跳过
    def hasSrcDir = file("${projectDir}/src").exists()
    if (!hasSrcDir) {
        logger.lifecycle("⊗ 跳过中间目录项目 ${project.path}")
        return
    }

    // 读取模块版本
    def versionFile = file("${projectDir}/version.properties")
    if (!versionFile.exists()) {
        throw new GradleException("未找到模块 '${project.path}' 的版本文件")
    }

    def props = new Properties()
    versionFile.withInputStream { props.load(it) }
    def moduleVersion = props.getProperty('version')

    // 保存到模块 ext
    project.ext.moduleVersion = moduleVersion
    logger.lifecycle("✓ 模块 ${project.path} 版本: ${moduleVersion}")

    // Maven 发布配置
    mavenPublishing {
        coordinates(group.toString(), project.name, project.ext.moduleVersion)
        // ...
    }
}
```

---

### 7.2 子模块 Gradle

**职责**：**仅写依赖**，其他配置均由主 Gradle 统一管理。

**位置**：`{模块名}-starter/build.gradle`

```groovy
dependencies {
    // 根据实际需要添加依赖

    // Spring Boot（如果需要）
    compileOnly 'org.springframework.boot:spring-boot-configuration-processor'
    compileOnly 'org.springframework.boot:spring-boot-autoconfigure'

    // 其他依赖
    implementation 'some.group:artifact:version'

    // 测试
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

**实际示例**：

`simple-aksk-starter/build.gradle`:
```groovy
dependencies {
    compileOnly 'org.springframework.boot:spring-boot-configuration-processor'
    compileOnly 'org.springframework.boot:spring-boot-autoconfigure'
    compileOnly 'javax.annotation:javax.annotation-api:1.3.2'

    implementation 'org.springframework.security:spring-security-oauth2-authorization-server'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

`smart-keyword-sensitive-starter/build.gradle`:
```groovy
dependencies {
    compileOnly 'org.springframework.boot:spring-boot-configuration-processor'
    compileOnly 'org.springframework.boot:spring-boot-autoconfigure'

    implementation 'com.hankcs:hanlp:portable-1.8.6'
    implementation 'com.hankcs:aho-corasick-double-array-trie:1.2.3'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

---

### 7.3 版本文件

**位置**：`{模块名}-starter/version.properties`

```properties
version=1.0.0
```

**说明**：
- 每个子模块必须有 `version.properties` 文件
- 主 Gradle 会读取此文件确定模块版本
- 发版时只需修改此文件

---

## 8. 硬编码零容忍规范

### 8.1 禁止事项

**绝对不能出现硬编码**，包括但不限于：

❌ **字符串字面量**：
```java
// 错误示例
if (type.equals("platform")) { ... }
String prefix = "io.github.surezzzzzz.sdk";
```

✅ **正确做法**：
```java
if (SomeEnum.PLATFORM.getCode().equals(type)) { ... }
String prefix = SomeConstant.CONFIG_PREFIX;
```

---

❌ **数字字面量**：
```java
// 错误示例
int expiry = 3600;
if (retryCount > 5) { ... }
```

✅ **正确做法**：
```java
int expiry = SomeConstant.DEFAULT_EXPIRY;
if (retryCount > SomeConstant.MAX_RETRY_COUNT) { ... }
```

---

❌ **拼接字符串**：
```java
// 错误示例
String id = "user-" + userId + "-" + randomId;
String errorMsg = "错误: " + message;
```

✅ **正确做法**：
```java
String id = String.format(SomeConstant.TEMPLATE_USER_ID, userId, randomId);
String errorMsg = String.format(ErrorMessage.SOME_ERROR, message);
```

---

### 8.2 例外情况

**仅以下情况可以不提取常量**：

1. **Controller Mapping路径**（可直接写在注解中）：
   ```java
   @RequestMapping("/admin")
   @GetMapping("/api/users/{id}")
   @PostMapping("/api/resource")
   @DeleteMapping("/api/resource/{id}")
   ```

   **说明**：路径直接写在映射注解中更加直观，易于理解API结构。

2. **重定向和模板路径**（可直接写在代码中）：
   ```java
   // 重定向路径
   return "redirect:/admin/login";
   return "redirect:/admin/create-success";

   // 模板引擎视图名称
   return "admin/index";
   return "user/profile";
   ```

   **说明**：这些路径在使用点直接写出更加清晰。如果某个路径被多次重用，可以考虑提取常量（但不强制）。

3. **日志输出**（动态内容，不影响业务逻辑）：
   ```java
   log.info("Processing request for user: {}", userId);
   log.debug("Operation completed successfully");
   ```

4. **测试代码**（测试数据可以硬编码）：
   ```java
   @Test
   void testSomething() {
       String testData = "test-123";
       // ...
   }
   ```

---

## 9. 代码注释规范

### 9.1 类注释

```java
/**
 * {类功能描述}
 *
 * @author surezzzzzz
 */
public class SomeClass {
    // ...
}
```

### 9.2 方法注释（公共方法必须）

```java
/**
 * {方法功能描述}
 *
 * @param param1 参数1说明
 * @param param2 参数2说明
 * @return 返回值说明
 * @throws SomeException 异常说明
 */
public String someMethod(String param1, int param2) {
    // ...
}
```

### 9.3 字段注释

```java
/**
 * {字段说明}
 */
private String field;

/**
 * 默认值（引用常量）
 */
private int value = SomeConstant.DEFAULT_VALUE;
```

---

## 10. 配置文件示例

**application.yml 结构**：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        {domain}:
          {module}:
            enable: true
            # 子配置
            sub-config:
              field: value
```

**实际示例**：

```yaml
# simple-aksk-starter
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            enable: true
            jwt:
              expires-in: 3600

# smart-keyword-sensitive-starter
io:
  github:
    surezzzzzz:
      sdk:
        sensitive:
          keyword:
            enable: true
            default-strategy:
              keep-region: true
```

---

### 10.1 敏感配置管理

对于包含敏感信息(如数据库密码、API密钥等)的配置,必须使用以下方式管理:

**1. application.yml(提交到 git)**:
- 包含公共配置和配置结构
- 敏感字段留空或注释掉
- 作为配置模板供团队参考

**2. application-local.yml(不提交到 git)**:
- 包含本地真实的敏感配置
- 每个开发者本地维护
- 添加到 `.gitignore` 忽略列表

**3. application-local.yml.example(提交到 git)**:
- 配置模板文件
- 提供配置示例和说明
- 帮助新开发者快速配置本地环境

**示例**:

```yaml
# application.yml(提交到git)
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/test_db?useUnicode=true&characterEncoding=UTF-8
    username: root
    # password: 请在 application-local.yml 中配置

# application-local.yml(不提交到git)
spring:
  datasource:
    password: your_real_password

# application-local.yml.example(提交到git)
spring:
  datasource:
    password: your_password_here  # 请修改为实际密码
```

**.gitignore 配置**:

在项目根目录的 `.gitignore` 中添加:
```
# 敏感配置文件
**/application-local.yml

# 证书和密钥文件
*.pem
*.crt
*.cer
*.key
*.p12
*.pfx
*.jks
*.keystore
```

---

## 11. 中间件相关规范

> **适用范围**：本章规范适用于使用中间件（如 Redis、MySQL 等）的 SDK 模块

### 11.1 Redis规范

> **适用范围**：使用 Redis 进行缓存的 SDK

#### 11.1.1 标准Key结构

Redis Key必须遵循以下结构，以支持多实例部署和Redis Cluster：

```
{sdk-prefix}:{business-type}:{me}::{data-id}
```

**示例**:
```
sure-auth-aksk:oauth2:authorization:test-app::{abc-123}
sure-cache:user:prod-server::{user-456}
```

#### 11.1.2 各部分说明

| 组成部分 | 说明 | 示例 | 要求 |
|---------|------|------|------|
| sdk-prefix | SDK标识前缀，标识哪个SDK | `sure-auth-aksk`、`sure-cache` | 业务驱动，保持描述性，不强制缩短 |
| business-type | 业务类型，标识数据类型 | `oauth2:authorization`、`cache:user` | 清晰描述业务含义 |
| me | 应用实例标识 | `test-app`、`prod-server` | 可配置，用于区分多实例 |
| data-id | 数据唯一标识 | `{abc-123}`、`{user-456}` | 使用`{}`包裹以支持Redis Cluster |

#### 11.1.3 配置结构

`me`参数应该配置在`RedisConfig`级别，而不是嵌套在具体的缓存类型配置中：

```java
@Data
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.xxx")
public class XxxProperties {

    private RedisConfig redis = new RedisConfig();

    @Data
    public static class RedisConfig {
        /**
         * 是否启用Redis缓存
         */
        private Boolean enabled = false;

        /**
         * 应用实例标识，用于区分多个应用实例共用Redis的场景
         * 默认值: "default"
         */
        private String me = "default";

        //其他Redis相关配置...
    }
}
```

**配置示例** (`application.yml`):
```yaml
io:
  github:
    surezzzzzz:
      sdk:
        xxx:
          redis:
            enabled: true
            me: my-app-instance  # 应用实例标识
```

#### 11.1.4 实现示例

**1. 定义常量**:

```java
public final class XxxConstant {
    /**
     * Redis Key前缀模板
     * 参数: application name (me)
     */
    public static final String REDIS_KEY_PREFIX_TEMPLATE = "sure-xxx:%s:";

    /**
     * 缓存名称（业务类型）
     */
    public static final String CACHE_USER = "cache:user";
    public static final String CACHE_SESSION = "cache:session";
}
```

**2. 配置Redis CacheManager**:

```java
@Configuration
@EnableCaching
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.xxx.redis",
                       name = "enabled", havingValue = "true")
public class RedisConfiguration {

    private final XxxProperties properties;

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 获取应用标识
        String me = properties.getRedis().getMe();

        // 获取TTL配置
        Integer ttlSeconds = properties.getTtl();

        // 配置key前缀: sure-xxx:{me}:
        String keyPrefix = String.format(XxxConstant.REDIS_KEY_PREFIX_TEMPLATE, me);

        // 配置缓存
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(ttlSeconds))
                .computePrefixWith(cacheName -> keyPrefix + cacheName + "::")
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .transactionAware()
                .build();
    }
}
```

**3. 使用Spring Cache注解**:

```java
@Service
@RequiredArgsConstructor
public class CachedUserService {

    private final UserService delegate;

    /**
     * 查询用户，优先从缓存读取
     * Redis Key: sure-xxx:cache:user:{me}::{userId}
     */
    @Cacheable(value = XxxConstant.CACHE_USER,
               key = "'{' + #userId + '}'",
               unless = "#result == null")
    public User findById(String userId) {
        return delegate.findById(userId);
    }

    /**
     * 更新用户，同时更新缓存
     */
    @CachePut(value = XxxConstant.CACHE_USER, key = "'{' + #user.id + '}'")
    public User update(User user) {
        return delegate.update(user);
    }

    /**
     * 删除用户，同时清除缓存
     */
    @CacheEvict(value = XxxConstant.CACHE_USER,
                key = "'{' + #userId + '}'")
    public void delete(String userId) {
        delegate.delete(userId);
    }
}
```

#### 11.1.5 Redis Cluster支持

**Hash Tag机制**:

Redis Cluster使用Hash Tag来决定key分配到哪个slot。Hash Tag是key中第一个`{}`包裹的部分。

```java
//✅ 正确：使用 {id} 作为Hash Tag
@Cacheable(value = CACHE_USER, key = "'{' + #userId + '}'")
//生成的key: sure-xxx:cache:user:my-app::{user-123}
// Hash Tag: {user-123}

// ❌ 错误：没有使用 {}
@Cacheable(value = CACHE_USER, key = "#userId")
// 生成的key: sure-xxx:cache:user:my-app::user-123
// 整个key都参与hash计算，无法保证相关key在同一slot
```

**为什么需要Hash Tag**:
- 确保相关的key分配到同一个slot
- 支持Redis Cluster的multi-key操作（如MGET、事务）
- 提高缓存命中率和性能

#### 11.1.6 最佳实践

1. **SDK前缀命名**: 使用清晰的业务描述，不强制缩短（如`sure-auth-aksk`而不是`aksk`）
2. **业务类型命名**: 使用冒号分隔的层级结构（如`oauth2:authorization:token`）
3. **me参数配置**: 在RedisConfig级别配置，适用于所有缓存类型
4. **Hash Tag使用**: 始终使用`'{' + #id + '}'`格式确保Redis Cluster支持
5. **TTL配置**: 根据业务需求配置合理的过期时间
6. **空值处理**: 使用`unless = "#result == null"`避免缓存空值

---

### 11.2 MySQL规范

> **适用范围**：需要连接数据库（如 MySQL）的 SDK 模块

#### 11.2.1 SQL文件组织结构

**位置**: 放在模块的 `docs/` 目录下

**文件命名**: 使用序号前缀标识执行顺序

**文件分类**:
```
{模块名}-starter/
├── docs/
│   ├── README.md                    ← 执行说明文档
│   ├── 00_database.sql              ← 数据库创建脚本
│   ├── 01_schema.sql                ← 表结构定义
│   ├── 02_init_data.sql             ← 必要的初始数据
│   └── 03_test_data.sql             ← 可选的测试数据
```

**README.md 内容**:
- SQL 文件执行顺序说明
- 数据库配置要求
- 测试数据说明

#### 11.2.2 SQL文件编写规范

**字符集要求**:
- 统一使用 `utf8mb4` 字符集
- 确保支持完整的 Unicode 字符（包括 emoji）

**建表规范**:

✅ **必须要求**:
- 使用 `DROP TABLE IF EXISTS` 避免重复创建错误
- **所有字段必须添加 COMMENT 注释**
- **表也必须添加 COMMENT 注释**
- 主键、索引必须明确定义
- 使用 `ENGINE=InnoDB` 存储引擎

**完整示例**:

```sql
-- 使用 DROP TABLE IF EXISTS 避免重复创建错误
DROP TABLE IF EXISTS `oauth2_registered_client`;

CREATE TABLE `oauth2_registered_client` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `client_id` VARCHAR(100) NOT NULL COMMENT '客户端ID',
    `client_secret` VARCHAR(200) NOT NULL COMMENT '客户端密钥',
    `client_name` VARCHAR(200) NOT NULL COMMENT '客户端名称',
    `client_type` VARCHAR(50) NOT NULL COMMENT '客户端类型：PLATFORM-平台客户端，THIRD_PARTY-第三方客户端',
    `grant_types` VARCHAR(500) NOT NULL COMMENT '授权类型，多个用逗号分隔',
    `redirect_uris` TEXT COMMENT '重定向URI，多个用逗号分隔',
    `scopes` VARCHAR(500) COMMENT '授权范围，多个用逗号分隔',
    `token_expiry` INT NOT NULL DEFAULT 3600 COMMENT 'Token过期时间（秒）',
    `refresh_token_expiry` INT NOT NULL DEFAULT 86400 COMMENT 'Refresh Token过期时间（秒）',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_client_id` (`client_id`),
    KEY `idx_client_type` (`client_type`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2客户端注册表';
```

**字段注释规范**:
- 主键字段：说明用途（如"主键ID"）
- 业务字段：说明字段含义和业务用途
- 枚举字段：列出所有可能的值及其含义（如"客户端类型：PLATFORM-平台客户端，THIRD_PARTY-第三方客户端"）
- 时间字段：说明时间含义（如"创建时间"、"更新时间"）
- 布尔字段：说明0和1的含义（如"是否启用：0-禁用，1-启用"）

**密码处理**:
- 生产数据：使用 `{bcrypt}` 前缀 + BCrypt 加密密码
- 测试数据：使用 `{noop}` 前缀 + 明文密码（仅用于测试）
- ❌ **禁止使用弱密码**：即使是测试数据，也不能使用弱密码（如 `123456`、`password`、`test123`、`admin123` 等）
- ✅ **建议使用相对复杂的测试密码**：包含字母、数字、特殊字符的组合

```sql
-- 生产数据示例
INSERT INTO `oauth2_user` (`username`, `password`, `enabled`)
VALUES ('admin', '{bcrypt}$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1);

-- 测试数据示例（使用相对复杂的密码）
INSERT INTO `oauth2_user` (`username`, `password`, `enabled`)
VALUES ('test_user', '{noop}Test@2024#User', 1);

-- ❌ 错误示例：不要使用弱密码
-- VALUES ('test_user', '{noop}123456', 1);
-- VALUES ('test_user', '{noop}test123', 1);
```

#### 11.2.3 数据库配置规范

**使用真实数据库**:
- ✅ 使用真实的 MySQL 数据库进行测试
- ❌ 不使用 H2 内存数据库（避免方言差异导致的问题）

**JDBC URL 配置**:

必须包含完整的连接参数：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/test_db?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    # password: 请在 application-local.yml 中配置（见 10.1 敏感配置管理）
```

**参数说明**:
- `useUnicode=true&characterEncoding=UTF-8`：确保字符编码正确
- `useSSL=false`：测试环境禁用 SSL（生产环境应启用）
- `serverTimezone=Asia/Shanghai`：设置时区
- `allowPublicKeyRetrieval=true`：允许客户端从服务器获取公钥

**JPA 配置**（如果使用 JPA）:
```yaml
spring:
  jpa:
    database-platform: org.hibernate.dialect.MySQL8Dialect
    hibernate:
      ddl-auto: create-drop  # 测试时自动创建和删除表
    show-sql: true           # 显示 SQL 语句（便于调试）
```

**注意事项**:
- `ddl-auto: create-drop` 仅用于测试环境
- 生产环境必须使用 `ddl-auto: none` 或 `validate`
- 敏感配置（如密码）必须使用 `application-local.yml` 管理（见 [10.1 敏感配置管理](#101-敏感配置管理)）

---

## 12. 测试规范

### 12.1 测试目录结构

> **包名规范**：测试代码的包名规范遵循 [1.1 包名规范](#11-包名规范)

```
{模块名}-starter/
├── src/test/java/io/github/surezzzzzz/sdk/{domain}/{module}/test/
│   ├── {模块名}TestApplication.java        ← 测试启动类
│   └── cases/                              ← 测试用例目录
│       ├── SomeFeatureTest.java
│       ├── AnotherFeatureTest.java
│       └── ...
└── src/test/resources/
    └── application.yml                     ← 测试配置（唯一）
```

**实际示例**：

`smart-keyword-sensitive-starter`:
```
smart-keyword-sensitive-starter/
├── src/test/java/io/github/surezzzzzz/sdk/sensitive/keyword/test/
│   ├── SmartKeywordSensitiveTestApplication.java
│   └── cases/
│       ├── KeywordSensitiveMaskHelperTest.java
│       ├── HashMaskTest.java
│       └── PlaceholderMaskTest.java
└── src/test/resources/
    └── application.yml
```

---

### 12.2 TestApplication 规范

**位置**：`src/test/java/io/github/surezzzzzz/sdk/{domain}/{module}/test/{模块名}TestApplication.java`

**要求**：
- 使用 `@SpringBootApplication` 注解
- 提供 `main` 方法（可选，但推荐）
- 保持简单，不添加额外配置

```java
package io.github.surezzzzzz.sdk.{domain}.{module}.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test Application
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class {模块名}TestApplication {
    public static void main(String[] args) {
        SpringApplication.run({模块名}TestApplication.class, args);
    }
}
```

**实际示例**：
```java
// SmartKeywordSensitiveTestApplication.java
@SpringBootApplication
public class SmartKeywordSensitiveTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartKeywordSensitiveTestApplication.class, args);
    }
}
```

---

### 12.3 测试类规范

**位置**：`src/test/java/io/github/surezzzzzz/sdk/{domain}/{module}/test/cases/{测试类名}.java`

**类命名规则**：
- 根据实际测试内容灵活命名
- 常见命名：`{功能名}Test`, `{Helper名}Test`, `{场景名}Test`
- 示例：`KeywordSensitiveMaskHelperTest`, `HashMaskTest`, `Ipv4SensitiveHelperTest`

**方法命名规则**：
- ✅ **使用驼峰命名法（camelCase）**
- ❌ **禁止使用下划线分隔**
- 示例：
  - ✅ `testSomeFeature()`, `shouldReturnTrueWhenValid()`
  - ❌ `test_some_feature()`, `should_return_true_when_valid()`

**必需注解**：
- `@Slf4j`：用于日志输出
- `@SpringBootTest(classes = {模块名}TestApplication.class)`：指定测试启动类

**可选注解**：
- `@DisplayName`：描述测试用例
- `@BeforeEach` / `@AfterEach`：初始化/清理测试数据

**基础模板**：

```java
@Slf4j
@SpringBootTest(classes = {模块名}TestApplication.class)
class SomeFeatureTest {

    @Autowired
    private SomeHelper helper;

    @Test
    @DisplayName("测试某功能")
    void testSomeFeature() {
        // 准备测试数据
        String input = "test input";

        // 执行测试
        String result = helper.doSomething(input);

        // 打印日志（断言前必须打log）
        log.info("输入: {}", input);
        log.info("输出: {}", result);

        // 断言
        assertNotNull(result, "结果不应为空");
    }
}
```

---

### 12.4 测试数据和配置规范

**配置文件（application.yml）**：

放在 `src/test/resources/application.yml`，包含：
- SDK 依赖的中间件配置（如 Redis、Elasticsearch 等）
- SDK 自身的配置项（如 `io.github.surezzzzzz.sdk.*`）
- 日志配置（如 `logging.level.*`）

**测试数据**：

- 简单测试数据：直接在测试方法中定义
- 复杂测试数据：使用 `@BeforeEach` 初始化，`@AfterEach` 清理
- 避免使用 `@ActiveProfiles`，只维护一个 `application.yml`

**示例**：

```yaml
# application.yml
io:
  github:
    surezzzzzz:
      sdk:
        sensitive:
          keyword:
            enable: true
            keywords:
              - keyword: "测试关键词"

logging:
  level:
    io.github.surezzzzzz.sdk: DEBUG
```

**敏感配置管理**：

对于包含敏感信息（如数据库密码、API密钥等）的测试配置，请参考 [10.1 敏感配置管理](#101-敏感配置管理)。

---

### 12.5 日志和断言规范

**日志规范**：

1. **必须打印关键输入输出**：让测试结果可视化，方便排查问题
2. **断言前必须打log**：确保错误信息可见
3. **格式不强制**：清晰即可，可以使用分隔线等方式增强可读性

**断言规范**：

1. **所有测试都需要断言**：验证测试结果的正确性
2. **断言前先打log**：让错误可视化
3. **提供断言失败消息**：使用断言的第二个参数提供错误描述

**示例**：

```java
@Test
void testMask() {
    String input = "192.168.1.1";
    String result = helper.mask(input);

    // 打印日志（断言前必须打log）
    log.info("======================================");
    log.info("测试IP脱敏");
    log.info("输入: {}", input);
    log.info("输出: {}", result);
    log.info("======================================");

    // 断言
    assertNotNull(result, "脱敏结果不应为空");
    assertTrue(result.contains("*"), "脱敏结果应包含*");
}
```

---

### 12.6 Gradle 依赖配置

在 `build.gradle` 的 `dependencies` 块中添加测试依赖：

```groovy
dependencies {
    // ... 其他依赖

    // 测试依赖
    testImplementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-log4j2'
}
```

**说明**：
- `spring-boot-starter-web`：提供 Spring MVC 和 Web 相关功能
- `spring-boot-starter-test`：提供 JUnit 5、Mockito 等测试框架
- `spring-boot-starter-log4j2`：使用 Log4j2 作为日志框架（统一使用）

---

### 12.7 测试覆盖要求

**核心要求**：

1. **端到端测试覆盖所有场景**：
   - 正常场景：常规输入和预期输出
   - 异常场景：错误输入、边界条件、null 值等
   - 边界场景：极限值、特殊字符等

2. **不需要单独的异常测试类**：异常测试混合在功能测试中

3. **测试方法粒度灵活**：根据实际情况选择细粒度或粗粒度

4. **测试独立性**：测试方法尽量自闭环，避免相互依赖

---

### 12.8 数据库测试规范

> **适用范围**：本节规范仅适用于需要连接数据库（如 MySQL）的 SDK 模块

**MySQL相关规范**：

数据库相关的规范（SQL文件组织、表设计、配置等）请参考 [11.2 MySQL规范](#112-mysql规范)，包括：
- SQL文件组织结构（docs/目录、文件命名）
- SQL文件编写规范（字符集、建表规范、字段注释、密码处理）
- 数据库配置规范（JDBC URL、JPA配置）

**测试注意事项**：
- 使用真实的 MySQL 数据库进行测试，不使用 H2 内存数据库
- 敏感配置（如密码）使用 `application-local.yml` 管理（见 [10.1 敏感配置管理](#101-敏感配置管理)）

---

## 13. 总结

### 核心原则

1. ✅ **Package Marker + 自定义注解**：精准扫描，避免污染
2. ✅ **单一全局 Properties 类**：集中管理配置
3. ✅ **硬编码零容忍**：所有字符串、数字必须提取为常量
4. ✅ **统一异常体系**：基础异常 + 具体异常
5. ✅ **命名单数化**：包名、类名全部使用单数
6. ✅ **主子 Gradle 分离**：子 Gradle 只写依赖，版本由 version.properties 管理
7. ✅ **枚举标准化**：提供 code/description/fromCode/isValid 方法
8. ✅ **测试覆盖所有场景**：正常、异常、边界场景全覆盖，测试粒度灵活选择
9. ✅ **测试自闭环**：测试方法尽量独立，避免相互依赖
10. ✅ **测试可视化**：必须打log展示关键输入输出，断言前先打log让错误可视化
11. ✅ **测试配置分离**：中间件配置、SDK配置、log配置放yml，测试数据放代码里
12. ✅ **测试简单直接**：不使用 @ActiveProfiles，只维护一个 application.yml
13. ✅ **敏感配置分离**：敏感信息使用application-local.yml管理，不提交到git，证书密钥文件加入.gitignore
14. ✅ **Redis Key标准化**（仅Redis SDK）：使用标准Key结构`{sdk-prefix}:{business-type}:{me}::{data-id}`，支持多实例和Redis Cluster
15. ✅ **数据库规范化**（仅数据库SDK）：SQL字段必须有COMMENT注释，使用utf8mb4字符集，禁止弱密码

### 检查清单

在提交代码前，请确认以下规范：

**主体代码**：

- [ ] 包名、类名全部使用单数（主体和测试统一遵循，见 [1.1 包名规范](#11-包名规范)）
- [ ] Package Marker 和自定义注解已创建（命名规范：`{模块名}Package`, `{模块名}Component`）
- [ ] Properties 类只有一个（命名规范：`{模块名}Properties`）
- [ ] 所有默认值引用常量
- [ ] 无硬编码字符串或数字（API 路径和日志除外）
- [ ] 所有常量在 Constant 类中定义（命名规范：`{模块名}Constant`）
- [ ] ErrorCode 和 ErrorMessage 一一对应
- [ ] 枚举提供标准方法（fromCode/isValid/getAllCodes）
- [ ] 自定义异常体系完整（基础异常命名：`{模块名}Exception`）
- [ ] 子 Gradle 仅包含依赖声明
- [ ] version.properties 文件存在
- [ ] Configuration 类使用 Package Marker + 自定义注解扫描（命名规范：`{模块名}Configuration`）

**测试代码**：

- [ ] TestApplication 在 `test` 包下（命名规范：`{模块名}TestApplication`）
- [ ] 测试用例在 `test/cases` 包下
- [ ] 测试配置在 `src/test/resources/application.yml`
- [ ] 使用 `@SpringBootApplication` 注解
- [ ] 测试类使用 `@Slf4j` 注解
- [ ] 测试类使用 `@SpringBootTest(classes = {模块名}TestApplication.class)` 注解
- [ ] 测试方法使用 `@Test` 注解
- [ ] 中间件配置、SDK配置、log配置放在 `application.yml`
- [ ] 测试数据在测试类中准备
- [ ] 不使用 `@ActiveProfiles`
- [ ] 所有测试都打印关键输入输出
- [ ] 断言前必须打log
- [ ] 所有测试都有断言
- [ ] 添加 `spring-boot-starter-test` 依赖
- [ ] 添加 `spring-boot-starter-log4j2` 依赖
- [ ] 覆盖所有正常场景
- [ ] 覆盖所有异常场景
- [ ] 覆盖所有边界场景
- [ ] 敏感配置使用application-local.yml管理（如数据库密码）
- [ ] .gitignore已添加敏感文件忽略规则（application-local.yml、证书密钥文件）

**数据库相关**（仅数据库SDK）：

- [ ] SQL文件放在docs/目录下
- [ ] SQL文件使用序号前缀命名（00_、01_、02_等）
- [ ] 所有表字段都有COMMENT注释
- [ ] 表也有COMMENT注释
- [ ] 使用utf8mb4字符集
- [ ] 使用DROP TABLE IF EXISTS避免重复创建错误
- [ ] 生产数据密码使用{bcrypt}前缀
- [ ] 测试数据密码使用{noop}前缀
- [ ] 禁止使用弱密码（即使是测试数据）
- [ ] JDBC URL包含完整参数
- [ ] 使用真实MySQL数据库测试（不用H2）

**Redis相关**（仅Redis SDK）：

- [ ] Redis Key使用标准结构：`{sdk-prefix}:{business-type}:{me}::{data-id}`
- [ ] `me`参数在RedisConfig级别配置（不在具体缓存类型配置中）
- [ ] 使用`'{' + #id + '}'`格式支持Redis Cluster Hash Tag
- [ ] SDK前缀保持描述性（业务驱动，不强制缩短）
- [ ] 业务类型使用冒号分隔的层级结构
- [ ] 配置了合理的TTL过期时间
- [ ] 使用`unless = "#result == null"`避免缓存空值



