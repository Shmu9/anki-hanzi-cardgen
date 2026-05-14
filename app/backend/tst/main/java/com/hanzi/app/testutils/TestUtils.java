package com.hanzi.app.testutils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.ToIntFunction;

public final class TestUtils {
    private TestUtils() {}

    // Keeps private static helper tests readable while each test class owns its parameter mapping.
    public static StaticMethodInvoker staticMethods(Class<?> type, ParameterTypes parameterTypes) {
        return new StaticMethodInvoker(type, parameterTypes);
    }

    // Shared assertion for service exceptions that expose an HTTP-style status code.
    public static <E extends Exception> void assertStatusException(
            Class<E> expectedType,
            int expectedStatus,
            String expectedMessage,
            ToIntFunction<E> status,
            ThrowingRunnable action) {
        try {
            action.run();
            fail("Expected " + expectedType.getSimpleName());
        } catch (Exception exception) {
            if (!expectedType.isInstance(exception)) {
                fail("Expected " + expectedType.getSimpleName() + " but got " + exception.getClass().getName());
            }
            E typedException = expectedType.cast(exception);
            assertEquals(expectedStatus, status.applyAsInt(typedException));
            assertEquals(expectedMessage, typedException.getMessage());
        }
    }

    public static void assertIllegalArgument(String expectedMessage, ThrowingRunnable action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertEquals(expectedMessage, exception.getMessage());
        } catch (Exception exception) {
            fail("Expected IllegalArgumentException but got " + exception.getClass().getName());
        }
    }

    public static Object invokeStatic(Class<?> type, String name, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Method method = type.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException ex) {
            // Reflection wraps the real failure; unwrap it so tests assert against domain exceptions.
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw ex;
        }
    }

    public static Object recordValue(Object record, String accessorName) throws Exception {
        Method method = record.getClass().getDeclaredMethod(accessorName);
        method.setAccessible(true);
        return method.invoke(record);
    }

    // Convenience facade around invokeStatic with typed return helpers for common assertions.
    public static final class StaticMethodInvoker {
        private final Class<?> type;
        private final ParameterTypes parameterTypes;

        private StaticMethodInvoker(Class<?> type, ParameterTypes parameterTypes) {
            this.type = type;
            this.parameterTypes = parameterTypes;
        }

        public String invokeString(String name, Object... args) throws Exception {
            return (String) invoke(name, args);
        }

        public boolean invokeBoolean(String name, Object... args) throws Exception {
            return (Boolean) invoke(name, args);
        }

        public void invokeVoid(String name, Object... args) throws Exception {
            invoke(name, args);
        }

        public Object invoke(String name, Object... args) throws Exception {
            return invokeStatic(type, name, parameterTypes.typesFor(name, args), args);
        }
    }

    // Different classes overload helpers differently, so callers provide the lookup signature rules.
    @FunctionalInterface
    public interface ParameterTypes {
        Class<?>[] typesFor(String name, Object[] args);
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
