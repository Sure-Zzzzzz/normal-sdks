grammar ConditionExpr;

// ========== 解析规则 ==========

/**
 * 顶层规则：完整的条件表达式
 */
parse
    : expression EOF
    ;

/**
 * 表达式（OR 优先级最低）
 */
expression
    : andExpression (OR andExpression)*
    ;

/**
 * AND 表达式（优先级高于 OR）
 */
andExpression
    : unaryExpression (AND unaryExpression)*
    ;

/**
 * 一元表达式（支持 NOT）
 */
unaryExpression
    : NOT unaryExpression                    # NotExpr
    | primaryExpression                      # PrimaryExpr
    ;

/**
 * 基础表达式（括号、条件）
 */
primaryExpression
    : LPAREN expression RPAREN               # ParenExpr
    | condition                              # ConditionExpr
    ;

/**
 * 条件表达式
 */
condition
    : field comparisonOp value               # ComparisonCondition
    | field IN LPAREN valueList RPAREN       # InCondition
    | field NOT IN LPAREN valueList RPAREN   # NotInCondition
    | field LIKE value                       # LikeCondition
    | field PREFIX LIKE value                # PrefixLikeCondition
    | field SUFFIX LIKE value                # SuffixLikeCondition
    | field NOT LIKE value                   # NotLikeCondition
    | field IS NULL                          # IsNullCondition
    | field IS NOT NULL                      # IsNotNullCondition
    ;

/**
 * 字段名（中文连续字符 | 英文标识符）
 */
field
    : IDENTIFIER
    | CHINESE_FIELD
    ;

/**
 * 比较运算符
 */
comparisonOp
    : EQ | NE | GT | GTE | LT | LTE
    ;

/**
 * 值
 */
value
    : STRING                                 # StringValue
    | NUMBER                                 # NumberValue
    | BOOLEAN                                # BooleanValue
    | TIME_RANGE                             # TimeRangeValue
    ;

/**
 * 值列表（用于 IN 运算符）
 */
valueList
    : value (COMMA value)*
    ;

// ========== 词法规则 ==========

// 逻辑运算符
AND     : A N D | '且' | '并且' ;
OR      : O R | '或' | '或者' ;
NOT     : N O T | '非' ;

// 比较运算符
EQ      : '=' | '==' | '等于' ;
NE      : '!=' | '<>' | '不等于' ;
GT      : '>' | '大于' | '晚于' ;
GTE     : '>=' | '大于等于' | '不小于' ;
LT      : '<' | '小于' | '早于' ;
LTE     : '<=' | '小于等于' | '不大于' ;

// 集合/匹配运算符
IN      : I N | '包含于' ;
LIKE    : L I K E | '模糊匹配' | '包含' ;
PREFIX  : P R E F I X | '前缀' ;
SUFFIX  : S U F F I X | '后缀' ;

// 空值判断
IS      : I S | '是' ;
NULL    : N U L L | '空' ;

// 布尔值
BOOLEAN
    : T R U E | '真'
    | F A L S E | '假' | '否'
    ;

// 时间范围（完整关键字列表）
TIME_RANGE
    : '近5分钟' | '最近5分钟' | '近五分钟' | '最近五分钟'
    | '近10分钟' | '最近10分钟' | '近十分钟' | '最近十分钟'
    | '近15分钟' | '最近15分钟' | '近十五分钟' | '最近十五分钟'
    | '近30分钟' | '最近30分钟' | '近三十分钟' | '最近三十分钟'
    | '近1小时' | '最近1小时' | '近一小时' | '最近一小时'
    | '近6小时' | '最近6小时' | '近六小时' | '最近六小时'
    | '近12小时' | '最近12小时' | '近十二小时' | '最近十二小时'
    | '近24小时' | '最近24小时' | '近二十四小时' | '最近二十四小时'
    | '近1天' | '最近1天' | '近一天' | '最近一天'
    | '近3天' | '最近3天' | '近三天' | '最近三天'
    | '近7天' | '最近7天' | '近七天' | '最近七天'
    | '最近14天' | '最近十四天'
    | '最近30天' | '最近三十天'
    | '最近60天' | '最近六十天'
    | '最近90天' | '最近九十天'
    | '近1周' | '最近1周' | '近一周' | '最近一周'
    | '近2周' | '最近2周' | '近两周' | '最近两周' | '近二周' | '最近二周'
    | '近1个月' | '最近1个月' | '近一个月' | '最近一个月' | '一个月'
    | '近2个月' | '最近2个月' | '近两个月' | '最近两个月'
    | '近三个月' | '最近3个月' | '最近三个月' | '三个月'
    | '近半年' | '最近6个月' | '最近六个月' | '半年'
    | '近1年' | '最近1年' | '近一年' | '最近一年' | '一年'
    | '近2年' | '最近2年' | '近两年' | '最近两年'
    | '近3年' | '最近3年' | '近三年' | '最近三年'
    | '今天' | '昨天' | '前天'
    | '本周' | '上周'
    | '本月' | '上月'
    | '本季度' | '上季度'
    | '今年' | '去年'
    ;

// 标识符（英文字段名）
IDENTIFIER
    : [a-zA-Z_][a-zA-Z0-9_]*
    ;

// 中文字段名（连续，不含空格）
CHINESE_FIELD
    : [\u4E00-\u9FA5]+
    ;

// 字符串值
STRING
    : '\'' (~['\r\n])* '\''
    | '"' (~["\r\n])* '"'
    ;

// 数字值
NUMBER
    : '-'? [0-9]+ ('.' [0-9]+)?
    ;

// 符号
LPAREN  : '(' ;
RPAREN  : ')' ;
COMMA   : ',' ;

// 忽略空白
WS      : [ \t\r\n]+ -> skip ;

// 大小写不敏感的 fragment
fragment A : [aA] ; fragment B : [bB] ; fragment C : [cC] ;
fragment D : [dD] ; fragment E : [eE] ; fragment F : [fF] ;
fragment G : [gG] ; fragment H : [hH] ; fragment I : [iI] ;
fragment J : [jJ] ; fragment K : [kK] ; fragment L : [lL] ;
fragment M : [mM] ; fragment N : [nN] ; fragment O : [oO] ;
fragment P : [pP] ; fragment Q : [qQ] ; fragment R : [rR] ;
fragment S : [sS] ; fragment T : [tT] ; fragment U : [uU] ;
fragment V : [vV] ; fragment W : [wW] ; fragment X : [xX] ;
fragment Y : [yY] ; fragment Z : [zZ] ;
