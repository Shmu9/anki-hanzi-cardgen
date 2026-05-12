package com.hanzi.app.services;

import java.util.List;

public final class AnkiSyncService extends StubService {
    public AnkiSyncService() {
        super("Anki sync", List.of(
                "GET /api/anki/status",
                "GET /api/anki/decks",
                "GET /api/anki/note-models",
                "POST /api/anki/sync",
                "GET /api/anki/sync/{syncId}"));
    }
}
