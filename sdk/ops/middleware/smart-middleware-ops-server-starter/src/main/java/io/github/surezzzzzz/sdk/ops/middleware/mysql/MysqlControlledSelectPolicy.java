package io.github.surezzzzzz.sdk.ops.middleware.mysql;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * MySQL 受控 SELECT AST 白名单。
 *
 * @author surezzzzzz
 */
final class MysqlControlledSelectPolicy {

    private static final String EMPTY_SQL = "SQL 不能为空";
    private static final String SQL_LENGTH_EXCEEDED = "SQL 长度超出允许范围";
    private static final String COMMENT_OR_SEPARATOR = "SQL 不允许包含注释或语句分隔符";
    private static final String PARSE_FAILED = "SQL 语法无法解析";
    private static final String UNSUPPORTED_STATEMENT = "仅支持单条受控 SELECT 查询";
    private static final String UNSUPPORTED_STRUCTURE = "仅支持无 schema 的单表 SELECT，不支持 CTE、JOIN、分页、分组、锁定、INTO 或其他复杂查询结构";
    private static final String INVALID_PROJECTION_SIZE = "SELECT 投影不能为空且列数不能超过允许范围";
    private static final String INVALID_PROJECTION = "SELECT 投影仅支持显式的当前表字段";
    private static final String INVALID_WHERE = "WHERE 仅支持当前表字段与字面量的受限条件";
    private static final String INVALID_ORDER_BY = "ORDER BY 仅支持当前表字段";

    private final int maxSqlLength;
    private final int maxColumns;

    MysqlControlledSelectPolicy(int maxSqlLength, int maxColumns) {
        this.maxSqlLength = maxSqlLength;
        this.maxColumns = maxColumns;
    }

