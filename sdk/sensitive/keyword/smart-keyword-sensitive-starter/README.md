# Smart Keyword Sensitive Starter

基于NLP和规则引擎的智能关键词脱敏SDK，专为组织机构名称脱敏设计，支持自动识别、元信息提取、智能降级和兜底保护。

## 核心特性

- **双引擎识别**：NLP语义理解 + 规则引擎，自动识别组织机构
- **智能元信息提取**：自动提取地域、行业、品牌、组织类型等元信息
- **三种脱敏策略**：星号掩码、占位符、哈希值，适应不同安全等级需求
- **三级智能降级**：针对金融/政府/教育机构，动态调整保留率以符合安全阈值
- **兜底脱敏机制**：确保所有文本都能被正确脱敏，无遗漏
- **智能括号处理**：自动识别和脱敏括号内的分支机构信息
- **详细脱敏追踪**：提供完整的元信息提取和脱敏决策过程日志

---

## 快速开始

### 1. 添加依赖

```gradle
implementation 'io.github.surezzzzzz:smart-keyword-sensitive-starter:1.0.0'
```

### 2. 最简配置

在 `application.yml` 中添加（启用即可，使用默认配置）：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        sensitive:
          keyword:
            enable: true
```

### 3. 极简使用

```java
@Autowired
private KeywordSensitiveMaskHelper helper;

// 单个脱敏
String masked = helper.mask("北京科技有限公司");
// 输出: **科技有限公司

// 批量脱敏
List<String> maskedList = helper.batchMask(Arrays.asList(
    "上海金融控股有限公司",
    "深圳互联网科技有限公司"
));

// 脱敏并返回详情（包含元信息、降级过程等）
MaskResultDetail detail = helper.maskWithDetail("东方大国银行股份有限公司");
System.out.println("原文: " + detail.getOriginal());
System.out.println("脱敏后: " + detail.getMasked());
System.out.println("详细原因: " + detail.getReason());
```

---

## 完整的脱敏流程

### 流程图

```
输入文本 "北京科技有限公司"
    ↓
┌─────────────────────────────────────────────────────────────────┐
│ 步骤1: 实体识别（CompositeEntityRecognizer）                      │
│  ├─ NLP识别（HanLP语义理解）                                       │
│  │   · 识别地名、组织、行业等实体                                  │
│  │   · 理解全文语义结构                                           │
│  └─ 规则识别（KeywordMatcher）                                    │
│      · AC自动机精确匹配                                           │
│      · 正则表达式模式匹配                                         │
└─────────────────────────────────────────────────────────────────┘
    ↓ 识别结果: "科技" "有限公司"
