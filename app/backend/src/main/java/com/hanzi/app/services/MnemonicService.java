package com.hanzi.app.services;

import java.util.List;

public final class MnemonicService extends StubService {
    public MnemonicService() {
        super("mnemonic generation", List.of(
                "GET /api/mnemonics/profiles",
                "POST /api/mnemonics/generate",
                "GET /api/mnemonics/runs/{runId}",
                "POST /api/mnemonics/runs/{runId}/retry"));
    }
}
