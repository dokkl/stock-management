package com.stockmanagement.common.web;

import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IExpressionObjectDialect;
import org.thymeleaf.expression.IExpressionObjectFactory;

/** 템플릿에서 {@code ${#money.price(amount, currency)}}로 MoneyFormatter를 쓰게 한다. */
@Component
public class MoneyDialect extends AbstractDialect implements IExpressionObjectDialect {

    private static final String NAME = "money";
    private final MoneyFormatter formatter = new MoneyFormatter();

    public MoneyDialect() {
        super("MoneyDialect");
    }

    @Override
    public IExpressionObjectFactory getExpressionObjectFactory() {
        return new IExpressionObjectFactory() {
            @Override
            public Set<String> getAllExpressionObjectNames() {
                return Set.of(NAME);
            }

            @Override
            public Object buildObject(IExpressionContext context, String expressionObjectName) {
                return Map.of(NAME, formatter).get(expressionObjectName);
            }

            @Override
            public boolean isCacheable(String expressionObjectName) {
                return true;
            }
        };
    }
}
