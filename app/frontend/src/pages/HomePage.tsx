import { useState } from "react";
import { WorkflowCard } from "../components/WorkflowCard";
import type { MetadataResponse } from "../types";
import type { RouteState } from "../routes";

interface HomePageProps {
    metadata: MetadataResponse | null;
    searchSummary: string;
    selectedGlyph: string | null;
    onNavigate: (route: RouteState) => void;
    onOpenDetail: (glyph: string) => void;
}

export function HomePage({ metadata, searchSummary, selectedGlyph, onNavigate, onOpenDetail }: HomePageProps) {
    const [quickGlyph, setQuickGlyph] = useState(selectedGlyph ?? "郎");

    return (
        <main className="page-content">
            <section className="home-hero">
                <div>
                    <p className="eyebrow">Dictionary explorer and Anki card authoring</p>
                    <h1>Turn character lookup into careful flashcards.</h1>
                    <p>
                        Explore Hanzi records, inspect decomposition, keep your own radical meanings, and stage cards with audio, stroke order, and
                        mnemonic fields before they reach Anki.
                    </p>
                    <div className="hero-actions">
                        <button className="primary-button" type="button" onClick={() => onNavigate({ page: "explore" })}>
                            Explore dictionary
                        </button>
                        <button className="secondary-button" type="button" onClick={() => onNavigate({ page: "create" })}>
                            Create flashcards
                        </button>
                    </div>
                </div>
                <form
                    className="quick-card"
                    onSubmit={(event) => {
                        event.preventDefault();
                        if (quickGlyph.trim()) {
                            onOpenDetail(quickGlyph.trim());
                        }
                    }}
                >
                    <label className="field">
                        <span>Quick character</span>
                        <input value={quickGlyph} onChange={(event) => setQuickGlyph(event.target.value)} />
                    </label>
                    <button className="primary-button" type="submit">
                        Open detail
                    </button>
                    <dl>
                        <div>
                            <dt>Unicode rows</dt>
                            <dd>{metadata ? metadata.unicode_count.toLocaleString() : "..."}</dd>
                        </div>
                        <div>
                            <dt>Glyph records</dt>
                            <dd>{metadata ? metadata.glyph_count.toLocaleString() : "..."}</dd>
                        </div>
                        <div>
                            <dt>Explorer</dt>
                            <dd>{searchSummary}</dd>
                        </div>
                    </dl>
                </form>
            </section>

            <section className="feature-grid" aria-label="Main workflows">
                <WorkflowCard title="Explore" body="Search by Hanzi, pinyin, Unicode, stroke count, HSK level, or frequency metadata." onClick={() => onNavigate({ page: "explore" })} />
                <WorkflowCard title="Character Detail" body="Inspect one glyph with decomposition trees, base components, reuse, and source records." onClick={() => onNavigate({ page: "detail", glyph: selectedGlyph ?? undefined })} />
                <WorkflowCard title="Create Cards" body="Stage editable character cards with deck, tags, media, stroke order, and mnemonic slots." onClick={() => onNavigate({ page: "create" })} />
                <WorkflowCard title="Preferences" body="Save radical meanings, mnemonic profile defaults, tone hints, and card enrichment choices." onClick={() => onNavigate({ page: "preferences" })} />
            </section>
        </main>
    );
}
