package com.hanzi.app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hanzi.app.services.AuthService.SessionContext;
import com.hanzi.app.services.PreferenceService.PreferenceException;
import com.hanzi.app.testutils.TestUtils;
import com.hanzi.app.testutils.TestUtils.StaticMethodInvoker;
import com.hanzi.app.testutils.TestUtils.ThrowingRunnable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class PreferenceServiceTest {
    private static final StaticMethodInvoker PRIVATE_METHODS =
            TestUtils.staticMethods(PreferenceService.class, PreferenceServiceTest::parameterTypes);

    @Test
    public void unconfiguredServiceRejectsPreferenceOperations() {
        PreferenceService service = new PreferenceService("   ", null, null);
        SessionContext session = sessionContext();

        assertPreferenceException(503, "Authentication database is not configured.",
                () -> service.overview(session));
        assertPreferenceException(503, "Authentication database is not configured.",
                () -> service.componentMeaningsPayload(session, "良"));
        assertPreferenceException(503, "Authentication database is not configured.",
                () -> service.saveComponentMeaning(session, Map.of("glyph", "良", "meaning", "good")));
        assertPreferenceException(503, "Authentication database is not configured.",
                () -> service.updateComponentMeaning(session, "良", UUID.randomUUID().toString(), Map.of("meaning", "good")));
        assertPreferenceException(503, "Authentication database is not configured.",
                () -> service.deleteComponentMeaning(session, "良", UUID.randomUUID().toString()));
        assertPreferenceException(503, "Authentication database is not configured.",
                () -> service.rankComponentMeanings(session, "良", Map.of("ids", List.of(UUID.randomUUID().toString()))));
        assertPreferenceException(503, "Authentication database is not configured.",
                () -> service.replaceComponentMeanings(session, "良", Map.of("definitions", List.of())));
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
    public void requiredStringsAreTrimmedAndGlyphCanUseComponentGlyphAlias() throws Exception {
        assertEquals("良", PRIVATE_METHODS.invokeString("normalizeRequiredString",
                Map.of("componentGlyph", " 良 "), "glyph", "Component glyph is required."));
        assertEquals("good", PRIVATE_METHODS.invokeString("normalizeRequiredString",
                Map.of("meaning", " good "), "meaning", "Preferred meaning is required."));

        assertPreferenceException(400, "Component glyph is required.",
                () -> PRIVATE_METHODS.invoke("normalizeRequiredString", Map.of(), "glyph", "Component glyph is required."));
        assertPreferenceException(400, "Preferred meaning is required.",
                () -> PRIVATE_METHODS.invoke("normalizeRequiredString", Map.of("meaning", " "), "meaning", "Preferred meaning is required."));
    }

    @Test
    public void pathGlyphAndPreferenceIdRejectBlankValues() throws Exception {
        assertEquals("良", PRIVATE_METHODS.invokeString("requirePathGlyph", " 良 "));
        assertEquals("8c413b41-b4ca-40ce-ae7d-8f4b704f422f",
                PRIVATE_METHODS.invokeString("requirePreferenceId", " 8c413b41-b4ca-40ce-ae7d-8f4b704f422f "));

        assertPreferenceException(400, "Component glyph is required.",
                () -> PRIVATE_METHODS.invoke("requirePathGlyph", " "));
        assertPreferenceException(400, "Preferred meaning ID is required.",
                () -> PRIVATE_METHODS.invoke("requirePreferenceId", " "));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void orderedIdsAcceptsBothRequestKeysAndRejectsBlankEntries() throws Exception {
        assertEquals(List.of("first", "second"),
                (List<String>) PRIVATE_METHODS.invoke("orderedIds", Map.of("ids", List.of(" first ", "second"))));
        assertEquals(List.of("third"),
                (List<String>) PRIVATE_METHODS.invoke("orderedIds", Map.of("preferenceIds", List.of(" third "))));
        assertEquals(List.of(), (List<String>) PRIVATE_METHODS.invoke("orderedIds", Map.of("ids", "not-a-list")));

        assertPreferenceException(400, "Ranked preference IDs cannot be blank.",
                () -> PRIVATE_METHODS.invoke("orderedIds", Map.of("ids", List.of("first", " "))));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void definitionInputsNormalizeValidDefinitions() throws Exception {
        List<Object> definitions = (List<Object>) PRIVATE_METHODS.invoke("definitionInputs", Map.of("definitions", List.of(
                Map.of(
                        "id", " 8c413b41-b4ca-40ce-ae7d-8f4b704f422f ",
                        "meaning", " good ",
                        "componentToken", " kangxi-138 ",
                        "useInMnemonics", "true",
                        "notes", " learner note "))));

        assertEquals(1, definitions.size());
        Object definition = definitions.getFirst();
        assertEquals("8c413b41-b4ca-40ce-ae7d-8f4b704f422f", TestUtils.recordValue(definition, "id"));
        assertEquals("good", TestUtils.recordValue(definition, "meaning"));
        assertEquals("kangxi-138", TestUtils.recordValue(definition, "componentToken"));
        assertEquals(true, TestUtils.recordValue(definition, "useInMnemonics"));
        assertEquals("learner note", TestUtils.recordValue(definition, "notes"));
    }

    @Test
    public void definitionInputsRejectInvalidDefinitionPayloads() {
        assertPreferenceException(400, "Definitions are required.",
                () -> PRIVATE_METHODS.invoke("definitionInputs", Map.of()));
        assertPreferenceException(400, "A glyph can have at most five saved definitions.",
                () -> PRIVATE_METHODS.invoke("definitionInputs", Map.of("definitions", List.of(
                        Map.of("meaning", "one"),
                        Map.of("meaning", "two"),
                        Map.of("meaning", "three"),
                        Map.of("meaning", "four"),
                        Map.of("meaning", "five"),
                        Map.of("meaning", "six")))));
        assertPreferenceException(400, "Each definition must be an object.",
                () -> PRIVATE_METHODS.invoke("definitionInputs", Map.of("definitions", List.of("good"))));
        assertPreferenceException(400, "Preferred meaning is required.",
                () -> PRIVATE_METHODS.invoke("definitionInputs", Map.of("definitions", List.of(Map.of("meaning", " ")))));
        assertPreferenceException(400, "Preferred meaning must be 50 characters or fewer.",
                () -> PRIVATE_METHODS.invoke("definitionInputs", Map.of("definitions", List.of(Map.of("meaning", "a".repeat(51))))));
        assertPreferenceException(409, "Each saved definition for a glyph must be unique.",
                () -> PRIVATE_METHODS.invoke("definitionInputs", Map.of("definitions", List.of(
                        Map.of("meaning", "good"),
                        Map.of("meaning", "good")))));
        assertPreferenceException(409, "Choose up to three saved definitions for mnemonic generation.",
                () -> PRIVATE_METHODS.invoke("definitionInputs", Map.of("definitions", List.of(
                        Map.of("meaning", "one", "useInMnemonics", true),
                        Map.of("meaning", "two", "useInMnemonics", true),
                        Map.of("meaning", "three", "useInMnemonics", true),
                        Map.of("meaning", "four", "useInMnemonics", true)))));
    }

    @Test
    public void optionalRankAcceptsBlankAndBoundsNumericValues() throws Exception {
        assertEquals(-1, PRIVATE_METHODS.invoke("optionalRank", (Object) null));
        assertEquals(-1, PRIVATE_METHODS.invoke("optionalRank", " "));
        assertEquals(0, PRIVATE_METHODS.invoke("optionalRank", "0"));
        assertEquals(4, PRIVATE_METHODS.invoke("optionalRank", 4));

        assertPreferenceException(400, "Rank must be a number from 0 to 4.",
                () -> PRIVATE_METHODS.invoke("optionalRank", "high"));
        assertPreferenceException(400, "Rank must be a number from 0 to 4.",
                () -> PRIVATE_METHODS.invoke("optionalRank", -1));
        assertPreferenceException(400, "Rank must be a number from 0 to 4.",
                () -> PRIVATE_METHODS.invoke("optionalRank", 5));
    }

    @Test
    public void meaningLengthAllowsAtMostFiftyCharacters() throws Exception {
        PRIVATE_METHODS.invoke("requireMeaningLength", "a".repeat(50));

        assertPreferenceException(400, "Preferred meaning must be 50 characters or fewer.",
                () -> PRIVATE_METHODS.invoke("requireMeaningLength", "a".repeat(51)));
    }

    private static SessionContext sessionContext() {
        UUID userId = UUID.fromString("8c413b41-b4ca-40ce-ae7d-8f4b704f422f");
        return new SessionContext(userId, Map.of("id", userId.toString()), Map.of("id", UUID.randomUUID().toString()));
    }

    private static void assertPreferenceException(int expectedStatus, String expectedMessage, ThrowingRunnable action) {
        TestUtils.assertStatusException(
                PreferenceException.class, expectedStatus, expectedMessage, PreferenceException::status, action);
    }

    private static Class<?>[] parameterTypes(String name, Object[] args) {
        if ("optionalRank".equals(name)) {
            return new Class<?>[] { Object.class };
        }
        Class<?>[] types = new Class<?>[args.length];
        for (int index = 0; index < args.length; index++) {
            Object arg = args[index];
            if (arg instanceof Map<?, ?>) {
                types[index] = Map.class;
            } else if (arg instanceof Integer) {
                types[index] = Object.class;
            } else {
                types[index] = String.class;
            }
        }
        return types;
    }
}
