import { EntryDetail } from "../components/EntryDetail";
import { SearchPane } from "../components/SearchPane";
import type { ComponentMeaningPreference, EntryResponse, GlyphRow, MetadataResponse, SearchFilters } from "../types";

interface ExplorePageProps {
    filters: SearchFilters;
    metadata: MetadataResponse | null;
    results: GlyphRow[];
    resultCountLabel: string;
    selectedGlyph: string | null;
    rawVisible: boolean;
    error: string | null;
    entry: EntryResponse | null;
    componentMeanings: ComponentMeaningPreference[];
    onFiltersChange: (filters: SearchFilters) => void;
    onSearchSubmit: () => void;
    onSelectGlyph: (glyph: string) => void;
    onToggleRaw: () => void;
    onOpenDetail: (glyph: string) => void;
    onCreateCard: (glyph?: string) => void;
    onOpenGlyphDefinitions: (glyph: string) => void;
}

export function ExplorePage({
    filters,
    metadata,
    results,
    resultCountLabel,
    selectedGlyph,
    rawVisible,
    error,
    entry,
    componentMeanings,
    onFiltersChange,
    onSearchSubmit,
    onSelectGlyph,
    onToggleRaw,
    onOpenDetail,
    onCreateCard,
    onOpenGlyphDefinitions,
}: ExplorePageProps) {
    return (
        <main className="app-shell">
            <SearchPane
                filters={filters}
                metadata={metadata}
                results={results}
                resultCountLabel={resultCountLabel}
                selectedGlyph={selectedGlyph}
                rawVisible={rawVisible}
                error={error}
                onFiltersChange={onFiltersChange}
                onSearchSubmit={onSearchSubmit}
                onSelectGlyph={onSelectGlyph}
                onToggleRaw={onToggleRaw}
            />
            <EntryDetail
                payload={entry}
                componentMeanings={componentMeanings}
                rawVisible={rawVisible}
                onSelectGlyph={onSelectGlyph}
                onOpenDetail={onOpenDetail}
                onCreateCard={onCreateCard}
                onOpenGlyphDefinitions={onOpenGlyphDefinitions}
            />
        </main>
    );
}
