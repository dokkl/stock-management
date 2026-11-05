package com.stockmanagement.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;

/** N+1 검출용: DataSource를 감싸 실행된 SQL 문 수를 센다(헌법 원칙 IV). */
public final class QueryCounter implements BeanPostProcessor {

    private static final Set<String> STATEMENT_METHODS = Set.of("prepareStatement", "createStatement", "prepareCall");
    private final AtomicLong count = new AtomicLong();

    public long count() {
        return count.get();
    }

    public void reset() {
        count.set(0);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource) {
            return proxy(DataSource.class, dataSource, (target, method, args) -> {
                Object result = invoke(method, target, args);
                return result instanceof Connection connection ? countingConnection(connection) : result;
            });
        }
        return bean;
    }

    private Connection countingConnection(Connection connection) {
        return proxy(Connection.class, connection, (target, method, args) -> {
            if (STATEMENT_METHODS.contains(method.getName())) {
                count.incrementAndGet();
            }
            return invoke(method, target, args);
        });
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @FunctionalInterface
    private interface Handler {
        Object handle(Object target, Method method, Object[] args) throws Throwable;
    }

    private static <T> T proxy(Class<T> type, T target, Handler handler) {
        InvocationHandler invocationHandler = (p, method, args) -> handler.handle(target, method, args);
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, invocationHandler));
    }
}
