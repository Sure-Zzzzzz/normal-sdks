package io.github.surezzzzzz.sdk.license.core.model;

/**
 * 条件操作数联合：字段引用或字面量。
 *
 * <p>ref 命中已声明字段（rights./facts. 前缀）；literal 仅四种合法类型值；
 * 不递归访问嵌套对象、不动态构造字段名。</p>
 *
 * @author surezzzzzz
 */
public abstract class Operand {

    private Operand() {
    }

    /** 引用已声明字段。 */
    public static FieldRef ref(Namespace namespace, String fieldName) {
        return new FieldRef(namespace, fieldName);
    }

    /** 整数字面量（单位与对侧一致）。 */
    public static Literal literal(long value) {
        return new Literal(Long.valueOf(value), FieldType.INTEGER);
    }

    /** 布尔字面量。 */
    public static Literal literal(boolean value) {
        return new Literal(Boolean.valueOf(value), FieldType.BOOLEAN);
    }

    /** 文本字面量。 */
    public static Literal literal(String value) {
        if (value == null) {
            throw new IllegalArgumentException("文本字面量不得为 null");
        }
        return new Literal(value, FieldType.STRING);
    }

    /** 文本集合字面量（STRING_SET，成员比较精确、顺序无关；入参复制防御）。 */
    public static Literal literal(java.util.List<String> value) {
        if (value == null) {
            throw new IllegalArgumentException("文本集合字面量不得为 null");
        }
        return new Literal(new java.util.ArrayList<String>(value), FieldType.STRING_SET);
    }

    /** 是否为字段引用。 */
    public abstract boolean isRef();

    /** 字段引用形态；非引用时抛出。 */
    public abstract FieldRef asRef();

    /** 字面量形态；非字面量时抛出。 */
    public abstract Literal asLiteral();

    /** 命名空间：权益或事实。 */
    public enum Namespace {
        RIGHTS("rights"),
        FACTS("facts");

        private final String code;

        Namespace(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        public static Namespace fromCode(String code) {
            for (Namespace value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("未知字段命名空间：" + code);
        }
    }

    /** 字段引用。 */
    public static final class FieldRef extends Operand {

        private final Namespace namespace;
        private final String fieldName;

        private FieldRef(Namespace namespace, String fieldName) {
            if (namespace == null || fieldName == null || fieldName.isEmpty()) {
                throw new IllegalArgumentException("字段引用必须含命名空间与字段名");
            }
            this.namespace = namespace;
            this.fieldName = fieldName;
        }

        public Namespace getNamespace() {
            return namespace;
        }

        public String getFieldName() {
            return fieldName;
        }

        @Override
        public boolean isRef() {
            return true;
        }

        @Override
        public FieldRef asRef() {
            return this;
        }

        @Override
        public Literal asLiteral() {
            throw new IllegalStateException("当前操作数是字段引用");
        }
    }

    /** 类型化字面量。 */
    public static final class Literal extends Operand {

        private final Object value;
        private final FieldType type;

        private Literal(Object value, FieldType type) {
            this.value = value;
            this.type = type;
        }

        public Object getValue() {
            return value;
        }

        public FieldType getType() {
            return type;
        }

        @Override
        public boolean isRef() {
            return false;
        }

        @Override
        public FieldRef asRef() {
            throw new IllegalStateException("当前操作数是字面量");
        }

        @Override
        public Literal asLiteral() {
            return this;
        }
    }
}
