package com.hanzi.app.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PreferenceService extends StubService {
    public PreferenceService() {
        super("user preferences", List.of(
                "GET /api/preferences",
                "PUT /api/preferences",
                "GET /api/preferences/radicals",
                "POST /api/preferences/radicals",
                "PUT /api/preferences/radicals/{component}"));
    }

    public Map<String, Object> overview(AuthService.SessionContext sessionContext) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("implemented", true);
        payload.put("userId", sessionContext.userId().toString());
        payload.put("user", sessionContext.user());
        payload.put("session", sessionContext.session());
        payload.put("mnemonicProfile", Map.of(
                "name", "Scene-based learner",
                "structure", "Scene Story",
                "toneHints", "When helpful"));
        payload.put("componentMeanings", List.of());
        return payload;
    }
}
