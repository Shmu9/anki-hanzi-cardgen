import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { getEntry, getMetadata, searchGlyphs } from "./api";
import { AuthPage } from "./pages/AuthPage";
import { CharacterDetailPage } from "./pages/CharacterDetailPage";
import { CreatePage } from "./pages/CreatePage";
import { ExplorePage } from "./pages/ExplorePage";
import { HomePage } from "./pages/HomePage";
import { PreferencesPage } from "./pages/PreferencesPage";
import { navItems, type Page, type RouteState } from "./routes";
import type { EntryResponse, GlyphRow, MetadataResponse, SearchFilters } from "./types";

const initialFilters: SearchFilters = {
    q: "",
    hsk: "any",
    strokeMin: "",
    strokeMax: "",
};

export default function App() {
    const [route, setRouteState] = useState<RouteState>(() => parseHashRoute());
    const [filters, setFilters] = useState<SearchFilters>(initialFilters);
    const [metadata, setMetadata] = useState<MetadataResponse | null>(null);
    const [results, setResults] = useState<GlyphRow[]>([]);
    const [entry, setEntry] = useState<EntryResponse | null>(null);
    const [selectedGlyph, setSelectedGlyph] = useState<string | null>(route.glyph ?? null);
    const [detailLookup, setDetailLookup] = useState(route.glyph ?? "");
    const [rawVisible, setRawVisible] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [searching, setSearching] = useState(false);

    useEffect(() => {
        const syncFromHash = () => {
            const next = parseHashRoute();
            setRouteState(next);
            if (next.glyph) {
                setDetailLookup(next.glyph);
            }
        };

        window.addEventListener("hashchange", syncFromHash);
        return () => window.removeEventListener("hashchange", syncFromHash);
    }, []);

    useEffect(() => {
        getMetadata()
            .then(setMetadata)
            .catch((err: Error) => setError(err.message));
    }, []);

    const setRoute = useCallback((next: RouteState) => {
        const targetHash = formatHashRoute(next);
        if (window.location.hash === targetHash) {
            setRouteState(next);
        } else {
            window.location.hash = targetHash;
        }
    }, []);

    const loadEntry = useCallback(async (key: string) => {
        const payload = await getEntry(key);
        const glyph = payload.entry.glyph ?? key;
        setEntry(payload);
        setSelectedGlyph(glyph);
        setDetailLookup(glyph);
        return glyph;
    }, []);

    const openCharacterDetail = useCallback(
        (glyph: string) => {
            loadEntry(glyph)
                .then((resolvedGlyph) => setRoute({ page: "detail", glyph: resolvedGlyph }))
                .catch((err: Error) => setError(err.message));
        },
        [loadEntry, setRoute],
    );

    const openCreatePage = useCallback(
        (glyph?: string) => {
            if (glyph) {
                setDetailLookup(glyph);
                setSelectedGlyph(glyph);
            }
            setRoute({ page: "create", glyph });
        },
        [setRoute],
    );

    const runSearch = useCallback(async () => {
        setSearching(true);
        try {
            const payload = await searchGlyphs(filters);
            setResults(payload.results);
            setError(null);
            if (!selectedGlyph && payload.results.length && payload.results[0].glyph) {
                await loadEntry(payload.results[0].glyph);
            }
        } catch (err) {
            setError(err instanceof Error ? err.message : "Search failed");
        } finally {
            setSearching(false);
        }
    }, [filters, loadEntry, selectedGlyph]);

    useEffect(() => {
        const timer = window.setTimeout(() => {
            runSearch();
        }, 180);
        return () => window.clearTimeout(timer);
    }, [runSearch]);

    useEffect(() => {
        if (!route.glyph) {
            return;
        }
        loadEntry(route.glyph).catch((err: Error) => setError(err.message));
    }, [loadEntry, route.glyph]);

    const selectGlyph = useCallback(
        (glyph: string) => {
            loadEntry(glyph).catch((err: Error) => setError(err.message));
        },
        [loadEntry],
    );

    const searchSummary = useMemo(() => {
        if (searching) {
            return "Searching";
        }
        if (error) {
            return "Needs attention";
        }
        return `${results.length} results ready`;
    }, [error, results.length, searching]);

    const submitDetailLookup = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const key = detailLookup.trim();
        if (key) {
            openCharacterDetail(key);
        }
    };

    return (
        <div className="site-shell">
            <header className="top-bar">
                <button className="wordmark" type="button" onClick={() => setRoute({ page: "home" })}>
                    <span className="wordmark-glyph">字</span>
                    <span>
                        Hanzi Cardgen
                        <small>{metadata ? `${metadata.unicode_count.toLocaleString()} characters` : "Dictionary loading"}</small>
                    </span>
                </button>
                <nav className="top-nav" aria-label="Primary navigation">
                    {navItems.map((item) => (
                        <button
                            className={route.page === item.page ? "active" : ""}
                            key={item.page}
                            type="button"
                            onClick={() => setRoute({ page: item.page, glyph: item.page === "detail" ? selectedGlyph ?? undefined : undefined })}
                        >
                            {item.label}
                        </button>
                    ))}
                </nav>
            </header>

            {route.page === "home" ? (
                <HomePage
                    metadata={metadata}
                    searchSummary={searchSummary}
                    selectedGlyph={selectedGlyph}
                    onNavigate={setRoute}
                    onOpenDetail={openCharacterDetail}
                />
            ) : null}

            {route.page === "explore" ? (
                <ExplorePage
                    filters={filters}
                    metadata={metadata}
                    results={results}
                    resultCountLabel={searching ? "..." : error ? "!" : String(results.length)}
                    selectedGlyph={selectedGlyph}
                    rawVisible={rawVisible}
                    error={error}
                    entry={entry}
                    onFiltersChange={setFilters}
                    onSearchSubmit={() => runSearch()}
                    onSelectGlyph={selectGlyph}
                    onToggleRaw={() => setRawVisible((visible) => !visible)}
                    onOpenDetail={openCharacterDetail}
                    onCreateCard={openCreatePage}
                />
            ) : null}

            {route.page === "detail" ? (
                <CharacterDetailPage
                    detailLookup={detailLookup}
                    entry={entry}
                    rawVisible={rawVisible}
                    onDetailLookupChange={setDetailLookup}
                    onSubmitDetailLookup={submitDetailLookup}
                    onToggleRaw={() => setRawVisible((visible) => !visible)}
                    onOpenDetail={openCharacterDetail}
                    onCreateCard={openCreatePage}
                />
            ) : null}

            {route.page === "create" ? <CreatePage selectedGlyph={route.glyph ?? selectedGlyph ?? ""} onOpenExplorer={() => setRoute({ page: "explore" })} /> : null}

            {route.page === "preferences" ? <PreferencesPage /> : null}

            {route.page === "auth" ? <AuthPage /> : null}
        </div>
    );
}

function parseHashRoute(): RouteState {
    const cleaned = window.location.hash.replace(/^#\/?/, "");
    const [rawPage, ...parts] = cleaned.split("/");
    const page = isPage(rawPage) ? rawPage : "home";
    const glyph = parts.length ? decodeURIComponent(parts.join("/")) : undefined;
    return { page, glyph };
}

function formatHashRoute(route: RouteState): string {
    const glyph = route.glyph ? `/${encodeURIComponent(route.glyph)}` : "";
    return `#/${route.page}${glyph}`;
}

function isPage(value: string): value is Page {
    return navItems.some((item) => item.page === value);
}
