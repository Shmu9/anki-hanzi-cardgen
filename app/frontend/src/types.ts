export type Nullable<T> = T | null;

export interface GlyphRow {
    id?: number;
    token: Nullable<string>;
    glyph: Nullable<string>;
    unicode: Nullable<string>;
    codepoint: Nullable<number>;
    kind: Nullable<string>;
    k_definition: Nullable<string>;
    k_mandarin: Nullable<string>;
    k_japanese: Nullable<string>;
    k_hanyu_pinlu: Nullable<string>;
    frequency_rank: Nullable<number>;
    hsk_level: Nullable<number>;
    stroke_count: Nullable<number>;
    rs_unicode: Nullable<string>;
    rs_adobe_japan1_6: Nullable<string>;
    decomp_type: Nullable<string>;
    decomp_components: Nullable<string>;
}

export interface MetadataResponse {
    db_path: string;
    glyph_count: number;
    unicode_count: number;
    metadata: Record<string, string>;
}

export interface SearchResponse {
    results: GlyphRow[];
}

export interface DecompositionNode {
    glyph: Nullable<string>;
    token: Nullable<string>;
    unicode?: Nullable<string>;
    kind: Nullable<string>;
    is_intermediate_component: boolean;
    display_title?: Nullable<string>;
    display_structure?: Nullable<string>;
    definition?: Nullable<string>;
    mandarin?: Nullable<string>;
    stroke_count?: Nullable<number>;
    decomp_type?: Nullable<string>;
    decomp_label?: Nullable<string>;
    depth: number;
    position?: number;
    components: DecompositionNode[];
}

export interface BaseComponent {
    glyph: Nullable<string>;
    token: Nullable<string>;
    position?: Nullable<number>;
    depth?: Nullable<number>;
    definition?: Nullable<string>;
    mandarin?: Nullable<string>;
}

export interface ReuseRow {
    glyph: string;
    token: Nullable<string>;
    k_definition: Nullable<string>;
    k_mandarin: Nullable<string>;
    stroke_count: Nullable<number>;
    frequency_rank: Nullable<number>;
}

export interface EntryResponse {
    entry: GlyphRow;
    decomposition_tree: DecompositionNode;
    base_components_with_definition: BaseComponent[];
    base_components: BaseComponent[];
    referenced_by: ReuseRow[];
}

export interface SearchFilters {
    q: string;
    hsk: string;
    strokeMin: string;
    strokeMax: string;
}

export interface AuthUser {
    id: string;
    email: Nullable<string>;
    username: Nullable<string>;
    displayName: Nullable<string>;
    status: string;
    createdAt: string;
    lastActiveAt: Nullable<string>;
}

export interface AuthSession {
    id: string;
    userId: string;
    createdAt: string;
    expiresAt: string;
    absoluteExpiresAt?: string;
}

export interface AuthResponse {
    authenticated: boolean;
    user?: AuthUser;
    session?: AuthSession;
    token?: string;
}
