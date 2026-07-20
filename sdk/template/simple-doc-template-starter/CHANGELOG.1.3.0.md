# Changelog

## [1.3.0] - 2026-07-20

### Compatibility

- **POI 运行时依赖治理**
  - doc-template 继续以 Apache POI 5.2.2 作为编译和主线验证基线
  - starter 不再向业务传递或强制 `poi-ooxml:5.2.2`，由应用维护单一、完整的 POI runtime
  - 本次发布源于已使用 POI 3.17 的应用集成场景；新增同 JVM 运行时兼容测试，显式断言实际 POI 版本，避免依赖解析后版本漂移导致假通过

- **编号与样式跨版本降级**
  - `NumberingProcessor` 优先反射调用 POI 5.x 的公开编号集合方法；POI 3.17 下回退读取对应内部集合
  - 编号集合无法读取时保持既有安全降级语义：列表前缀为空，不中断文档渲染
  - `WordStyleResolver` 保留现有样式内部结构反射与降级机制，说明调整为跨 POI 版本内部实现兼容

### Dependency Contract

- Apache POI 由应用维护单一 runtime；starter 不内置、shade、relocate 或 force POI
- 主线运行时为 POI 5.2.2；本版本增加对 POI 3.17 运行时的兼容验证
- 不支持在同一应用中混用不同 POI 版本的 `poi`、`poi-ooxml`、OOXML schemas 或 XMLBeans 组件
- 其他 POI 版本不属于本版本的兼容性承诺

### Compatibility Notes

- 不改变模板语法、公共渲染 API、配置 key、错误码或 PDF 输出行为
- 应用需显式提供与其依赖图一致的完整 POI runtime
- Spring Boot 2.7.9 作为主线验证基线，并兼容验证 Spring Boot 2.4.5

### Validation

- Spring Boot 2.7.9 + POI 5.2.2 完整模块测试：通过
- Spring Boot 2.7.9 + POI 3.17 运行时完整模块测试：通过
- Spring Boot 2.4.5 + POI 5.2.2 完整模块测试：通过
- Spring Boot 2.4.5 + POI 3.17 运行时完整模块测试：通过
