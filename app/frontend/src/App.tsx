import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
    getComponentMeanings,
    getEntry,
    getMetadata,
    getPreferenceOverview,
    getSession,
    registerAccount,
    saveComponentMeaningSet,
    searchGlyphs,
    signIn,
    signOut,
} from "./api";
import { CharacterDetailPage } from "./pages/CharacterDetailPage";
import { CreatePage } from "./pages/CreatePage";
import { ExplorePage } from "./pages/ExplorePage";
import { HomePage } from "./pages/HomePage";
import { PreferencesPage } from "./pages/PreferencesPage";
import { RegisterPage } from "./pages/RegisterPage";
import { SignInPage } from "./pages/SignInPage";
import { navItems, pages, type Page, type RouteState } from "./routes";
import type {
    AuthResponse,
    AuthSession,
    AuthUser,
    ComponentMeaningPreference,
    EntryResponse,
    GlyphRow,
    MetadataResponse,
    SearchFilters,
    StandardDefinitionPreference,
} from "./types";

const SESSION_TOKEN_KEY = "hanzi.sessionToken";
const SESSION_WARNING_MS = 2 * 60 * 1000;
const MAX_USER_DEFINITION_LENGTH = 50;

const initialFilters: SearchFilters = {
    q: "",
    hsk: "any",
    strokeMin: "",
    strokeMax: "",
};

