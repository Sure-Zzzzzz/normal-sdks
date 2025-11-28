# S3 SDK

S3对象存储基础SDK模块，提供S3相关的基础数据模型和实体定义。

## 功能概述

本模块是S3对象存储服务的基础模块，主要包含：

- **S3事件模型**: 定义S3相关的事件数据结构
- **基础实体**: 提供S3操作所需的基础实体类
- **数据模型**: 定义S3对象存储相关的数据模型

## 模块结构

```
s3/
├── src/
│   └── main/
│       └── java/
│           └── io/
│               └── github/
│                   └── surezzzzzz/
│                       └── sdk/
│                           └── oss/
│                               └── s3/
│                                   └── data/
│                                       └── model/
│                                           └── entity/
│                                               └── S3Event.java
├── build.gradle
├── version.properties
└── README.md
```

## 数据模型

### S3Event

`S3Event`是本模块目前提供的核心数据模型，用于表示S3相关的事件信息。

```java
package io.github.surezzzzzz.sdk.oss.s3.data.model.entity;

/**
 * S3事件实体类
 * 用于封装S3对象存储相关的事件数据
 */
public class S3Event {
    // 事件相关属性和方法
}
```

## 使用方式

本模块主要作为`s3-client-starter`模块的基础依赖，提供数据模型支持。

### 添加依赖

在`build.gradle`中添加依赖：

```gradle
dependencies {
    implementation project(':sdk:oss:s3')
}
```

### 使用数据模型

```java
import io.github.surezzzzzz.sdk.oss.s3.data.model.entity.S3Event;

public class YourService {
    
    public void handleS3Event(S3Event event) {
        // 处理S3事件
        // 可以根据实际需求扩展S3Event的功能
    }
}
```

## 扩展开发

本模块设计为轻量级的基础模块，您可以根据实际需求：

1. **扩展现有模型**: 在`S3Event`中添加更多属性和方法
2. **添加新模型**: 在`data.model.entity`包下创建新的数据模型
3. **添加枚举类**: 定义S3相关的枚举类型
4. **添加工具类**: 提供数据模型的工具类支持

### 扩展示例

```java
// 添加新的数据模型
package io.github.surezzzzzz.sdk.oss.s3.data.model.entity;

public class S3ObjectMetadata {
    private String bucketName;
    private String objectKey;
    private long size;
    private String contentType;
    private Map<String, String> metadata;
    
    // getter和setter方法
}

// 添加枚举类
package io.github.surezzzzzz.sdk.oss.s3.data.model.enums;

public enum S3EventType {
    OBJECT_CREATED,
    OBJECT_DELETED,
    BUCKET_CREATED,
    BUCKET_DELETED
}
```

## 依赖说明

本模块作为基础模块，依赖非常轻量：

- **无外部依赖**: 仅依赖Java标准库
- **可扩展性**: 设计上支持灵活扩展

## 版本管理

版本信息定义在`version.properties`文件中：

```properties
version=1.0.0
```

## 构建配置

`build.gradle`配置：

```gradle
plugins {
    id 'java-library'
}

group = 'io.github.surezzzzzz.sdk.oss'
version = '1.0.0'

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    // 无外部依赖，保持轻量级
}
```

## 注意事项

1. **保持轻量**: 本模块设计为轻量级基础模块，避免添加过多复杂逻辑
2. **向前兼容**: 扩展数据模型时，注意保持向前兼容性
3. **文档完善**: 添加新模型时，请同步更新相关文档
4. **测试覆盖**: 扩展功能时，建议添加相应的单元测试

## 维护与支持

本模块作为S3对象存储服务的基础模块，由开发团队统一维护。如有扩展需求或问题，请联系开发团队。