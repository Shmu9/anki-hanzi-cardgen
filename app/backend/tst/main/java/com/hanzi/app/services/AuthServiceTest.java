package com.hanzi.app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hanzi.app.services.AuthService.AuthException;
import com.hanzi.app.services.AuthService.AuthSettings;
import com.hanzi.app.testutils.TestUtils;
import com.hanzi.app.testutils.TestUtils.StaticMethodInvoker;
import com.hanzi.app.testutils.TestUtils.ThrowingRunnable;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class AuthServiceTest {
    private static final StaticMethodInvoker PRIVATE_METHODS =
            TestUtils.staticMethods(AuthService.class, (name, args) -> parameterTypes(args));

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
        assertEquals("jdbc:postgresql://localhost/hanzi", PRIVATE_METHODS.invokeString("normalizeJdbcUrl", "postgres://localhost/hanzi"));
        assertEquals("jdbc:postgresql://localhost/hanzi", PRIVATE_METHODS.invokeString("normalizeJdbcUrl", "postgresql://localhost/hanzi"));
        assertEquals("jdbc:postgresql://localhost/hanzi", PRIVATE_METHODS.invokeString("normalizeJdbcUrl", "jdbc:postgresql://localhost/hanzi"));
        assertEquals("jdbc:h2:mem:test", PRIVATE_METHODS.invokeString("normalizeJdbcUrl", " jdbc:h2:mem:test "));
        assertNull(PRIVATE_METHODS.invokeString("normalizeJdbcUrl", "   "));
    }

    @Test
    public void normalizesEmailAndUsernameBeforePersistenceChecks() throws Exception {
        assertEquals("learner@example.com", PRIVATE_METHODS.invokeString("normalizeEmail", " Learner@Example.COM "));
        assertEquals("sample-user", PRIVATE_METHODS.invokeString("normalizeUsername", " Sample-User "));
        assertEquals("Display_Name", PRIVATE_METHODS.invokeString("normalizeDisplayName", " Display_Name "));
    }

    @Test
    public void displayNameAllowsPredictableNonReservedNames() throws Exception {
        PRIVATE_METHODS.invokeVoid("validateDisplayName", "Learner_01");
        PRIVATE_METHODS.invokeVoid("validateDisplayName", "hanzi-fan");
    }

    @Test
    public void displayNameRejectsMissingInvalidLengthInvalidCharactersAndReservedNames() {
        assertAuthException(400, "Display name is required.",
                () -> PRIVATE_METHODS.invokeVoid("validateDisplayName", (String) null));
        assertAuthException(400, "Display name must be 3 to 20 characters and use only letters, numbers, underscores, or hyphens.",
                () -> PRIVATE_METHODS.invokeVoid("validateDisplayName", "ab"));
        assertAuthException(400, "Display name must be 3 to 20 characters and use only letters, numbers, underscores, or hyphens.",
                () -> PRIVATE_METHODS.invokeVoid("validateDisplayName", "abcdefghijklmnopqrstu"));
        assertAuthException(400, "Display name must be 3 to 20 characters and use only letters, numbers, underscores, or hyphens.",
                () -> PRIVATE_METHODS.invokeVoid("validateDisplayName", "hanzi fan"));
        assertAuthException(400, "Display name must be 3 to 20 characters and use only letters, numbers, underscores, or hyphens.",
                () -> PRIVATE_METHODS.invokeVoid("validateDisplayName", "Support"));
    }

    @Test
    public void passwordAcceptsMinimumStrongRuleset() throws Exception {
        PRIVATE_METHODS.invokeVoid("validatePassword", "studytime9", "learner@example.com", "HanziFan");
        PRIVATE_METHODS.invokeVoid("validatePassword", "bright-path", "learner@example.com", "HanziFan");
    }

    @Test
    public void passwordRejectsWeakOrUserDerivedValues() {
        assertAuthException(400, "Password must be at least 8 characters.",
                () -> PRIVATE_METHODS.invokeVoid("validatePassword", "short1", "learner@example.com", "HanziFan"));
        assertAuthException(400, "Password must include at least one number or symbol.",
                () -> PRIVATE_METHODS.invokeVoid("validatePassword", "longenough", "learner@example.com", "HanziFan"));
        assertAuthException(400, "Password must not include your display name.",
                () -> PRIVATE_METHODS.invokeVoid("validatePassword", "hanziFan9!", "learner@example.com", "HanziFan"));
        assertAuthException(400, "Password must not include long pieces of your email address.",
                () -> PRIVATE_METHODS.invokeVoid("validatePassword", "learner9!", "learner@example.com", "HanziFan"));
        assertAuthException(400, "Password must not include long pieces of your email address.",
                () -> PRIVATE_METHODS.invokeVoid("validatePassword", "example9!", "learner@example.com", "HanziFan"));
    }

    @Test
    public void passwordHashVerifiesOriginalPasswordOnly() throws Exception {
        String hash = PRIVATE_METHODS.invokeString("hashPassword", "studytime9");

        assertNotNull(hash);
        assertTrue(hash.startsWith("pbkdf2_sha256$120000$"));
        assertTrue(PRIVATE_METHODS.invokeBoolean("verifyPassword", "studytime9", hash));
        assertFalse(PRIVATE_METHODS.invokeBoolean("verifyPassword", "studytime8", hash));
        assertFalse(PRIVATE_METHODS.invokeBoolean("verifyPassword", "studytime9", "not-a-pbkdf2-hash"));
    }

    @Test
    public void sessionTokensAreRandomAndTokenHashesAreDeterministic() throws Exception {
        String first = PRIVATE_METHODS.invokeString("sessionToken");
        String second = PRIVATE_METHODS.invokeString("sessionToken");

        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first, second);
        assertEquals(PRIVATE_METHODS.invokeString("hashToken", first), PRIVATE_METHODS.invokeString("hashToken", first));
        assertNotEquals(first, PRIVATE_METHODS.invokeString("hashToken", first));
    }

    @Test
    public void truncateTrimsBlankValuesAndLimitsLongValues() throws Exception {
        assertNull(PRIVATE_METHODS.invokeString("truncate", "   ", 5));
        assertEquals("short", PRIVATE_METHODS.invokeString("truncate", "short", 10));
        assertEquals("abcde", PRIVATE_METHODS.invokeString("truncate", "abcdefghi", 5));
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
        TestUtils.assertStatusException(AuthException.class, expectedStatus, expectedMessage, AuthException::status, action);
    }

    private static void assertIllegalArgument(String expectedMessage, ThrowingRunnable action) {
        TestUtils.assertIllegalArgument(expectedMessage, action);
    }

    private static Class<?>[] parameterTypes(Object[] args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int index = 0; index < args.length; index++) {
            types[index] = args[index] instanceof Integer ? int.class : String.class;
        }
        return types;
    }
}