┌─────────────────────────────────────────────────────────────────┐
│ 步骤2: 元信息提取（MetaInfoExtractor）                            │
│  ┌───────────────────────────────────────┐                      │
│  │ 2.1 提取括号内容（BracketExtractor）    │                      │
│  │  · 识别括号类型（中文/英文）            │                      │
│  │  · 提取括号内容并移除                  │                      │
│  │  · 对括号内容进行二次元信息提取         │                      │
│  └───────────────────────────────────────┘                      │
│  ┌───────────────────────────────────────┐                      │
│  │ 2.2 规则引擎提取                       │                      │
│  │  · 地域提取（RegionExtractor）         │                      │
│  │    → regions.txt 前缀匹配             │                      │
│  │    → 结果: "北京"                     │                      │
│  │  · 品牌提取（BrandExtractor）          │                      │
│  │    → brand-keywords.txt 前缀匹配      │                      │
│  │    → 结果: null                       │                      │
│  │  · 组织类型提取（OrgTypeExtractor）    │                      │
│  │    → org-types.txt 后缀匹配           │                      │
│  │    → 结果: "有限公司"                 │                      │
│  │  · 行业提取（IndustryExtractor）       │                      │
│  │    → industry-keywords.txt 关键词匹配 │                      │
│  │    → 结果: "科技"                     │                      │
│  └───────────────────────────────────────┘                      │
│  ┌───────────────────────────────────────┐                      │
│  │ 2.3 NLP增强（如果启用）                │                      │
│  │  · HanLP语义分析                      │                      │
│  │  · 识别完整地名（"北京" → "北京市"）   │                      │
│  │  · 识别主营业务（避免误匹配部门名称）   │                      │
│  │  · 识别完整品牌名                     │                      │
│  └───────────────────────────────────────┘                      │
│  ┌───────────────────────────────────────┐                      │
│  │ 2.4 NLP与规则合并                      │                      │
│  │  · 地域/品牌/组织类型: NLP更完整时覆盖 │                      │
│  │  · 行业: 优先使用NLP（语义更准确）     │                      │
│  │  · 组织类型: 优先使用规则（后缀匹配）  │                      │
│  └───────────────────────────────────────┘                      │
│                                                                  │
│  最终元信息:                                                      │
│    · region: "北京"                                             │
│    · industry: "科技"                                           │
│    · orgType: "有限公司"                                        │
│    · brand: null                                                │
│    · bracketContent: null                                       │
└─────────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────────┐
│ 步骤3: 读取用户配置策略                                            │
│  · keep-region: false   （地域脱敏）                             │
│  · keep-industry: true  （行业保留）                             │
│  · keep-org-type: true  （组织类型保留）                         │
│  · keep-brand: false    （品牌脱敏）                             │
│  · keep-bracket: false  （括号智能脱敏）                         │
│  · mask-type: asterisk  （星号掩码）                             │
└─────────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────────┐
│ 步骤4: 敏感机构检测 & 保留率计算                                   │
│  ┌───────────────────────────────────────┐                      │
│  │ 4.1 机构类型判定                       │                      │
│  │  · 匹配金融关键词: 否                  │                      │
│  │  · 匹配政府关键词: 否                  │                      │
│  │  · 匹配教育关键词: 否                  │                      │
│  │  → 机构类型: NONE（普通机构）          │                      │
│  └───────────────────────────────────────┘                      │
│  ┌───────────────────────────────────────┐                      │
│  │ 4.2 保留率阈值设定                     │                      │
│  │  · 金融机构: 60%（可配置覆盖）         │                      │
│  │  · 政府机构: 60%（可配置覆盖）         │                      │
│  │  · 教育机构: 70%（可配置覆盖）         │                      │
│  │  · 普通机构: 80%（可配置覆盖）         │                      │
│  │  → 阈值: 80%                         │                      │
│  └───────────────────────────────────────┘                      │
│  ┌───────────────────────────────────────┐                      │
│  │ 4.3 计算保留率（仅主体，不含括号）      │                      │
│  │  · 主体文本: "北京科技有限公司"        │                      │
│  │  · 总长度: 9字符                       │                      │
│  │  · 保留字符:                           │                      │
│  │    - keep-region=false → 0字符        │                      │
│  │    - keep-industry=true → 2字符（科技）│                      │
│  │    - keep-org-type=true → 4字符（有限公司）│                 │
│  │  · 保留率 = (0+2+4) / 9 = 66.7%       │                      │
│  │  → 低于阈值80%，无需降级               │                      │
│  └───────────────────────────────────────┘                      │
└─────────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────────┐
│ 步骤5: 三级智能降级（如需要）                                      │
│  保留率66.7% < 阈值80% → 无需降级，使用原始配置                    │
│                                                                  │
│  降级逻辑（仅当保留率 >= 阈值时触发）:                              │
│  ┌─ 第一级降级: keep-region=false                               │
│  │  · 重新计算保留率                                             │
│  │  · 如果保留率仍 >= 60%，继续第二级                            │
│  ├─ 第二级降级: keep-industry=false                             │
│  │  · 重新计算保留率                                             │
│  │  · 如果保留率仍 >= 60%，继续第三级                            │
│  └─ 第三级降级: keep-org-type=false                             │
│     · 如果所有字段都不保留，触发Fallback                         │
└─────────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────────┐
│ 步骤6: 应用脱敏策略（AsteriskMaskStrategy）                       │
│  ┌───────────────────────────────────────┐                      │
│  │ 6.1 创建字符标记数组                   │                      │
│  │  boolean[] keepMask = new boolean[9]; │                      │
│  └───────────────────────────────────────┘                      │
│  ┌───────────────────────────────────────┐                      │
│  │ 6.2 标记保留字符                       │                      │
│  │  · keep-region=false → 不标记"北京"    │                      │
│  │  · keep-industry=true → 标记"科技"     │                      │
│  │    keepMask[2]=true, keepMask[3]=true │                      │
│  │  · keep-org-type=true → 标记"有限公司" │                      │
│  │    keepMask[5..8]=true                │                      │
│  └───────────────────────────────────────┘                      │
│  ┌───────────────────────────────────────┐                      │
│  │ 6.3 根据标记数组构建脱敏结果           │                      │
│  │  原文: 北 京 科 技 有 限 公 司         │                      │
│  │  位置:  0  1  2  3  4  5  6  7  8     │                      │
│  │  标记:  F  F  T  T  F  T  T  T  T     │                      │
│  │  结果:  *  * 科 技  * 有 限 公 司      │                      │
│  │  → "**科技*有限公司"                  │                      │
│  └───────────────────────────────────────┘                      │
│  ┌───────────────────────────────────────┐                      │
│  │ 6.4 处理括号内容（如有）               │                      │
│  │  · keep-bracket=true: 完整保留         │                      │
│  │  · keep-bracket=false: 智能脱敏        │                      │
│  │    - 对括号内容应用同样的策略          │                      │
│  │    - 保留地域/组织类型，脱敏其他       │                      │
│  └───────────────────────────────────────┘                      │
└─────────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────────┐
│ 步骤7: 兜底脱敏检测（Fallback）                                   │
│  触发条件检测（任意一个满足即触发）:                               │
│  ✗ 无有效元信息                                                  │
│  ✗ 主体部分全为星号                                              │
│  ✗ 输出等于输入（未脱敏）                                         │
│  ✗ 最终保留字符=0                                                │
│  → 不触发Fallback，正常输出                                      │
│                                                                  │
│  Fallback范围退避规则（如触发）:                                  │
│  · ≤3字符：保留首1字符                                           │
│  · 4-7字符：保留首尾各1字符                                       │
│  · 8-11字符：保留首尾各2字符                                      │
│  · ≥12字符：保留首尾各3字符                                       │
└─────────────────────────────────────────────────────────────────┘
    ↓
