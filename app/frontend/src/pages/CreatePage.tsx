interface CreatePageProps {
    selectedGlyph: string;
    onOpenExplorer: () => void;
}

export function CreatePage({ selectedGlyph, onOpenExplorer }: CreatePageProps) {
    return (
        <main className="page-content">
            <section className="page-hero compact-hero">
                <div>
                    <p className="eyebrow">Flashcard creation</p>
                    <h1>Stage cards before Anki sync.</h1>
                    <p>Generate default fields from dictionary data, choose deck settings, review media, and edit every field before export.</p>
                </div>
                <button className="secondary-button" type="button" onClick={onOpenExplorer}>
                    Add from explorer
                </button>
            </section>

            <section className="builder-layout">
                <form className="panel form-panel">
                    <div className="section-heading">
                        <h2>Card draft</h2>
                        <span>{selectedGlyph || "New"}</span>
                    </div>
                    <label className="field">
                        <span>Character</span>
                        <input defaultValue={selectedGlyph} placeholder="漢" />
                    </label>
                    <label className="field">
                        <span>Definition</span>
                        <textarea placeholder="Generated definition appears here" />
                    </label>
                    <label className="field">
                        <span>Mnemonic</span>
                        <textarea placeholder="Use the active radical preferences to generate or edit a mnemonic" />
                    </label>
                    <div className="form-grid">
                        <label className="field">
                            <span>Deck</span>
                            <select defaultValue="Mandarin::Characters">
                                <option>Mandarin::Characters</option>
                                <option>Mandarin::HSK</option>
                                <option>Mandarin::Components</option>
                            </select>
                        </label>
                        <label className="field">
                            <span>Note preset</span>
                            <select defaultValue="Single-character study">
                                <option>Single-character study</option>
                                <option>Recognition</option>
                                <option>Production</option>
                                <option>Decomposition study</option>
                            </select>
                        </label>
                    </div>
                    <label className="field">
                        <span>Tags</span>
                        <input placeholder="hsk, needs-review, sourced-audio" />
                    </label>
                    <div className="toggle-row">
                        <label>
                            <input defaultChecked type="checkbox" /> Audio
                        </label>
                        <label>
                            <input defaultChecked type="checkbox" /> Stroke order
                        </label>
                        <label>
                            <input defaultChecked type="checkbox" /> Decomposition
                        </label>
                    </div>
                    <div className="form-actions">
                        <button className="secondary-button" type="button">
                            Preview
                        </button>
                        <button className="primary-button" type="button">
                            Add to drafts
                        </button>
                    </div>
                </form>

                <aside className="panel draft-queue">
                    <div className="section-heading">
                        <h2>Draft queue</h2>
                        <span>3 staged</span>
                    </div>
                    {["郎", "良", "都"].map((glyph) => (
                        <div className="queue-row" key={glyph}>
                            <span className="queue-glyph">{glyph}</span>
                            <div>
                                <strong>{glyph === "郎" ? "young man; gentleman" : glyph === "良" ? "good; fine" : "metropolis; capital"}</strong>
                                <span>Needs mnemonic review</span>
                            </div>
                        </div>
                    ))}
                </aside>
            </section>
        </main>
    );
}
