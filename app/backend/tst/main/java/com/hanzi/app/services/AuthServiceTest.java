package com.hanzi.app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.hanzi.app.services.AuthService.AuthException;
import com.hanzi.app.services.AuthService.AuthSettings;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class AuthServiceTest {
    @Test
    public void unconfiguredServiceRejectsAuthOperations() {
        AuthService service = new AuthService("   ", null, null);

        assertFalse(service.isConfigured());
        assertAuthException(503, "Authentication database is not configured.",
                () -> service.register(Map.of(), "JUnit", "127.0.0.1"));
        assertAuthException(503, "Authentication database is not configured.",
                () -> service.signIn(Map.of(), "JUnit", "127.0.0.1"));
        assertAuthException(503, "Authentication database is not configured.",
                () -> service.session("token"));
        assertAuthException(503, "Authentication database is not configured.",
                () -> service.signOut("token"));
    }

    @Test
    public void normalizeJdbcUrlAcceptsCommonPostgresForms() throws Exception {
        assertEquals("jdbc:postgresql://localhost/hanzi", invokeString("normalizeJdbcUrl", "postgres://localhost/hanzi"));
        assertEquals("jdbc:postgresql://localhost/hanzi", invokeString("normalizeJdbcUrl", "postgresql://localhost/hanzi"));
        assertEquals("jdbc:postgresql://localhost/hanzi", invokeString("normalizeJdbcUrl", "jdbc:postgresql://localhost/hanzi"));
        assertEquals("jdbc:h2:mem:test", invokeString("normalizeJdbcUrl", " jdbc:h2:mem:test "));
        assertNull(invokeString("normalizeJdbcUrl", "   "));
    }

    @Test
    public void normalizesEmailAndUsernameBeforePersistenceChecks() throws Exception {
        assertEquals("learner@example.com", invokeString("normalizeEmail", " Learner@Example.COM "));
        assertEquals("sample-user", invokeString("normalizeUsername", " Sample-User "));
        assertEquals("Display_Name", invokeString("normalizeDisplayName", " Display_Name "));
    }

    @Test
    public void displayNameAllowsPredictableNonReservedNames() throws Exception {
        invokeVoid("validateDisplayName", "Learner_01");
        invokeVoid("validateDisplayName", "hanzi-fan");
    }

    @Test
    public void displayNameRejectsMissingInvalidLengthInvalidCharactersAndReservedNames() {
        assertAuthException(400, "Display name is required.",
                () -> invokeVoid("validateDisplayName", (String) null));
        assertAuthException(400, "Display name must be 3 to 20 characters and use only letters, numbers, underscores, or hyphens.",
                () -> invokeVoid("validateDisplayName", "ab"));
        assertAuthException(400, "Display name must be 3 to 20 characters and use only letters, numbers, underscores, or hyphens.",
                () -> invokeVoid("validateDisplayName", "abcdefghijklmnopqrstu"));
        assertAuthException(400, "Display name must be 3 to 20 characters and use only letters, numbers, underscores, or hyphens.",
                () -> invokeVoid("validateDisplayName", "hanzi fan"));
        assertAuthException(400, "Display name must be 3 to 20 characters and use only letters, numbers, underscores, or hyphens.",
                () -> invokeVoid("validateDisplayName", "Support"));
    }

    @Test
    public void passwordAcceptsMinimumStrongRuleset() throws Exception {
        invokeVoid("validatePassword", "studytime9", "learner@example.com", "HanziFan");
        invokeVoid("validatePassword", "bright-path", "learner@example.com", "HanziFan");
    }

    @Test
    public void passwordRejectsWeakOrUserDerivedValues() {
        assertAuthException(400, "Password must be at least 8 characters.",
                () -> invokeVoid("validatePassword", "short1", "learner@example.com", "HanziFan"));
        assertAuthException(400, "Password must include at least one number or symbol.",
                () -> invokeVoid("validatePassword", "longenough", "learner@example.com", "HanziFan"));
        assertAuthException(400, "Password must not include your display name.",
                () -> invokeVoid("validatePassword", "hanziFan9!", "learner@example.com", "HanziFan"));
        assertAuthException(400, "Password must not include long pieces of your email address.",
                () -> invokeVoid("validatePassword", "learner9!", "learner@example.com", "HanziFan"));
        assertAuthException(400, "Password must not include long pieces of your email address.",
                () -> invokeVoid("validatePassword", "example9!", "learner@example.com", "HanziFan"));
    }

    @Test
    public void passwordHashVerifiesOriginalPasswordOnly() throws Exception {
        String hash = invokeString("hashPassword", "studytime9");

        assertNotNull(hash);
        assertTrue(hash.startsWith("pbkdf2_sha256$120000$"));
        assertTrue(invokeBoolean("verifyPassword", "studytime9", hash));
        assertFalse(invokeBoolean("verifyPassword", "studytime8", hash));
        assertFalse(invokeBoolean("verifyPassword", "studytime9", "not-a-pbkdf2-hash"));
    }

    @Test
    public void sessionTokensAreRandomAndTokenHashesAreDeterministic() throws Exception {
        String first = invokeString("sessionToken");
        String second = invokeString("sessionToken");

        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first, second);
        assertEquals(invokeString("hashToken", first), invokeString("hashToken", first));
        assertNotEquals(first, invokeString("hashToken", first));
    }

    @Test
    public void truncateTrimsBlankValuesAndLimitsLongValues() throws Exception {
        assertNull(invokeString("truncate", "   ", 5));
        assertEquals("short", invokeString("truncate", "short", 10));
        assertEquals("abcde", invokeString("truncate", "abcdefghi", 5));
    }

    @Test
    public void testSettingsCanShortenButNotLengthenSessionLifetimes() {
        AuthService service = new AuthService(
                "   ",
                null,
                null,
                new AuthSettings(Duration.ofSeconds(2), Duration.ofSeconds(4)));

        assertFalse(service.isConfigured());
        assertIllegalArgument("Session TTL must not exceed 20 minutes.",
                () -> new AuthService("   ", null, null, new AuthSettings(Duration.ofMinutes(21), Duration.ofHours(1))));
        assertIllegalArgument("Absolute session TTL must not exceed 12 hours.",
                () -> new AuthService("   ", null, null, new AuthSettings(Duration.ofMinutes(20), Duration.ofHours(13))));
    }

    private static void assertAuthException(int expectedStatus, String expectedMessage, ThrowingRunnable action) {
        try {
            action.run();
            fail("Expected AuthException");
        } catch (AuthException exception) {
            assertEquals(expectedStatus, exception.status());
            assertEquals(expectedMessage, exception.getMessage());
        } catch (Exception exception) {
            fail("Expected AuthException but got " + exception.getClass().getName());
        }
    }

    private static void assertIllegalArgument(String expectedMessage, ThrowingRunnable action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertEquals(expectedMessage, exception.getMessage());
        } catch (Exception exception) {
            fail("Expected IllegalArgumentException but got " + exception.getClass().getName());
        }
    }

    private static String invokeString(String name, Object... args) throws Exception {
        return (String) invoke(name, args);
    }

    private static boolean invokeBoolean(String name, Object... args) throws Exception {
        return (Boolean) invoke(name, args);
    }

    private static void invokeVoid(String name, Object... args) throws Exception {
        invoke(name, args);
    }

    /**
     * Reflection bridge for exercising {@link AuthService}'s private static validation helpers.
     *
     * <p>The typed wrappers above keep individual tests readable while this method centralizes
     * private-method lookup, invocation, and unwrapping of exceptions thrown by the helper.
     */
    private static Object invoke(String name, Object... args) throws Exception {
        Method method = AuthService.class.getDeclaredMethod(name, parameterTypes(args));
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException ex) {
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

    private static Class<?>[] parameterTypes(Object[] args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int index = 0; index < args.length; index++) {
            types[index] = args[index] instanceof Integer ? int.class : String.class;
        }
        return types;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