最终输出: "**科技*有限公司"
```

### 关键设计说明

#### 1. 为什么行业识别优先使用NLP？

规则引擎基于关键词匹配，容易误匹配部门名称。例如：
- 输入：`"东方大国邮政集团有限公司河东省信息技术中心"`
- 规则引擎：识别到"信息技术"（误匹配部门名称）
- NLP引擎：识别到"邮政"（正确的主营业务）
- **最终结果**：使用NLP的"邮政"

#### 2. 为什么组织类型优先使用规则？

NLP可能识别出更长的组织类型，导致保留率虚高：
- 输入：`东方大国证券交易所"`
- 规则引擎：识别后缀"交易所"（3字符）
- NLP引擎：识别"证券交易所"（5字符）
- **最终结果**：使用规则的"交易所"（避免保留率过高触发不必要的降级）

#### 3. 保留率计算为什么不含括号？

括号通常是分支机构信息，独立脱敏处理，不影响主体保留率：
- 输入：`"北京科技有限公司（海淀分公司）"`
- 主体：`"北京科技有限公司"` → 计算保留率
- 括号：`"（海淀分公司）"` → 独立脱敏

---

## 配置方案推荐

### 方案1：通用脱敏（推荐，默认配置）

**适用场景**：大部分业务场景，兼顾安全性和可读性

```yaml
io.github.surezzzzzz.sdk.sensitive.keyword:
  enable: true
  default-strategy:
    keep-region: false        # 地域脱敏（安全优先）
    keep-industry: true       # 行业保留（增加可读性）
    keep-org-type: true       # 组织类型保留（基本不敏感）
    keep-bracket: false       # 括号智能脱敏（通常是分支机构）
    keep-brand: false         # 品牌脱敏（品牌是敏感信息）
    mask-type: asterisk       # 星号掩码
    keep-length: true         # 保留长度信息
```

### 方案2：高安全性脱敏

**适用场景**：金融、政府、医疗等高安全等级场景

```yaml
io.github.surezzzzzz.sdk.sensitive.keyword:
  enable: true
  default-strategy:
    keep-region: false
    keep-industry: false      # 行业也脱敏
    keep-org-type: false      # 组织类型也脱敏
    keep-bracket: false
    keep-brand: false
    mask-type: hash           # 使用哈希掩码（不可逆）
```

**效果示例**：
- `中国工商银行股份有限公司` → `a1b2c3d4e5f6...`（32位MD5哈希）
- 完全不可逆，最高安全性
- 相同输入产生相同哈希，便于去重统计

### 方案3：品牌保留型

**适用场景**：需要保留品牌信息进行统计分析的场景