export default function App() {
    const [route, setRouteState] = useState<RouteState>(() => parseRoute());
    const [filters, setFilters] = useState<SearchFilters>(initialFilters);
    const [metadata, setMetadata] = useState<MetadataResponse | null>(null);
    const [results, setResults] = useState<GlyphRow[]>([]);
    const [entry, setEntry] = useState<EntryResponse | null>(null);
    const [selectedGlyph, setSelectedGlyph] = useState<string | null>(route.glyph ?? null);
    const [detailLookup, setDetailLookup] = useState(route.glyph ?? "");
    const [rawVisible, setRawVisible] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [searching, setSearching] = useState(false);
    const [authUser, setAuthUser] = useState<AuthUser | null>(null);
    const [authSession, setAuthSession] = useState<AuthSession | null>(null);
    const [authToken, setAuthToken] = useState(() => localStorage.getItem(SESSION_TOKEN_KEY) ?? "");
    const [authMessage, setAuthMessage] = useState("");
    const [authBusy, setAuthBusy] = useState(false);
    const [sessionWarningVisible, setSessionWarningVisible] = useState(false);
    const [componentMeanings, setComponentMeanings] = useState<ComponentMeaningPreference[]>([]);
    const [allComponentMeanings, setAllComponentMeanings] = useState<ComponentMeaningPreference[]>([]);
    const [standardDefinitionPreferences, setStandardDefinitionPreferences] = useState<StandardDefinitionPreference[]>([]);
    const [preferenceBusy, setPreferenceBusy] = useState(false);
    const [preferenceMessage, setPreferenceMessage] = useState("");
    const refreshingSessionRef = useRef(false);

    useEffect(() => {
        const syncFromLocation = () => {
            const next = parseRoute();
            setRouteState(next);
            if (next.glyph) {
                setDetailLookup(next.glyph);
            }
        };

        window.addEventListener("popstate", syncFromLocation);
        return () => {
            window.removeEventListener("popstate", syncFromLocation);
        };
    }, []);

    useEffect(() => {
        getMetadata()
            .then(setMetadata)
            .catch((err: Error) => setError(err.message));
    }, []);

    const clearAuth = useCallback((message = "") => {
        localStorage.removeItem(SESSION_TOKEN_KEY);
        setAuthToken("");
        setAuthUser(null);
        setAuthSession(null);
        setSessionWarningVisible(false);
        setComponentMeanings([]);
        setAllComponentMeanings([]);
        setStandardDefinitionPreferences([]);
        setPreferenceMessage("");
        setAuthMessage(message);
    }, []);

    const applyAuthResponse = useCallback((response: AuthResponse, fallbackToken?: string) => {
        if (response.authenticated && response.user && response.session) {
            const nextToken = response.token ?? fallbackToken ?? "";
            if (nextToken) {
                localStorage.setItem(SESSION_TOKEN_KEY, nextToken);
                setAuthToken(nextToken);
            }
            setAuthUser(response.user);
            setAuthSession(response.session);
            setSessionWarningVisible(false);
            return true;
        }
        return false;
    }, []);

    const refreshSession = useCallback(
        async (options: { showErrors?: boolean } = {}) => {
            if (!authToken || refreshingSessionRef.current) {
                return;
            }
            refreshingSessionRef.current = true;
            try {
                const response = await getSession(authToken);
                if (!applyAuthResponse(response, authToken)) {
                    clearAuth("Session expired. Sign in again to continue.");
                }
            } catch (err) {
                if (options.showErrors) {
                    setAuthMessage(err instanceof Error ? err.message : "Could not refresh the session.");
                }
            } finally {
                refreshingSessionRef.current = false;
            }
        },
        [applyAuthResponse, authToken, clearAuth],
    );

    useEffect(() => {
        if (!authToken) {
            setAuthUser(null);
            setAuthSession(null);
            return;
        }
        let isCurrent = true;
        getSession(authToken)
            .then((response) => {
                if (!isCurrent) {
                    return;
                }
                if (applyAuthResponse(response, authToken)) {
                    setAuthMessage("");
                } else {
                    clearAuth();
                }
            })
            .catch((err: Error) => {
                if (isCurrent) {
                    setAuthMessage(err.message);
                }
            });
        return () => {
            isCurrent = false;
        };
    }, [applyAuthResponse, authToken, clearAuth]);

    useEffect(() => {
        if (!authToken || !authUser) {
            setComponentMeanings([]);
            setAllComponentMeanings([]);
            setStandardDefinitionPreferences([]);
            return;
        }
        const actionEvents = ["click", "keydown", "change", "submit"];
        const onAction = () => {
            refreshSession();
        };
        actionEvents.forEach((eventName) => window.addEventListener(eventName, onAction, true));
        return () => {
            actionEvents.forEach((eventName) => window.removeEventListener(eventName, onAction, true));
        };
    }, [authToken, authUser, refreshSession]);

    useEffect(() => {
        const glyph = entry?.entry.glyph;
        if (!authToken || !authUser || !glyph) {
            setComponentMeanings([]);
            setPreferenceMessage("");
            return;
        }
        let isCurrent = true;
        getComponentMeanings(glyph, authToken)
            .then((payload) => {
                if (isCurrent) {
                    setComponentMeanings(payload.componentMeanings);
                    setPreferenceMessage("");
                }
            })
            .catch((err: Error) => {
                if (isCurrent) {
                    setComponentMeanings([]);
                    setPreferenceMessage(err.message);
                }
            });
        return () => {
            isCurrent = false;
        };
    }, [authToken, authUser, entry?.entry.glyph]);

    useEffect(() => {
        if (!authToken || route.page !== "preferences") {
            return;
        }
        let isCurrent = true;
        getPreferenceOverview(authToken)
            .then((payload) => {
                if (isCurrent) {
                    setAllComponentMeanings(payload.componentMeanings);
                    setStandardDefinitionPreferences(payload.standardDefinitionPreferences);
                    setPreferenceMessage("");
                }
            })
            .catch((err: Error) => {
                if (isCurrent) {
                    setPreferenceMessage(err.message);
                }
            });
        return () => {
            isCurrent = false;
        };
    }, [authToken, route.page]);

    useEffect(() => {
        if (!authSession?.expiresAt) {
            return;
        }
        const expiresAtMs = Date.parse(authSession.expiresAt);
        if (!Number.isFinite(expiresAtMs)) {
            return;
        }
        const warningDelay = Math.max(0, expiresAtMs - Date.now() - SESSION_WARNING_MS);
        const expiryDelay = Math.max(0, expiresAtMs - Date.now());
        const warningTimer = window.setTimeout(() => setSessionWarningVisible(true), warningDelay);
        const expiryTimer = window.setTimeout(() => clearAuth("Session expired. Sign in again to continue."), expiryDelay);
        return () => {
            window.clearTimeout(warningTimer);
            window.clearTimeout(expiryTimer);
        };
    }, [authSession, clearAuth]);

    const setRoute = useCallback((next: RouteState) => {
        const targetPath = formatRoutePath(next);
        if (window.location.pathname === targetPath) {
            setRouteState(next);
        } else {
            window.history.pushState(null, "", targetPath);
            setRouteState(next);
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

    const openGlyphDefinitions = useCallback(
        (glyph: string) => {
            setRoute({ page: "preferences", section: "glyphs-definitions", glyph });
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

    const applyPreferencePayload = useCallback(
        (glyph: string, preferences: ComponentMeaningPreference[], useStandardDefinitionInMnemonics?: boolean) => {
            if (entry?.entry.glyph === glyph) {
                setComponentMeanings(preferences);
            }
            setAllComponentMeanings((current) => [
                ...current.filter((preference) => preference.componentGlyph !== glyph),
                ...preferences,
            ]);
            if (useStandardDefinitionInMnemonics !== undefined) {
                setStandardDefinitionPreferences((current) => [
                    ...current.filter((preference) => preference.componentGlyph !== glyph),
                    ...(useStandardDefinitionInMnemonics
                        ? [{ componentGlyph: glyph, useInMnemonics: true, updatedAt: new Date().toISOString() }]
                        : []),
                ]);
            }
        },
        [entry?.entry.glyph],
    );

    const saveGlyphDefinitionSet = useCallback(
        async (
            glyph: string,
            definitions: Array<{ id?: string; meaning: string; componentToken?: string | null; notes?: string | null; useInMnemonics?: boolean }>,
            useStandardDefinitionInMnemonics: boolean,
        ) => {
            if (!authToken || !authUser) {
                setPreferenceMessage("Sign in to save user definitions.");
                setRoute({ page: "auth" });
                return false;
            }
            if (definitions.some((definition) => definition.meaning.trim().length > MAX_USER_DEFINITION_LENGTH)) {
                setPreferenceMessage(`User definitions must be ${MAX_USER_DEFINITION_LENGTH} characters or fewer.`);
                return false;
            }
            setPreferenceBusy(true);
            setPreferenceMessage("");
            try {
                const payload = await saveComponentMeaningSet(glyph, definitions, useStandardDefinitionInMnemonics, authToken);
                applyPreferencePayload(glyph, payload.componentMeanings, payload.useStandardDefinitionInMnemonics ?? useStandardDefinitionInMnemonics);
                setPreferenceMessage("Saved glyph definitions.");
                return true;
            } catch (err) {
                setPreferenceMessage(err instanceof Error ? err.message : "Could not save glyph definitions.");
                return false;
            } finally {
                setPreferenceBusy(false);
            }
        },
        [applyPreferencePayload, authToken, authUser, setRoute],
    );

    const submitSignIn = useCallback(
        async (input: { identifier: string; password: string }) => {
            setAuthBusy(true);
            setAuthMessage("");
            try {
                const response = await signIn(input);
                if (applyAuthResponse(response)) {
                    setAuthMessage("Signed in.");
                    setRoute({ page: "preferences" });
                    return true;
                }
                return false;
            } catch (err) {
                setAuthMessage(err instanceof Error ? err.message : "Authentication failed.");
                return false;
            } finally {
                setAuthBusy(false);
            }
        },
        [applyAuthResponse, setRoute],
    );

    const submitRegister = useCallback(
        async (input: { displayName: string; email: string; password: string }) => {
            setAuthBusy(true);
            setAuthMessage("");
            try {
                const response = await registerAccount(input);
                if (applyAuthResponse(response)) {
                    setAuthMessage("Account created.");
                    setRoute({ page: "preferences" });
                    return true;
                }
                return false;
            } catch (err) {
                setAuthMessage(err instanceof Error ? err.message : "Registration failed.");
                return false;
            } finally {
                setAuthBusy(false);
            }
        },
        [applyAuthResponse, setRoute],
    );

    const submitSignOut = useCallback(async () => {
        setAuthBusy(true);
        try {
            await signOut(authToken);
        } finally {
            setAuthBusy(false);
            clearAuth("Signed out.");
            setRoute({ page: "auth" });
        }
    }, [authToken, clearAuth, setRoute]);

    const dynamicNavItems = useMemo(
        () => [
            ...navItems,
            authUser
                ? { page: "preferences" as const, label: authUser.displayName ? `Profile: ${authUser.displayName}` : "Profile" }
                : { page: "auth" as const, label: "Sign in" },
        ],
        [authUser],
    );

    const activeStandardDefinitionPreference = useMemo(() => {
        if (route.section !== "glyphs-definitions" || !route.glyph) {
            return false;
        }
        return Boolean(standardDefinitionPreferences.find((preference) => preference.componentGlyph === route.glyph)?.useInMnemonics);
    }, [route.glyph, route.section, standardDefinitionPreferences]);

    const activeStandardDefinition = route.section === "glyphs-definitions" && route.glyph && entry?.entry.glyph === route.glyph
        ? entry.entry.k_definition ?? ""
        : "";

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
                    {dynamicNavItems.map((item) => (
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
                    componentMeanings={componentMeanings}
                    onFiltersChange={setFilters}
                    onSearchSubmit={() => runSearch()}
                    onSelectGlyph={selectGlyph}
                    onToggleRaw={() => setRawVisible((visible) => !visible)}
                    onOpenDetail={openCharacterDetail}
                    onCreateCard={openCreatePage}
                    onOpenGlyphDefinitions={openGlyphDefinitions}
                />
            ) : null}

            {route.page === "detail" ? (
                <CharacterDetailPage
                    detailLookup={detailLookup}
                    entry={entry}
                    componentMeanings={componentMeanings}
                    rawVisible={rawVisible}
                    onDetailLookupChange={setDetailLookup}
                    onSubmitDetailLookup={submitDetailLookup}
                    onToggleRaw={() => setRawVisible((visible) => !visible)}
                    onOpenDetail={openCharacterDetail}
                    onCreateCard={openCreatePage}
                    onOpenGlyphDefinitions={openGlyphDefinitions}
                />
            ) : null}

            {route.page === "create" ? <CreatePage selectedGlyph={route.glyph ?? selectedGlyph ?? ""} onOpenExplorer={() => setRoute({ page: "explore" })} /> : null}

            {route.page === "preferences" ? (
                <PreferencesPage
                    user={authUser}
                    isGlyphDefinitionsPage={route.section === "glyphs-definitions"}
                    activeGlyph={route.section === "glyphs-definitions" ? route.glyph : undefined}
                    standardDefinition={activeStandardDefinition}
                    useStandardDefinitionInMnemonics={activeStandardDefinitionPreference}
                    allPreferences={allComponentMeanings}
                    isBusy={authBusy || preferenceBusy}
                    message={preferenceMessage || authMessage}
                    onSignOut={submitSignOut}
                    onSignIn={() => setRoute({ page: "auth" })}
                    onOpenGlyph={openGlyphDefinitions}
                    onBackToPreferences={() => setRoute({ page: "preferences", section: "glyphs-definitions" })}
                    onOpenDetail={openCharacterDetail}
                    onSaveDefinitions={saveGlyphDefinitionSet}
                />
            ) : null}

            {route.page === "auth" ? (
                <SignInPage
                    user={authUser}
                    message={authMessage}
                    isBusy={authBusy}
                    onNavigateToRegister={() => setRoute({ page: "register" })}
                    onSignIn={submitSignIn}
                />
            ) : null}

            {route.page === "register" ? (
                <RegisterPage
                    user={authUser}
                    message={authMessage}
                    isBusy={authBusy}
                    onNavigateToSignIn={() => setRoute({ page: "auth" })}
                    onRegister={submitRegister}
                />
            ) : null}

            {sessionWarningVisible && authUser ? (
                <div className="session-warning" role="alert">
                    <div>
                        <strong>Your session is about to expire.</strong>
                        <span>Stay signed in to keep working.</span>
                    </div>
                    <button className="primary-button" disabled={authBusy} type="button" onClick={() => refreshSession({ showErrors: true })}>
                        Stay signed in
                    </button>
                </div>
            ) : null}
        </div>
    );
}

function parseRoute(): RouteState {
    const cleaned = window.location.pathname.replace(/^\/?/, "");
    const [rawPage, ...parts] = cleaned.split("/");
    const page = isPage(rawPage) ? rawPage : "home";
    const section = page === "preferences" && parts[0] ? decodeURIComponent(parts[0]) : undefined;
    const glyphParts = section ? parts.slice(1) : parts;
    const glyph = glyphParts.length ? decodeURIComponent(glyphParts.join("/")) : undefined;
    return { page, section, glyph };
}

function formatRoutePath(route: RouteState): string {
    const section = route.section ? `/${encodeURIComponent(route.section)}` : "";
    const glyph = route.glyph ? `/${encodeURIComponent(route.glyph)}` : "";
    return `/${route.page}${section}${glyph}`;
}

function isPage(value: string): value is Page {
    return pages.includes(value as Page);
}
