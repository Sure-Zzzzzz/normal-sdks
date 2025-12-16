# Changelog

## [1.0.2]

### ✨ 新增功能

#### 路径匹配规则缓存

拦截器模式下，对路径匹配结果进行缓存，避免重复执行 AntPathMatcher 计算，提升性能。

- 使用 ConcurrentHashMap 实现零依赖缓存
- 自动缓存匹配成功和未匹配的路径
- 服务重启后自动重建缓存

#### 配置验证

应用启动时自动验证配置项，快速失败，避免运行时错误。

验证项包括：
- `me`：服务标识（非空，长度 ≤ 50）
- `mode`：运行模式（annotation/interceptor/both）
- `count`、`window`：限流规则参数（> 0）
- `keyStrategy`：key生成策略（method/path/path-pattern/ip）
- `fallback`：降级策略（allow/deny）
- `commandTimeout`：Redis超时（> 0）
- `pathPattern`：拦截器路径模式（非空）

### 📝 兼容性

完全向后兼容 1.0.1 版本，无需修改现有配置。
