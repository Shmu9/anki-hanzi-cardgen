package com.hanzi.app.services;

import java.util.List;

public final class PreferenceService extends StubService {
    public PreferenceService() {
        super("user preferences", List.of(
                "GET /api/preferences",
                "PUT /api/preferences",
                "GET /api/preferences/radicals",
                "POST /api/preferences/radicals",
                "PUT /api/preferences/radicals/{component}"));
    }
}
