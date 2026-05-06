import { compactDefinition, text } from "../format";
import type { BaseComponent, DecompositionNode, EntryResponse, ReuseRow } from "../types";
import type { CSSProperties } from "react";

interface EntryDetailProps {
    payload: EntryResponse | null;
    rawVisible: boolean;
    onSelectGlyph: (glyph: string) => void;
    onOpenDetail?: (glyph: string) => void;
    onCreateCard?: (glyph: string) => void;
}

export function EntryDetail({ payload, rawVisible, onSelectGlyph, onOpenDetail, onCreateCard }: EntryDetailProps) {
    if (!payload) {
        return (
            <section className="detail-pane">
                <div className="empty-state">
                    <div className="empty-glyph">字</div>
                    <h2>Select a character</h2>
                </div>
            </section>
        );
    }

    const entry = payload.entry;
    const badges = [
        entry.hsk_level ? `HSK ${entry.hsk_level}` : null,
        entry.frequency_rank ? `Rank ${entry.frequency_rank}` : null,
        entry.kind,
        entry.decomp_type ? `IDS ${entry.decomp_type}` : null,
    ].filter(Boolean);

    return (
        <section className="detail-pane">
            <article className="entry-view">
                <header className="entry-header">
                    <div className="glyph-block">
                        <div className="entry-glyph">{text(entry.glyph, "?")}</div>
                        <div>
                            <h2>
                                {text(entry.glyph, "?")} {text(entry.unicode)}
                            </h2>
                            <p id="entryReading">{[entry.k_mandarin, entry.k_japanese].filter(Boolean).join(" · ") || "No reading"}</p>
                        </div>
                    </div>
                    <div className="entry-tools">
                        <div className="badge-row">
                            {badges.map((badge) => (
                                <span className="badge" key={badge}>
                                    {badge}
                                </span>
                            ))}
                        </div>
                        <div className="entry-action-row">
                            {entry.glyph && onOpenDetail ? (
                                <button className="secondary-button" type="button" onClick={() => onOpenDetail(entry.glyph!)}>
                                    Detail
                                </button>
                            ) : null}
                            {entry.glyph && onCreateCard ? (
                                <button className="primary-button" type="button" onClick={() => onCreateCard(entry.glyph!)}>
                                    Create card
                                </button>
                            ) : null}
                        </div>
                    </div>
                </header>

                <section className="definition-band">
                    <h3>Definition</h3>
                    <p>{text(entry.k_definition, "No definition available.")}</p>
                </section>

                <section className="metric-grid">
                    <Metric label="Token" value={entry.token} />
                    <Metric label="Codepoint" value={entry.codepoint} />
                    <Metric label="Strokes" value={entry.stroke_count} />
                    <Metric label="Radical Stroke" value={entry.rs_unicode} />
                    <Metric label="Hanyu Pinlu" value={entry.k_hanyu_pinlu} />
                    <Metric label="Adobe RS" value={entry.rs_adobe_japan1_6} />
                    <Metric label="Mandarin" value={entry.k_mandarin} />
                    <Metric label="Japanese" value={entry.k_japanese} />
                </section>

                <section className="panel">
                    <div className="section-heading">
                        <h3>Nearest Defined Components</h3>
                        <span>{payload.base_components_with_definition.length}</span>
                    </div>
                    <div className="component-list">
                        {payload.base_components_with_definition.map((component) => (
                            <ComponentPill component={component} key={componentKey(component)} onSelectGlyph={onSelectGlyph} />
                        ))}
                    </div>
                </section>

                <section className="workspace-grid">
                    <div className="panel">
                        <div className="section-heading">
                            <h3>IDS Breakdown</h3>
                            <span>{payload.decomposition_tree.decomp_label || entry.decomp_type || "none"}</span>
                        </div>
                        <div className="tree-view">
                            <TreeNode node={payload.decomposition_tree} />
                        </div>
                    </div>

                    <div className="panel">
                        <div className="section-heading">
                            <h3>Base Components</h3>
                            <span>{payload.base_components.length}</span>
                        </div>
                        <div className="component-list">
                            {payload.base_components.map((component) => (
                                <ComponentPill component={component} key={componentKey(component)} onSelectGlyph={onSelectGlyph} />
                            ))}
                        </div>
                    </div>
                </section>

                <section className="panel">
                    <div className="section-heading">
                        <h3>Direct Reuse</h3>
                        <span>{payload.referenced_by.length}</span>
                    </div>
                    <div className="reuse-list">
                        {payload.referenced_by.map((row) => (
                            <ReusePill row={row} key={row.glyph} onSelectGlyph={onSelectGlyph} />
                        ))}
                    </div>
                </section>

                {rawVisible ? (
                    <section className="panel raw-panel">
                        <div className="section-heading">
                            <h3>Raw Record</h3>
                        </div>
                        <pre>{JSON.stringify(payload, null, 2)}</pre>
                    </section>
                ) : null}
            </article>
        </section>
    );
}

function Metric({ label, value }: { label: string; value: unknown }) {
    return (
        <div className="metric">
            <span>{label}</span>
            <strong>{text(value)}</strong>
        </div>
    );
}

function TreeNode({ node }: { node: DecompositionNode }) {
    const isIntermediate = Boolean(node.is_intermediate_component);
    const glyphText = isIntermediate ? node.decomp_type || "IDS" : text(node.glyph, node.token || "?");
    const title = isIntermediate ? "Intermediate component" : text(node.definition, node.token || "Component");
    const detailParts = isIntermediate
        ? [node.decomp_label || node.decomp_type, node.display_structure ? `structure ${node.display_structure}` : null, `${node.components.length} subcomponents`]
        : [node.mandarin, node.unicode || node.token, node.stroke_count ? `${node.stroke_count} strokes` : null];

    return (
        <>
            <div className="tree-node" style={{ "--depth": node.depth || 0 } as CSSProperties}>
                <div className={`tree-chip${isIntermediate ? " intermediate" : ""}`}>
                    <span className="tree-glyph">{glyphText}</span>
                    <span className="tree-text">
                        <strong>{title}</strong>
                        <span>{detailParts.filter(Boolean).join(" · ")}</span>
                    </span>
                    <span className="operator">{node.decomp_type || ""}</span>
                </div>
            </div>
            {node.components.map((child) => (
                <TreeNode node={child} key={`${child.depth}:${child.position ?? 0}:${child.glyph ?? child.token}`} />
            ))}
        </>
    );
}

function ComponentPill({ component, onSelectGlyph }: { component: BaseComponent; onSelectGlyph: (glyph: string) => void }) {
    const glyph = component.glyph || component.token;
    return (
        <button
            type="button"
            className="component-pill"
            title={component.token || component.glyph || ""}
            onClick={() => glyph && onSelectGlyph(glyph)}
        >
            <strong>{text(component.glyph, "?")}</strong>
            <span>{compactDefinition(component.definition || component.mandarin || component.token, 44)}</span>
        </button>
    );
}

function componentKey(component: BaseComponent) {
    return `${component.glyph ?? ""}:${component.token ?? ""}`;
}

function ReusePill({ row, onSelectGlyph }: { row: ReuseRow; onSelectGlyph: (glyph: string) => void }) {
    return (
        <button
            type="button"
            className="reuse-pill"
            title={compactDefinition(row.k_definition || row.k_mandarin || row.token)}
            onClick={() => onSelectGlyph(row.glyph)}
        >
            {row.glyph}
        </button>
    );
}