```yaml
io.github.surezzzzzz.sdk.sensitive.keyword:
  enable: true
  default-strategy:
    keep-region: false
    keep-industry: true
    keep-org-type: true
    keep-bracket: false
    keep-brand: true          # 保留品牌信息
    mask-type: asterisk
```

**注意**：保留品牌可能泄露敏感信息，请根据业务需求谨慎使用。

### 方案4：占位符型（数据分析友好）

**适用场景**：需要保留语义信息进行数据分析，但仍需脱敏

```yaml
io.github.surezzzzzz.sdk.sensitive.keyword:
  enable: true
  default-strategy:
    keep-region: false
    keep-industry: true
    keep-org-type: true
    keep-bracket: false
    keep-brand: false
    mask-type: placeholder    # 使用占位符
```

**效果示例**：
- `北京科技有限公司` → `[企业_有限公司]`
- `河南省人民政府` → `[单位_政府]`
- `清华大学` → `[学校_大学]`

**特点**：
- 占位符自动根据组织类型生成语义化标签
- 便于数据分类和统计（如按机构类型分组）
- 完全脱敏，无原始信息泄露
- 不同机构的占位符可能相同（失去唯一性）

---

## 完整配置项说明

### 核心配置

| 配置项 | 类型 | 默认值 | 说明 | 影响 |
|--------|------|--------|------|------|
| `enable` | Boolean | false | 是否启用脱敏功能 | 关闭后所有脱敏失效 |
| `default-strategy.keep-region` | Boolean | false | 是否保留地域信息 | **true**: 保留地域前缀如"北京"、"上海"等，可能泄露机构位置<br>**false**: 地域脱敏为星号，更安全 |
| `default-strategy.keep-industry` | Boolean | true | 是否保留行业信息 | **true**: 保留"金融"、"科技"等行业关键词，增加可读性<br>**false**: 行业脱敏为星号，更安全但失去语义 |
| `default-strategy.keep-org-type` | Boolean | true | 是否保留组织类型 | **true**: 保留"有限公司"、"研究院"等组织类型，通常不敏感<br>**false**: 组织类型脱敏为星号，最高安全性 |
| `default-strategy.keep-bracket` | Boolean | false | 是否保留括号内容 | **true**: 完整保留括号内容如"（海淀分公司）"<br>**false**: 对括号内容进行智能脱敏（推荐） |
| `default-strategy.keep-brand` | Boolean | false | 是否保留品牌信息 | **true**: 保留"华为"、"阿里巴巴"等品牌名称，可能泄露敏感信息<br>**false**: 品牌脱敏为星号，更安全（推荐） |
| `default-strategy.mask-type` | String | asterisk | 掩码类型 | **asterisk**: 星号掩码如"**科技有限公司"，兼顾安全和可读性（推荐）<br>**placeholder**: 占位符如"[企业_有限公司]"，适合数据分析<br>**hash**: MD5哈希如"a1b2c3d4..."，最高安全性，不可逆 |
| `default-strategy.keep-length` | Boolean | true | 是否保留长度信息 | **true**: 星号数量等于原文长度，保留一定信息<br>**false**: 使用固定数量星号，完全隐藏长度信息 |
| `default-strategy.fixed-mask-length` | Integer | 3 | 固定星号数量 (v1.0.2+) | 当`keep-length=false`时生效，使用固定数量星号替代原文长度<br>**示例**: `fixed-mask-length=3`时 "上海联合产权交易所" → "***交易所"<br>`fixed-mask-length=5`时 "上海联合产权交易所" → "*****交易所" |
| `default-strategy.fixed-bracket-mask-length` | Integer | null | 括号内容固定星号数量 (v1.0.2+) | 当`keep-length=false`时生效，括号内容使用配置的固定星号数量<br>**默认**: `null`（未配置时继承自`fixed-mask-length`）<br>**示例**: `fixed-bracket-mask-length=2`时 "北京科技有限公司（海淀分公司）" 括号内使用2个星号<br>**应用场景**: 为括号内容（通常是分支机构信息）单独配置脱敏长度 |

### 高级配置

#### 1. NLP配置

