export function PreferencesPage() {
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

                <form className="panel form-panel">
                    <div className="section-heading">
                        <h2>Radical meanings</h2>
                        <span>User scoped</span>
                    </div>
                    <div className="preference-table">
                        <PreferenceRow glyph="阝" position="right side" meaning="town / district" alternates="hill, slope, ear" />
                        <PreferenceRow glyph="良" position="component" meaning="good, fine" alternates="excellent, wholesome" />
                        <PreferenceRow glyph="氵" position="left side" meaning="water" alternates="liquid, river" />
                    </div>
                    <div className="form-grid">
                        <label className="field">
                            <span>Component</span>
                            <input placeholder="忄" />
                        </label>
                        <label className="field">
                            <span>Preferred meaning</span>
                            <input placeholder="heart, feeling" />
                        </label>
                    </div>
                    <button className="primary-button" type="button">
                        Save preference
                    </button>
                </form>
            </section>
        </main>
    );
}

function PreferenceRow({ glyph, position, meaning, alternates }: { glyph: string; position: string; meaning: string; alternates: string }) {
    return (
        <div className="preference-row">
            <span className="queue-glyph">{glyph}</span>
            <div>
                <strong>{meaning}</strong>
                <span>
                    {position} · alternates: {alternates}
                </span>
            </div>
        </div>
    );
}
