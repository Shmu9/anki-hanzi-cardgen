package com.hanzi.app.services;

import com.hanzi.app.GlyphStore;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public final class DictionaryService {
    private final GlyphStore glyphStore;

    public DictionaryService(GlyphStore glyphStore) {
        this.glyphStore = glyphStore;
    }

    public Map<String, Object> metadata() throws SQLException {
        return glyphStore.metadata();
    }

    public List<Map<String, Object>> search(String query, String hsk, String strokeMin, String strokeMax, int limit)
            throws SQLException {
        return glyphStore.search(query, hsk, strokeMin, strokeMax, limit);
    }

    public Map<String, Object> characterDetail(String key) throws SQLException {
        return glyphStore.entry(key);
    }
}
