package io.github.surezzzzzz.sdk.ops.middleware.mysql;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;
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
 * MySQL 受控 SELECT 请求校验器。
 *
 * @author surezzzzzz
 */
public class MysqlSelectRequestValidator extends DefaultMiddlewareOpsRequestValidator<MysqlSelectRequest> {

    private final int maxSqlLength;
    private final int maxSize;
    private final int maxColumns;

    /**
     * 创建 MySQL 受控 SELECT 请求校验器。
     *
     * @param maxSqlLength SQL 最大字符数
     * @param maxSize      返回行数上限
     * @param maxColumns   返回列数上限
     */
    public MysqlSelectRequestValidator(int maxSqlLength, int maxSize, int maxColumns) {
        super(MysqlSelectRequest.class);
        this.maxSqlLength = maxSqlLength;
        this.maxSize = maxSize;
        this.maxColumns = maxColumns;
    }

    @Override
    public void validate(MysqlSelectRequest request) {
        requireDatasource(request.getDatasourceKey());
        if (request.getSql() == null || request.getSql().trim().isEmpty() || request.getSql().length() > maxSqlLength
                || containsCommentOrStatementSeparator(request.getSql())) {
            throw rejected();
        }
        if (request.getSize() <= 0 || request.getSize() > maxSize) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "结果数量超出允许范围");
        }
        Statement statement = parseSingleStatement(request.getSql());
        if (!(statement instanceof PlainSelect) || !isSupportedSelect((PlainSelect) statement)) {
            throw rejected();
        }
    }

    private Statement parseSingleStatement(String sql) {
        try {
            return CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            throw rejected();
        }
    }

    private boolean isSupportedSelect(PlainSelect select) {
        if (select.getWithItemsList() != null || select.getForClause() != null || select.getLimit() != null
                || select.getOffset() != null || select.getFetch() != null || select.getIntoTables() != null
                || select.getIntoTempTable() != null || select.getJoins() != null || select.getGroupBy() != null
                || select.getHaving() != null || select.getQualify() != null || select.getForMode() != null
                || select.getForUpdateTable() != null || select.isNoWait() || select.isSkipLocked()
                || select.getLateralViews() != null || !(select.getFromItem() instanceof Table)) {
            return false;
        }
        Table table = (Table) select.getFromItem();
        if (!isLocalTable(table) || table.getPivot() != null || table.getUnPivot() != null) {
            return false;
        }
        List<SelectItem<?>> selectItems = select.getSelectItems();
        if (selectItems == null || selectItems.isEmpty() || selectItems.size() > maxColumns) {
            return false;
        }
        for (SelectItem<?> selectItem : selectItems) {
            if (!isSupportedProjection(selectItem.getExpression(), table)) {
                return false;
            }
        }
        if (!isSupportedCondition(select.getWhere(), table)) {
            return false;
        }
        if (select.getOrderByElements() != null) {
            for (net.sf.jsqlparser.statement.select.OrderByElement orderBy : select.getOrderByElements()) {
                if (!isLocalColumn(orderBy.getExpression(), table)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isSupportedProjection(Expression expression, Table sourceTable) {
        return isLocalColumn(expression, sourceTable);
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

    private MiddlewareOpsException rejected() {
        return new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "SQL 不符合受控查询规范");
    }
}