| 配置项 | 类型 | 默认值 | 说明 | 影响 |
|--------|------|--------|------|------|
| `nlp.enabled` | Boolean | true | 是否启用NLP增强 | **true**: 使用HanLP进行语义理解，提升元信息提取准确率（推荐）<br>**false**: 仅使用规则引擎，可能误匹配行业信息 |
| `nlp.provider` | String | BUILT_IN | NLP提供者 | **BUILT_IN**: 使用内置HanLP<br>**CUSTOM**: 使用自定义NLP实现 |
| `nlp.fallback-to-rule` | Boolean | true | NLP失败时降级到规则 | **true**: NLP超时/失败时使用规则引擎（推荐）<br>**false**: NLP失败后直接返回空结果 |
| `nlp.timeout-ms` | Integer | 1000 | NLP超时时间（毫秒） | 超时后触发fallback-to-rule |

#### 2. Fallback脱敏配置

| 配置项 | 类型 | 默认值 | 说明 | 影响 |
|--------|------|--------|------|------|
| `default-strategy.fallback.enabled` | Boolean | true | 是否启用Fallback | **true**: 无法提取元信息时使用范围退避（推荐）<br>**false**: 禁用后可能导致部分文本未脱敏 |
| `default-strategy.fallback.mask-type` | String | asterisk | Fallback掩码类型 | 同上述mask-type |
| `default-strategy.fallback.keep-length` | Boolean | true | 是否保留长度信息 | 同上述keep-length |
| `default-strategy.fallback.length-threshold-short` | Integer | 3 | 短文本长度阈值 | 文本长度≤此值时，保留keepCharsShort个首字符 |
| `default-strategy.fallback.length-threshold-medium` | Integer | 7 | 中文本长度阈值 | 文本长度在thresholdShort+1到thresholdMedium之间时，保留首尾各keepCharsMedium个字符 |
| `default-strategy.fallback.length-threshold-long` | Integer | 11 | 长文本长度阈值 | 文本长度在thresholdMedium+1到thresholdLong之间时，保留首尾各keepCharsLong个字符 |
| `default-strategy.fallback.keep-chars-short` | Integer | 1 | 短文本保留字符数 | 长度≤thresholdShort时保留的首字符数 |
| `default-strategy.fallback.keep-chars-medium` | Integer | 1 | 中文本保留字符数 | 长度在thresholdShort+1到thresholdMedium时保留的首尾各自字符数 |
| `default-strategy.fallback.keep-chars-long` | Integer | 2 | 长文本保留字符数 | 长度在thresholdMedium+1到thresholdLong时保留的首尾各自字符数 |
| `default-strategy.fallback.keep-chars-extra-long` | Integer | 3 | 超长文本保留字符数 | 长度>thresholdLong时保留的首尾各自字符数 |

**Fallback示例**：
- `ABC`（长度3≤thresholdShort） → `A**`（保留首1字符）
- `ABCDEF`（长度6，在thresholdShort+1到thresholdMedium之间） → `A****F`（保留首尾各1字符）
- `ABCDEFGH`（长度8，在thresholdMedium+1到thresholdLong之间） → `AB****GH`（保留首尾各2字符）
- `ABCDEFGHIJKL`（长度12>thresholdLong） → `ABC******JKL`（保留首尾各3字符）

#### 3. 保留率阈值配置

| 配置项 | 类型 | 默认值 | 说明 | 影响 |
|--------|------|--------|------|------|
| `retention-thresholds.none` | Double | 0.8 | 普通机构保留率阈值 | 保留率超过此值时触发降级，降低保留字段直到低于阈值 |
| `retention-thresholds.financial` | Double | 0.6 | 金融机构保留率阈值 | 金融机构更严格，保留率超过60%即触发降级 |
| `retention-thresholds.government` | Double | 0.6 | 政府机构保留率阈值 | 政府机构更严格，保留率超过60%即触发降级 |
| `retention-thresholds.education` | Double | 0.7 | 教育机构保留率阈值 | 教育机构中等严格，保留率超过70%即触发降级 |

**保留率计算公式**：
```
保留率 = 保留字符数 / 主体文本长度（不含括号）
```

**三级降级机制**：
1. **第一级降级**：保留率 >= 阈值 → 关闭地域保留（keep-region=false）
2. **第二级降级**：保留率仍 >= 60% → 关闭行业保留（keep-industry=false）
3. **第三级降级**：保留率仍 >= 60% → 关闭组织类型保留（keep-org-type=false）
4. **Fallback触发**：所有字段都不保留 → 使用范围退避

**调整保留率阈值的影响**：
- **提高阈值**（如将financial从0.6改为0.8）：降级触发更少，保留更多信息，但可能泄露敏感信息
- **降低阈值**（如将none从0.8改为0.6）：降级触发更多，脱敏更彻底，但可读性下降

