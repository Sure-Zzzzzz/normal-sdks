package io.github.surezzzzzz.sdk.crm.server.core.domain.common;

import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 以 ISO-4217 货币表示的不可变金额。
 *
 * @author surezzzzzz
 */
@Getter
public final class Money {

    private final BigDecimal amount;
    private final String currency;

    /**
     * 创建Money。
     *
     * @param amount   货币金额
     * @param currency ISO-4217 货币代码
     *
     */
    public Money(BigDecimal amount, String currency) {
        this.currency = CrmValidationHelper.currency(currency, "currency");
        this.amount = CrmValidationHelper.monetaryAmount(amount, this.currency, "amount");
    }


    /**
     * 按数量精确计算金额。
     *
     * @param quantity 正数数量
     * @return 处理后的领域事实或校验结果。
     *
     */
    public Money multiply(BigDecimal quantity) {
        CrmValidationHelper.positiveDecimal(quantity, "quantity");
        return new Money(amount.multiply(quantity), currency);
    }

    /**
     * 合并同一币种的金额。
     *
     * @param other 待合并的同币种金额
     * @return 处理后的领域事实或校验结果。
     *
     */
    public Money add(Money other) {
        if (other == null || !currency.equals(other.currency)) {
            throw CrmException.validation("currency");
        }
        return new Money(amount.add(other.amount), currency);
    }
}
