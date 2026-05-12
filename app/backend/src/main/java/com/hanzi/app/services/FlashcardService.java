package com.hanzi.app.services;

import java.util.List;

public final class FlashcardService extends StubService {
    public FlashcardService() {
        super("flashcard creation", List.of(
                "GET /api/cards/drafts",
                "POST /api/cards/drafts",
                "GET /api/cards/drafts/{draftId}",
                "PUT /api/cards/drafts/{draftId}",
                "POST /api/cards/drafts/{draftId}/preview"));
    }
}