#### 4. 关键词集合配置

| 配置项 | 类型 | 默认值 | 说明 | 影响                                               |
|--------|------|--------|------|--------------------------------------------------|
| `keyword-sets.mode` | Enum | APPEND | 配置模式 | **APPEND**: 追加到内置规则（推荐）<br>**REPLACE**: 完全替换内置规则 |
| `keyword-sets.financial-keywords` | Set<String> | 内置 | 金融机构敏感词 | 用于判定金融机构，触发60%保留率阈值                              |
| `keyword-sets.government-keywords` | Set<String> | 内置 | 政府机构敏感词 | 用于判定政府机构，触发60%保留率阈值                              |
| `keyword-sets.education-keywords` | Set<String> | 内置 | 教育机构敏感词 | 用于判定教育机构，触发70%保留率阈值                              |
| `keyword-sets.non-region-blacklist` | Set<String> | 内置 | 非地域词黑名单 | 避免误匹配地域（如"东方电网"的"东方"不是地域）                        |

**扩展示例**：
```yaml
io.github.surezzzzzz.sdk.sensitive.keyword:
  keyword-sets:
    mode: APPEND
    financial-keywords:
      - "特殊金融关键词"
    government-keywords:
      - "特殊政府关键词"
```

#### 5. 自定义关键词策略

| 配置项 | 类型 | 说明 | 影响 |
|--------|------|------|------|
| `keywords[].keyword` | String | 关键词 | 匹配到此关键词时应用独立策略 |
| `keywords[].patterns` | List<String> | 别名/简称列表 | 配置多种写法，共享主keyword的meta和strategy |
| `keywords[].meta.region` | String | 预定义地域 | 跳过自动提取，使用预定义的地域信息 |
| `keywords[].meta.industry` | String | 预定义行业 | 跳过自动提取，使用预定义的行业信息 |
| `keywords[].meta.org-type` | String | 预定义组织类型 | 跳过自动提取，使用预定义的组织类型 |
| `keywords[].meta.brand` | String | 预定义品牌 | 跳过自动提取，使用预定义的品牌信息 |
| `keywords[].strategy.keep-region` | Boolean | 是否保留地域 | 覆盖默认策略的keep-region |
| `keywords[].strategy.keep-industry` | Boolean | 是否保留行业 | 覆盖默认策略的keep-industry |
| `keywords[].strategy.keep-org-type` | Boolean | 是否保留组织类型 | 覆盖默认策略的keep-org-type |
| `keywords[].strategy.keep-bracket` | Boolean | 是否保留括号 | 覆盖默认策略的keep-bracket |
| `keywords[].strategy.keep-brand` | Boolean | 是否保留品牌 | 覆盖默认策略的keep-brand |
| `keywords[].strategy.mask-type` | String | 掩码类型 | 覆盖默认策略的mask-type |

**基础自定义关键词示例**：
```yaml
io.github.surezzzzzz.sdk.sensitive.keyword:
  keywords:
    - keyword: "工商银行"
      strategy:
        keep-region: false
        keep-industry: false    # 金融机构，行业也脱敏
        keep-org-type: false
        keep-brand: false
        mask-type: hash         # 使用哈希
```

#### 6. Patterns 别名映射 (v1.0.1+)

**核心价值**: 解决组织机构的多种写法问题，统一脱敏策略。

**关键机制**：
1. Patterns通过AC自动机高效匹配
2. 匹配到pattern后,映射到主keyword
3. 使用主keyword的strategy和meta进行脱敏
4. 所有变体脱敏规则统一管理

#### 7. 预定义Meta信息 (v1.0.1+)

**核心价值**: 为缩写/简称提供准确的元信息，避免自动提取失败。

**Meta工作原理**：

1. **优先级**: 预定义meta > 自动提取meta
2. **跳过提取**: 配置meta后，跳过规则引擎和NLP的自动提取，直接使用预定义值
3. **性能优化**: 跳过NLP调用，脱敏速度更快

---

## 配置替换的影响对照

### keep-region 的影响

| 配置值 | 输入示例 | 输出示例 | 说明 |
|--------|----------|----------|------|
| `true` | 北京科技有限公司 | 北京科技有限公司 | 保留地域前缀，可能泄露机构位置 |
| `false` | 北京科技有限公司 | **科技有限公司 | 地域脱敏为星号（推荐） |