    void validate(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw rejected(EMPTY_SQL);
        }
        if (sql.length() > maxSqlLength) {
            throw rejected(SQL_LENGTH_EXCEEDED);
        }
        if (containsCommentOrStatementSeparator(sql)) {
            throw rejected(COMMENT_OR_SEPARATOR);
        }
        Statement statement = parseSingleStatement(sql);
        if (!(statement instanceof PlainSelect)) {
            throw rejected(UNSUPPORTED_STATEMENT);
        }
        validateSupportedSelect((PlainSelect) statement);
    }

    private Statement parseSingleStatement(String sql) {
        try {
            return CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            throw rejected(PARSE_FAILED);
        }
    }

    private void validateSupportedSelect(PlainSelect select) {
        if (hasUnsupportedStructure(select)) {
            throw rejected(UNSUPPORTED_STRUCTURE);
        }
        Table table = (Table) select.getFromItem();
        if (!isLocalTable(table) || table.getPivot() != null || table.getUnPivot() != null) {
            throw rejected(UNSUPPORTED_STRUCTURE);
        }
        validateProjection(select.getSelectItems(), table);
        if (!isSupportedCondition(select.getWhere(), table)) {
            throw rejected(INVALID_WHERE);
        }
        validateOrderBy(select, table);
    }

    private boolean hasUnsupportedStructure(PlainSelect select) {
        return select.getWithItemsList() != null || select.getForClause() != null || select.getLimit() != null
                || select.getOffset() != null || select.getFetch() != null || select.getIntoTables() != null
                || select.getIntoTempTable() != null || select.getJoins() != null || select.getGroupBy() != null
                || select.getHaving() != null || select.getQualify() != null || select.getForMode() != null
                || select.getForUpdateTable() != null || select.isNoWait() || select.isSkipLocked()
                || select.getLateralViews() != null || !(select.getFromItem() instanceof Table);
    }

    private void validateProjection(List<SelectItem<?>> selectItems, Table table) {
        if (selectItems == null || selectItems.isEmpty() || selectItems.size() > maxColumns) {
            throw rejected(INVALID_PROJECTION_SIZE);
        }
        for (SelectItem<?> selectItem : selectItems) {
            if (!isLocalColumn(selectItem.getExpression(), table)) {
                throw rejected(INVALID_PROJECTION);
            }
        }
    }

    private void validateOrderBy(PlainSelect select, Table table) {
        if (select.getOrderByElements() == null) {
            return;
        }
        for (net.sf.jsqlparser.statement.select.OrderByElement orderBy : select.getOrderByElements()) {
            if (!isLocalColumn(orderBy.getExpression(), table)) {
                throw rejected(INVALID_ORDER_BY);
            }
        }
    }

    private boolean isSupportedCondition(Expression expression, Table sourceTable) {
        if (expression == null) {
            return true;
        }
        if (expression instanceof Parenthesis) {
            return isSupportedCondition(((Parenthesis) expression).getExpression(), sourceTable);
        }
        if (expression instanceof AndExpression || expression instanceof OrExpression) {
            BinaryExpression binary = (BinaryExpression) expression;
            return isSupportedCondition(binary.getLeftExpression(), sourceTable)
                    && isSupportedCondition(binary.getRightExpression(), sourceTable);
        }
        if (expression instanceof EqualsTo || expression instanceof NotEqualsTo || expression instanceof GreaterThan
                || expression instanceof GreaterThanEquals || expression instanceof MinorThan
                || expression instanceof MinorThanEquals) {
            BinaryExpression binary = (BinaryExpression) expression;
            return isLocalColumn(binary.getLeftExpression(), sourceTable) && isLiteral(binary.getRightExpression());
        }
        if (expression instanceof LikeExpression) {
            LikeExpression like = (LikeExpression) expression;
            return like.getLikeKeyWord() == LikeExpression.KeyWord.LIKE
                    && isLocalColumn(like.getLeftExpression(), sourceTable) && isLiteral(like.getRightExpression());
        }
        if (expression instanceof IsNullExpression) {
            return isLocalColumn(((IsNullExpression) expression).getLeftExpression(), sourceTable);
        }
        if (expression instanceof Between) {
            Between between = (Between) expression;
            return isLocalColumn(between.getLeftExpression(), sourceTable) && isLiteral(between.getBetweenExpressionStart())
                    && isLiteral(between.getBetweenExpressionEnd());
        }
        if (expression instanceof InExpression) {
            InExpression in = (InExpression) expression;
            if (!isLocalColumn(in.getLeftExpression(), sourceTable) || !(in.getRightExpression() instanceof ExpressionList)) {
                return false;
            }
            for (Expression value : ((ExpressionList<Expression>) in.getRightExpression()).getExpressions()) {
                if (!isLiteral(value)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private boolean isLocalColumn(Expression expression, Table sourceTable) {
        if (!(expression instanceof Column)) {
            return false;
        }
        Table table = ((Column) expression).getTable();
        if (table == null || table.getName() == null) {
            return true;
        }
        return isLocalTable(table) && (table.getName().equalsIgnoreCase(sourceTable.getName())
                || sourceTable.getAlias() != null && table.getName().equalsIgnoreCase(sourceTable.getAlias().getName()));
    }

    private boolean isLocalTable(Table table) {
        return table != null && table.getName() != null && table.getSchemaName() == null;
    }

    private boolean isLiteral(Expression expression) {
        if (expression instanceof SignedExpression) {
            return isLiteral(((SignedExpression) expression).getExpression());
        }
        return expression instanceof LongValue || expression instanceof DoubleValue || expression instanceof StringValue
                || expression instanceof NullValue || expression instanceof DateValue || expression instanceof TimeValue
                || expression instanceof TimestampValue || expression instanceof HexValue;
    }

    private boolean containsCommentOrStatementSeparator(String sql) {
        return sql.contains("--") || sql.contains("/*") || sql.contains("#") || sql.indexOf(';') >= 0;
    }

    private MiddlewareOpsException rejected(String message) {
        return new MiddlewareOpsException(HttpStatus.BAD_REQUEST, message);
    }
}
