# [1.0.1] - 2026-01-28

## 依赖优化

### 移除未使用的依赖
- **问题**: 模块依赖了 `simple-aksk-core:1.0.0`，但实际代码中未使用
- **影响**: 引入不必要的传递依赖，增加项目体积
- **修复**: 从 `build.gradle` 中移除 `simple-aksk-core` 依赖
- **验证**: 所有测试通过（132个测试，100%通过率）

### 模块依赖结构优化

**优化前**:
```
security-context-starter
  ├── simple-aksk-core (未使用)
  └── simple-aksk-resource-core
```

**优化后**:
```
security-context-starter
  └── simple-aksk-resource-core
      ├── SimpleAkskSecurityContextProvider (接口)
      ├── SimpleAkskSecurityAspect (切面)
      └── 权限注解 (@RequireContext, @RequireField, etc.)
```

**Provider 架构**:
- `AkskUserContextProvider` 实现 `SimpleAkskSecurityContextProvider` 接口
- 适配 `SimpleAkskSecurityContextHelper` 的静态方法
- 用于 `SimpleAkskSecurityAspect` 的依赖注入
- 从 HTTP Headers 获取上下文数据

## 兼容性

✅ **完全向后兼容** - 此版本仅优化依赖结构，不涉及任何 API 变更或功能调整

## 升级指南

从 1.0.0 升级到 1.0.1：

```gradle
dependencies {
    // 旧版本
    // implementation 'io.github.sure-zzzzzz:simple-aksk-security-context-starter:1.0.0'

    // 新版本
    implementation 'io.github.sure-zzzzzz:simple-aksk-security-context-starter:1.0.1'
}
```

无需修改任何代码或配置，直接升级即可。
