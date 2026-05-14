import type { FormEvent } from "react";
import { EntryDetail } from "../components/EntryDetail";
import type { ComponentMeaningPreference, EntryResponse } from "../types";

interface CharacterDetailPageProps {
    detailLookup: string;
    entry: EntryResponse | null;
    componentMeanings: ComponentMeaningPreference[];
    rawVisible: boolean;
    onDetailLookupChange: (value: string) => void;
    onSubmitDetailLookup: (event: FormEvent<HTMLFormElement>) => void;
    onToggleRaw: () => void;
    onOpenDetail: (glyph: string) => void;
    onCreateCard: (glyph?: string) => void;
    onOpenGlyphDefinitions: (glyph: string) => void;
}

export function CharacterDetailPage({
    detailLookup,
    entry,
    componentMeanings,
    rawVisible,
    onDetailLookupChange,
    onSubmitDetailLookup,
    onToggleRaw,
    onOpenDetail,
    onCreateCard,
    onOpenGlyphDefinitions,
}: CharacterDetailPageProps) {
    return (
        <main className="page-content detail-page">
            <section className="page-hero compact-hero">
                <div>
                    <p className="eyebrow">Character detail</p>
                    <h1>Inspect one glyph deeply</h1>
                    <p>Review definitions, readings, metadata, component trees, reuse, and raw source payloads from the dictionary.</p>
                </div>
                <form className="inline-lookup" onSubmit={onSubmitDetailLookup}>
                    <label className="field">
                        <span>Lookup</span>
                        <input
                            autoComplete="off"
                            placeholder="郎 or U+90CE"
                            value={detailLookup}
                            onChange={(event) => onDetailLookupChange(event.target.value)}
                        />
                    </label>
                    <button className="primary-button" type="submit">
                        Open
                    </button>
                    <button className={`secondary-button${rawVisible ? " active" : ""}`} type="button" onClick={onToggleRaw}>
                        Raw
                    </button>
                </form>
            </section>
            <EntryDetail
                payload={entry}
                componentMeanings={componentMeanings}
                rawVisible={rawVisible}
                onSelectGlyph={onOpenDetail}
                onCreateCard={onCreateCard}
                onOpenGlyphDefinitions={onOpenGlyphDefinitions}
            />
        </main>
    );
}