### keep-industry 的影响

| 配置值 | 输入示例 | 输出示例 | 说明 |
|--------|----------|----------|------|
| `true` | 北京科技有限公司 | **科技有限公司 | 保留行业关键词，增加可读性（推荐） |
| `false` | 北京科技有限公司 | ****有限公司 | 行业脱敏为星号，更安全但失去语义 |

### keep-org-type 的影响

| 配置值 | 输入示例 | 输出示例 | 说明 |
|--------|----------|----------|------|
| `true` | 北京科技有限公司 | **科技有限公司 | 保留组织类型，基本不敏感（推荐） |
| `false` | 北京科技有限公司 | **科技**** | 组织类型脱敏为星号，最高安全性 |

### keep-bracket 的影响

| 配置值 | 输入示例 | 输出示例 | 说明 |
|--------|----------|----------|------|
| `true` | 北京科技有限公司（海淀分公司） | **科技有限公司（海淀分公司） | 完整保留括号内容 |
| `false` | 北京科技有限公司（海淀分公司） | **科技有限公司（***公司） | 智能脱敏括号内容（推荐） |

### keep-brand 的影响

| 配置值 | 输入示例 | 输出示例 | 说明 |
|--------|----------|----------|------|
| `true` | 深圳华为技术有限公司 | **华为技术有限公司 | 保留品牌名称，可能泄露敏感信息 |
| `false` | 深圳华为技术有限公司 | **技术有限公司 | 品牌脱敏为星号（推荐） |

### mask-type 的影响

| 配置值 | 输入示例 | 输出示例 | 说明 |
|--------|----------|----------|------|
| `asterisk` | 北京科技有限公司 | **科技有限公司 | 星号掩码，兼顾安全和可读性（推荐） |
| `placeholder` | 北京科技有限公司 | [企业_有限公司] | 占位符，适合数据分析 |
| `hash` | 北京科技有限公司 | a1b2c3d4e5f6... | MD5哈希，最高安全性，不可逆 |

### 保留率阈值的影响

**示例：金融机构"工商银行股份有限公司"**

| 配置 | 保留率计算 | 是否触发降级 | 输出示例 |
|------|-----------|-------------|----------|
| `financial: 0.6`（默认） | 保留"银行"+"股份有限公司" = 7/11 = 63.6% > 60% | 是 | `******股份有限公司` |
| `financial: 0.8`（提高阈值） | 保留"银行"+"股份有限公司" = 7/11 = 63.6% < 80% | 否 | `****银行股份有限公司` |
| `financial: 0.5`（降低阈值） | 保留"银行"+"股份有限公司" = 7/11 = 63.6% > 50% | 是（更严格） | `***********` |

---

## 常见问题

### Q1: 为什么金融/政府机构的脱敏结果几乎全是星号？

**A**: 这是**三级智能降级机制**的结果，设计上针对敏感机构加强脱敏。

- 金融机构阈值：60%（可配置覆盖）
- 政府机构阈值：60%（可配置覆盖）
- 教育机构阈值：70%（可配置覆盖）
- 普通机构阈值：80%（可配置覆盖）

当保留率超过阈值时，会依次关闭地域、行业、组织类型保留，直到保留率降到阈值以下。

**如需调整**，可自定义保留率阈值：
```yaml
io.github.surezzzzzz.sdk.sensitive.keyword:
  retention-thresholds:
    financial: 0.75  # 提高金融机构阈值到75%
```

### Q2: NLP提取的行业和规则引擎不一样怎么办？

**A**: 这是正常现象，且设计上**优先使用NLP的行业识别结果**。

- **规则引擎**：基于关键词匹配，可能误匹配部门名称
- **NLP引擎**：基于语义理解，能识别主营业务
- **推荐**：保持`nlp.enabled=true`，获得更准确的行业识别

### Q3: 如何完全禁用Fallback？

**A**: 不推荐禁用Fallback，因为可能导致部分文本无法脱敏。如果确实需要：

```yaml
io.github.surezzzzzz.sdk.sensitive.keyword:
  default-strategy:
    fallback:
      enabled: false
```

禁用后，无法提取元信息的文本将保持原样输出。

### Q4: 占位符策略生成的占位符都是 [ORG_001]？

**A**: 占位符会自动根据提取的**组织类型**生成语义化标签：

