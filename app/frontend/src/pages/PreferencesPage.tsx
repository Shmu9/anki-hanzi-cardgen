import { type FormEvent, type MouseEvent, useEffect, useMemo, useRef, useState } from "react";
import type { AuthUser, ComponentMeaningPreference } from "../types";

interface PreferencesPageProps {
    user: AuthUser | null;
    isGlyphDefinitionsPage: boolean;
    activeGlyph?: string;
    standardDefinition: string;
    useStandardDefinitionInMnemonics: boolean;
    allPreferences: ComponentMeaningPreference[];
    isBusy: boolean;
    message: string;
    onSignOut: () => void;
    onSignIn: () => void;
    onOpenGlyph: (glyph: string) => void;
    onBackToPreferences: () => void;
    onOpenDetail: (glyph: string) => void;
    onSaveDefinitions: (glyph: string, definitions: DraftDefinition[], useStandardDefinitionInMnemonics: boolean) => Promise<boolean>;
}

interface DraftDefinition {
    id?: string;
    meaning: string;
    componentToken?: string | null;
    notes?: string | null;
    useInMnemonics?: boolean;
}

const EMPTY_PREFERENCES: ComponentMeaningPreference[] = [];
const MAX_USER_DEFINITION_LENGTH = 50;

export function PreferencesPage({
    user,
    isGlyphDefinitionsPage,
    activeGlyph,
    standardDefinition,
    useStandardDefinitionInMnemonics,
    allPreferences,
    isBusy,
    message,
    onSignOut,
    onSignIn,
    onOpenGlyph,
    onBackToPreferences,
    onOpenDetail,
    onSaveDefinitions,
}: PreferencesPageProps) {
    const groupedPreferences = useMemo(() => groupByGlyph(allPreferences), [allPreferences]);
    const activePreferences = activeGlyph ? groupedPreferences.get(activeGlyph) ?? EMPTY_PREFERENCES : EMPTY_PREFERENCES;

    // TODO: consider splitting into new page/component
    if (isGlyphDefinitionsPage) {
        return (
            <main className="page-content">
                <section className="page-hero compact-hero">
                    <div>
                        <p className="eyebrow">Glyph definitions</p>
                        <h1>{activeGlyph ? `Tune ${activeGlyph} definitions.` : "Tune glyph definitions."}</h1>
                        <p>Save alternate component meanings and choose which ones should feed mnemonic generation.</p>
                    </div>
                </section>

                <section className="settings-grid glyph-definitions-page">
                    {message ? <p className="auth-message glyph-definitions-message">{message}</p> : null}
                    {activeGlyph ? (
                        <GlyphDefinitionPanel
                            glyph={activeGlyph}
                            standardDefinition={standardDefinition}
                            useStandardDefinitionInMnemonics={useStandardDefinitionInMnemonics}
                            preferences={activePreferences}
                            isBusy={isBusy}
                            onBack={onBackToPreferences}
                            onOpenDetail={onOpenDetail}
                            onSaveDefinitions={onSaveDefinitions}
                        />
                    ) : (
                        <GlyphPreferenceIndex
                            preferencesByGlyph={groupedPreferences}
                            isSignedIn={Boolean(user)}
                            onOpenGlyph={onOpenGlyph}
                        />
                    )}
                </section>
            </main>
        );
    }

    return (
        <main className="page-content">
            <section className="page-hero compact-hero">
                <div>
                    <p className="eyebrow">User preferences</p>
                    <h1>Make mnemonic generation personal.</h1>
                    <p>Keep radical meanings, alternate interpretations, profile defaults, and card enrichment choices separate from dictionary data.</p>
                </div>
            </section>

            <section className="settings-grid">
                <ProfilePanel user={user} isBusy={isBusy} message={message} onSignOut={onSignOut} onSignIn={onSignIn} />
                <MnemonicProfilePanel />
                {activeGlyph ? (
                    <GlyphDefinitionPanel
                        glyph={activeGlyph}
                        standardDefinition={standardDefinition}
                        useStandardDefinitionInMnemonics={useStandardDefinitionInMnemonics}
                        preferences={activePreferences}
                        isBusy={isBusy}
                        onBack={onBackToPreferences}
                        onOpenDetail={onOpenDetail}
                        onSaveDefinitions={onSaveDefinitions}
                    />
                ) : (
                    <GlyphPreferenceIndex
                        preferencesByGlyph={groupedPreferences}
                        isSignedIn={Boolean(user)}
                        onOpenGlyph={onOpenGlyph}
                    />
                )}
            </section>
        </main>
    );
}

