import { EntryDetail } from "../components/EntryDetail";
import { SearchPane } from "../components/SearchPane";
import type { EntryResponse, GlyphRow, MetadataResponse, SearchFilters } from "../types";

interface ExplorePageProps {
    filters: SearchFilters;
    metadata: MetadataResponse | null;
    results: GlyphRow[];
    resultCountLabel: string;
    selectedGlyph: string | null;
    rawVisible: boolean;
    error: string | null;
    entry: EntryResponse | null;
    onFiltersChange: (filters: SearchFilters) => void;
    onSearchSubmit: () => void;
    onSelectGlyph: (glyph: string) => void;
    onToggleRaw: () => void;
    onOpenDetail: (glyph: string) => void;
    onCreateCard: (glyph?: string) => void;
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
    onFiltersChange,
    onSearchSubmit,
    onSelectGlyph,
    onToggleRaw,
    onOpenDetail,
    onCreateCard,
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
                rawVisible={rawVisible}
                onSelectGlyph={onSelectGlyph}
                onOpenDetail={onOpenDetail}
                onCreateCard={onCreateCard}
            />
        </main>
    );
}