- `有限公司` → `[企业_有限公司]`
- `政府` → `[单位_政府]`
- `大学` → `[学校_大学]`
- `医院` → `[医疗_医院]`
- `银行` → `[金融_银行]`

只有当无法提取到组织类型时，才会使用配置的默认占位符。

### Q5: 如何扩展敏感机构的判定规则？

**A**: 通过 `keyword-sets` 配置扩展：

```yaml
io.github.surezzzzzz.sdk.sensitive.keyword:
  keyword-sets:
    mode: APPEND          # 追加模式，不影响内置规则
    financial-keywords:
      - "特殊金融关键词"
    government-keywords:
      - "特殊政府关键词"
```

内置规则从 `industry-keywords.txt` 动态加载，用户配置会追加到内置规则（APPEND模式）或完全替换（REPLACE模式）。

---

## API参考

### KeywordSensitiveMaskHelper

主要脱敏助手类，提供单个、批量、详情三种脱敏方法。

#### 1. 单个脱敏

```java
/**
 * 单个文本脱敏
 * @param text 待脱敏文本
 * @return 脱敏后的文本
 */
String mask(String text);
```

**示例**：
```java
String masked = helper.mask("北京科技有限公司");
// 输出: "**科技有限公司"
```

#### 2. 批量脱敏

```java
/**
 * 批量文本脱敏
 * @param textList 待脱敏文本列表
 * @return 脱敏后的文本列表
 */
List<String> batchMask(List<String> textList);
```

**示例**：
```java
List<String> maskedList = helper.batchMask(Arrays.asList(
    "上海金融控股有限公司",
    "深圳互联网科技有限公司"
));
// 输出: ["**金融控股有限公司", "**互联网科技有限公司"]
```

#### 3. 详情脱敏

```java
/**
 * 脱敏并返回详细信息
 * @param text 待脱敏文本
 * @return 脱敏详情对象
 */
MaskResultDetail maskWithDetail(String text);
```

**示例**：
```java
MaskResultDetail detail = helper.maskWithDetail("东方大国银行股份有限公司");
System.out.println("原文: " + detail.getOriginal());
System.out.println("脱敏后: " + detail.getMasked());
System.out.println("详细原因: " + detail.getReason());
```

**MaskResultDetail 结构**：
```java
public class MaskResultDetail {
    private String originalText;       // 原始文本
    private String maskedText;         // 脱敏后文本
    private String reason;             // 脱敏原因（包含元信息、降级过程等）

    // Getters...
}
```

**详细原因示例**：
```
北京科技有限公司 → **科技有限公司
├─ 来源: 关键词匹配 "科技"
├─ 元信息提取:
│  · 地域: 北京（规则引擎）
│  · 行业: 科技（NLP增强）
│  · 组织类型: 有限公司（规则引擎）
│  · 品牌: null
├─ 策略配置:
│  · keep-region: false
│  · keep-industry: true
│  · keep-org-type: true
│  · keep-brand: false
├─ 机构类型: 普通机构（保留率阈值80%）
├─ 保留率计算: 66.7%（6字符/9字符）
└─ 降级调整: 无需降级
```

---

## 版本历史

### 1.0.0 (2025-12-27)

**首个正式版本发布**

核心功能：
- ✅ 双引擎识别：NLP语义理解 + 规则引擎
- ✅ 智能元信息提取：地域、行业、品牌、组织类型、括号内容
- ✅ 三种脱敏策略：星号、占位符、哈希
- ✅ 三级智能降级：针对金融/政府/教育机构动态调整保留率
- ✅ Fallback范围退避：确保所有文本都能被正确脱敏
- ✅ 详细脱敏追踪：完整的元信息提取和脱敏决策过程日志
- ✅ 可配置保留率阈值：支持用户自定义金融/政府/教育机构的保留率阈值
- ✅ Spring Boot自动配置：开箱即用，IDE智能提示

技术特性：
- 基于HanLP的NLP增强（可选）
- AC自动机高效关键词匹配
- 智能括号识别和二次元信息提取
- 保留率动态计算和智能降级
- 灵活的配置体系（默认策略 + 关键词独立策略）

---

## 许可证

Apache License 2.0

---

## 联系方式

- **作者**: surezzzzzz
- **邮箱**: [GitHub Issues](https://github.com/surezzzzzz/normal-sdks/issues)
- **仓库**: https://github.com/surezzzzzz/normal-sdks

如有问题或建议，欢迎提交Issue。