function ProfilePanel({
    user,
    isBusy,
    message,
    onSignOut,
    onSignIn,
}: {
    user: AuthUser | null;
    isBusy: boolean;
    message: string;
    onSignOut: () => void;
    onSignIn: () => void;
}) {
    return (
        <div className="panel form-panel">
            <div className="section-heading">
                <h2>Profile</h2>
                <span>{user ? "Signed in" : "Signed out"}</span>
            </div>
            {user ? (
                <>
                    <div className="account-summary">
                        <strong>{user.displayName || user.email || user.username}</strong>
                        <span>{user.email || user.username}</span>
                    </div>
                    <button className="secondary-button" disabled={isBusy} type="button" onClick={onSignOut}>
                        Sign out
                    </button>
                </>
            ) : (
                <>
                    <p className="muted-copy">Sign in to save mnemonic profiles and component meanings to your account.</p>
                    <button className="primary-button" type="button" onClick={onSignIn}>
                        Sign in
                    </button>
                </>
            )}
            {message ? <p className="auth-message">{message}</p> : null}
        </div>
    );
}

function MnemonicProfilePanel() {
    return (
        <form className="panel form-panel">
            <div className="section-heading">
                <h2>Mnemonic profile</h2>
                <span>Default</span>
            </div>
            <label className="field">
                <span>Profile name</span>
                <input defaultValue="Scene-based learner" />
            </label>
            <div className="form-grid">
                <label className="field">
                    <span>Structure</span>
                    <select defaultValue="Scene Story">
                        <option>Scene Story</option>
                        <option>Component Ladder</option>
                        <option>Sound Hook</option>
                        <option>Compact Cue</option>
                    </select>
                </label>
                <label className="field">
                    <span>Tone hints</span>
                    <select defaultValue="When helpful">
                        <option>When helpful</option>
                        <option>Always</option>
                        <option>Never</option>
                    </select>
                </label>
            </div>
            <div className="toggle-row vertical">
                <label>
                    <input defaultChecked type="checkbox" /> Allow approved alternate radical meanings
                </label>
                <label>
                    <input defaultChecked type="checkbox" /> Include component definitions in prompt input
                </label>
                <label>
                    <input type="checkbox" /> Prefer compact exam-friendly phrasing
                </label>
            </div>
        </form>
    );
}

