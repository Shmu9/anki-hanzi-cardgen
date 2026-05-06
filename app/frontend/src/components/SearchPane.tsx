import type { GlyphRow, MetadataResponse, SearchFilters } from "../types";
import { compactDefinition, text } from "../format";

interface SearchPaneProps {
    filters: SearchFilters;
    metadata: MetadataResponse | null;
    results: GlyphRow[];
    resultCountLabel: string;
    selectedGlyph: string | null;
    rawVisible: boolean;
    error: string | null;
    onFiltersChange: (filters: SearchFilters) => void;
    onSearchSubmit: () => void;
    onSelectGlyph: (glyph: string) => void;
    onToggleRaw: () => void;
}

export function SearchPane({
    filters,
    metadata,
    results,
    resultCountLabel,
    selectedGlyph,
    rawVisible,
    error,
    onFiltersChange,
    onSearchSubmit,
    onSelectGlyph,
    onToggleRaw,
}: SearchPaneProps) {
    const setFilter = (key: keyof SearchFilters, value: string) => {
        onFiltersChange({ ...filters, [key]: value });
    };

    return (
        <aside className="search-pane">
            <header className="brand-row">
                <div>
                    <h1>Glyph Explorer</h1>
                    <p id="dbMeta">
                        {metadata
                            ? `${metadata.unicode_count.toLocaleString()} Unicode rows · ${metadata.glyph_count.toLocaleString()} glyph records`
                            : "Loading dictionary"}
                    </p>
                </div>
                <button
                    className={`icon-button${rawVisible ? " active" : ""}`}
                    title="Toggle raw record"
                    aria-label="Toggle raw record"
                    type="button"
                    onClick={onToggleRaw}
                >
                    {"{}"}
                </button>
            </header>

            <form
                className="search-form"
                onSubmit={(event) => {
                    event.preventDefault();
                    onSearchSubmit();
                }}
            >
                <label className="field search-field">
                    <span>Search</span>
                    <input
                        name="q"
                        autoComplete="off"
                        placeholder="漢, U+6F22, han, water"
                        value={filters.q}
                        onChange={(event) => setFilter("q", event.target.value)}
                    />
                </label>
                <div className="filter-grid">
                    <label className="field">
                        <span>HSK</span>
                        <select name="hsk" value={filters.hsk} onChange={(event) => setFilter("hsk", event.target.value)}>
                            <option value="any">Any</option>
                            <option value="1">1</option>
                            <option value="2">2</option>
                            <option value="3">3</option>
                            <option value="4">4</option>
                            <option value="5">5</option>
                            <option value="6">6</option>
                        </select>
                    </label>
                    <label className="field">
                        <span>Stroke Min</span>
                        <input
                            name="stroke_min"
                            type="number"
                            min="1"
                            inputMode="numeric"
                            value={filters.strokeMin}
                            onChange={(event) => setFilter("strokeMin", event.target.value)}
                        />
                    </label>
                    <label className="field">
                        <span>Stroke Max</span>
                        <input
                            name="stroke_max"
                            type="number"
                            min="1"
                            inputMode="numeric"
                            value={filters.strokeMax}
                            onChange={(event) => setFilter("strokeMax", event.target.value)}
                        />
                    </label>
                </div>
            </form>

            <section className="results-section">
                <div className="section-heading">
                    <h2>Results</h2>
                    <span>{resultCountLabel}</span>
                </div>
                <div className="results-list">
                    {error ? <div className="panel error-panel">{error}</div> : null}
                    {!error &&
                        results.map((row, index) => (
                            <button
                                className={`result-card${row.glyph === selectedGlyph ? " selected" : ""}`}
                                key={row.glyph ?? row.token ?? row.unicode ?? index}
                                type="button"
                                onClick={() => row.glyph && onSelectGlyph(row.glyph)}
                            >
                                <span className="result-glyph">{text(row.glyph, "?")}</span>
                                <span className="result-main">
                                    <strong>{text(row.k_definition, "No definition")}</strong>
                                    <span>
                                        {[row.k_mandarin, row.unicode, row.stroke_count ? `${row.stroke_count} strokes` : null]
                                            .filter(Boolean)
                                            .join(" · ")}
                                    </span>
                                </span>
                            </button>
                        ))}
                    {!error && !results.length ? <div className="panel empty-results">{compactDefinition("No results found")}</div> : null}
                </div>
            </section>
        </aside>
    );
}