function GlyphPreferenceIndex({
    preferencesByGlyph,
    isSignedIn,
    onOpenGlyph,
}: {
    preferencesByGlyph: Map<string, ComponentMeaningPreference[]>;
    isSignedIn: boolean;
    onOpenGlyph: (glyph: string) => void;
}) {
    const [glyphDraft, setGlyphDraft] = useState("");
    const rows = Array.from(preferencesByGlyph.entries()).sort(([left], [right]) => left.localeCompare(right));

    const submit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const glyph = glyphDraft.trim();
        if (glyph) {
            onOpenGlyph(glyph);
        }
    };

    return (
        <div className="panel form-panel">
            <div className="section-heading">
                <h2>Glyph definitions</h2>
                <span>{rows.length}</span>
            </div>
            <form className="inline-preference-form" onSubmit={submit}>
                <label className="field">
                    <span>Glyph</span>
                    <input disabled={!isSignedIn} placeholder="阝" value={glyphDraft} onChange={(event) => setGlyphDraft(event.target.value)} />
                </label>
                <button className="primary-button" disabled={!isSignedIn} type="submit">
                    Open
                </button>
            </form>
            {!rows.length ? (
                <div className="preference-row">
                    <span className="queue-glyph">阝</span>
                    <div>
                        <strong>No saved user definitions yet</strong>
                        <a href={glyphDefinitionsPath("阝")} onClick={(event) => handleGlyphLink(event, "阝", onOpenGlyph)}>
                            Open /preferences/glyphs-definitions/阝
                        </a>
                    </div>
                </div>
            ) : null}
            <div className="preference-table">
                {rows.map(([glyph, preferences]) => (
                    <div className="preference-row" key={glyph}>
                        <span className="queue-glyph">{glyph}</span>
                        <div>
                            <strong>{preferences.map((preference) => preference.meaning).join(" / ")}</strong>
                            <a href={glyphDefinitionsPath(glyph)} onClick={(event) => handleGlyphLink(event, glyph, onOpenGlyph)}>
                                {decodeURIComponent(glyphDefinitionsPath(glyph))}
                            </a>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}

function GlyphDefinitionPanel({
    glyph,
    standardDefinition,
    useStandardDefinitionInMnemonics,
    preferences,
    isBusy,
    onBack,
    onOpenDetail,
    onSaveDefinitions,
}: {
    glyph: string;
    standardDefinition: string;
    useStandardDefinitionInMnemonics: boolean;
    preferences: ComponentMeaningPreference[];
    isBusy: boolean;
    onBack: () => void;
    onOpenDetail: (glyph: string) => void;
    onSaveDefinitions: (glyph: string, definitions: DraftDefinition[], useStandardDefinitionInMnemonics: boolean) => Promise<boolean>;
}) {
    const [definitionDraft, setDefinitionDraft] = useState("");
    const savedDefinitions = useMemo(() => preferences.map(preferenceToDraft), [preferences]);
    const [draftDefinitions, setDraftDefinitions] = useState<DraftDefinition[]>(() => savedDefinitions);
    const [draftUseStandardDefinition, setDraftUseStandardDefinition] = useState(useStandardDefinitionInMnemonics);
    const mnemonicCount = draftDefinitions.filter((preference) => preference.useInMnemonics).length;
    const savedSignature = useMemo(() => definitionsSignature(savedDefinitions), [savedDefinitions]);
    const draftSignature = useMemo(() => definitionsSignature(draftDefinitions), [draftDefinitions]);
    const hasChanges = savedSignature !== draftSignature || useStandardDefinitionInMnemonics !== draftUseStandardDefinition;
    const lastGlyphRef = useRef(glyph);
    const lastSavedSignatureRef = useRef(savedSignature);
    const lastSavedStandardDefinitionRef = useRef(useStandardDefinitionInMnemonics);
    const syncAfterSaveRef = useRef(false);

    useEffect(() => {
        const glyphChanged = lastGlyphRef.current !== glyph;
        const wasDirtyBeforeSavedUpdate = draftSignature !== lastSavedSignatureRef.current;
        const wasStandardDirtyBeforeSavedUpdate = draftUseStandardDefinition !== lastSavedStandardDefinitionRef.current;
        if (glyphChanged || syncAfterSaveRef.current || (!wasDirtyBeforeSavedUpdate && !wasStandardDirtyBeforeSavedUpdate)) {
            setDraftDefinitions(savedDefinitions);
            setDraftUseStandardDefinition(useStandardDefinitionInMnemonics);
            if (glyphChanged || syncAfterSaveRef.current) {
                setDefinitionDraft("");
            }
            syncAfterSaveRef.current = false;
        }
        lastGlyphRef.current = glyph;
        lastSavedSignatureRef.current = savedSignature;
        lastSavedStandardDefinitionRef.current = useStandardDefinitionInMnemonics;
    }, [draftSignature, draftUseStandardDefinition, glyph, savedDefinitions, savedSignature, useStandardDefinitionInMnemonics]);

    const submitAdd = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const meaning = definitionDraft.trim();
        if (!meaning || meaning.length > MAX_USER_DEFINITION_LENGTH || draftDefinitions.length >= 5) {
            return;
        }
        setDraftDefinitions((current) => [
            ...current,
            {
                id: `draft-${Date.now()}-${Math.random().toString(36).slice(2)}`,
                meaning,
                useInMnemonics: false,
            },
        ]);
        setDefinitionDraft("");
    };

    const moveDefinition = (id: string | undefined, direction: -1 | 1) => {
        setDraftDefinitions((current) => {
            const currentIndex = current.findIndex((definition) => definition.id === id);
            const nextIndex = currentIndex + direction;
            if (currentIndex < 0 || nextIndex < 0 || nextIndex >= current.length) {
                return current;
            }
            const next = [...current];
            [next[currentIndex], next[nextIndex]] = [next[nextIndex], next[currentIndex]];
            return next;
        });
    };

    const updateDefinition = (id: string | undefined, input: Partial<DraftDefinition>) => {
        setDraftDefinitions((current) => current.map((definition) => (definition.id === id ? { ...definition, ...input } : definition)));
    };

    const deleteDefinition = (id: string | undefined) => {
        setDraftDefinitions((current) => current.filter((definition) => definition.id !== id));
    };

    const saveDefinitions = async () => {
        const normalized = draftDefinitions.map((definition) => ({
            ...definition,
            meaning: definition.meaning.trim(),
        }));
        if (normalized.some((definition) => !definition.meaning || definition.meaning.length > MAX_USER_DEFINITION_LENGTH)) {
            return;
        }
        if (await onSaveDefinitions(glyph, normalized, draftUseStandardDefinition)) {
            syncAfterSaveRef.current = true;
        }
    };

    return (
        <div className="panel form-panel glyph-definition-panel">
            <div className="section-heading">
                <h2>{glyph} definitions</h2>
                <span>{draftDefinitions.length}/5 saved</span>
            </div>
            <div className="glyph-route-row">
                <button className="secondary-button" type="button" onClick={onBack}>
                    All glyphs
                </button>
                <button className="secondary-button" type="button" onClick={() => onOpenDetail(glyph)}>
                    Character detail
                </button>
                <a href={glyphDefinitionsPath(glyph)}>{decodeURIComponent(glyphDefinitionsPath(glyph))}</a>
            </div>
            <div className="mnemonic-default-row">
                <strong>Mnemonic generation</strong>
                <span>{mnemonicSummary(draftUseStandardDefinition, mnemonicCount)}</span>
            </div>
            <div className="glyph-standard-definition-row">
                <div>
                    <strong>Standard definition</strong>
                    <span>{standardDefinition || "Loading dictionary definition"}</span>
                </div>
                <label className="mnemonic-toggle">
                    <input
                        checked={draftUseStandardDefinition}
                        disabled={isBusy || !standardDefinition}
                        type="checkbox"
                        onChange={(event) => setDraftUseStandardDefinition(event.target.checked)}
                    />
                    <span>Always use</span>
                </label>
            </div>
            <div className="glyph-definition-list">
                {draftDefinitions.map((preference, index) => (
                    <EditableDefinitionRow
                        key={preference.id}
                        preference={preference}
                        index={index}
                        total={draftDefinitions.length}
                        mnemonicCount={mnemonicCount}
                        isBusy={isBusy}
                        onMovePreference={moveDefinition}
                        onUpdatePreference={updateDefinition}
                        onDeletePreference={deleteDefinition}
                    />
                ))}
            </div>
            <form className="inline-preference-form" onSubmit={submitAdd}>
                <label className="field">
                    <span>Definition</span>
                    <input
                        disabled={isBusy || draftDefinitions.length >= 5}
                        maxLength={MAX_USER_DEFINITION_LENGTH}
                        placeholder={draftDefinitions.length >= 5 ? "Maximum of 5 saved definitions" : "town / district"}
                        value={definitionDraft}
                        onChange={(event) => setDefinitionDraft(event.target.value)}
                    />
                </label>
                <button className="secondary-button" disabled={isBusy || draftDefinitions.length >= 5} type="submit">
                    Add
                </button>
            </form>
            <div className="form-actions">
                <button className="primary-button" disabled={isBusy || !hasChanges} type="button" onClick={saveDefinitions}>
                    Save
                </button>
                <button
                    className="secondary-button"
                    disabled={isBusy || (!hasChanges && !definitionDraft)}
                    type="button"
                    onClick={() => {
                        setDefinitionDraft("");
                        setDraftDefinitions(savedDefinitions);
                        setDraftUseStandardDefinition(useStandardDefinitionInMnemonics);
                    }}
                >
                    Reset
                </button>
            </div>
        </div>
    );
}

function EditableDefinitionRow({
    preference,
    index,
    total,
    mnemonicCount,
    isBusy,
    onMovePreference,
    onUpdatePreference,
    onDeletePreference,
}: {
    preference: DraftDefinition;
    index: number;
    total: number;
    mnemonicCount: number;
    isBusy: boolean;
    onMovePreference: (id: string | undefined, direction: -1 | 1) => void;
    onUpdatePreference: (id: string | undefined, input: Partial<DraftDefinition>) => void;
    onDeletePreference: (id: string | undefined) => void;
}) {
    const toggleMnemonic = (checked: boolean) => {
        if (checked && !preference.useInMnemonics && mnemonicCount >= 3) {
            return;
        }
        onUpdatePreference(preference.id, { useInMnemonics: checked });
    };

    return (
        <div className="glyph-definition-row">
            <div className="rank-stack">
                <strong>#{index + 1}</strong>
                <button className="mini-button" disabled={isBusy || index === 0} type="button" onClick={() => onMovePreference(preference.id, -1)}>
                    Up
                </button>
                <button className="mini-button" disabled={isBusy || index === total - 1} type="button" onClick={() => onMovePreference(preference.id, 1)}>
                    Down
                </button>
            </div>
            <label className="field">
                <span>Definition</span>
                <input
                    disabled={isBusy}
                    maxLength={MAX_USER_DEFINITION_LENGTH}
                    value={preference.meaning}
                    onChange={(event) => onUpdatePreference(preference.id, { meaning: event.target.value })}
                />
            </label>
            <label className="mnemonic-toggle">
                <input
                    checked={Boolean(preference.useInMnemonics)}
                    disabled={isBusy || (!preference.useInMnemonics && mnemonicCount >= 3)}
                    type="checkbox"
                    onChange={(event) => toggleMnemonic(event.target.checked)}
                />
                <span>Always use</span>
            </label>
            <button className="secondary-button" disabled={isBusy} type="button" onClick={() => onDeletePreference(preference.id)}>
                Delete
            </button>
        </div>
    );
}

function preferenceToDraft(preference: ComponentMeaningPreference): DraftDefinition {
    return {
        id: preference.id,
        meaning: preference.meaning,
        componentToken: preference.componentToken,
        notes: preference.notes,
        useInMnemonics: preference.useInMnemonics,
    };
}

function definitionsSignature(definitions: DraftDefinition[]) {
    return JSON.stringify(definitions.map((definition) => ({
        id: definition.id ?? null,
        meaning: definition.meaning,
        notes: definition.notes ?? null,
        useInMnemonics: Boolean(definition.useInMnemonics),
    })));
}

function mnemonicSummary(useStandardDefinition: boolean, savedDefinitionCount: number) {
    const parts = [];
    if (useStandardDefinition) {
        parts.push("standard definition");
    }
    if (savedDefinitionCount) {
        parts.push(`${savedDefinitionCount} saved definition${savedDefinitionCount === 1 ? "" : "s"}`);
    }
    return parts.length ? `${parts.join(" + ")} always used` : "Standard dictionary definition by default";
}

function groupByGlyph(preferences: ComponentMeaningPreference[]) {
    const groups = new Map<string, ComponentMeaningPreference[]>();
    for (const preference of preferences) {
        const current = groups.get(preference.componentGlyph) ?? [];
        current.push(preference);
        groups.set(preference.componentGlyph, current);
    }
    for (const [glyph, glyphPreferences] of groups) {
        groups.set(glyph, [...glyphPreferences].sort((left, right) => left.rank - right.rank));
    }
    return groups;
}

function glyphDefinitionsPath(glyph: string) {
    return `/preferences/glyphs-definitions/${encodeURIComponent(glyph)}`;
}

function handleGlyphLink(event: MouseEvent<HTMLAnchorElement>, glyph: string, onOpenGlyph: (glyph: string) => void) {
    event.preventDefault();
    onOpenGlyph(glyph);
}
