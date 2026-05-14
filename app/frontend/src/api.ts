import type {
    AuthResponse,
    ComponentMeaningResponse,
    EntryResponse,
    MetadataResponse,
    PreferenceOverviewResponse,
    SearchFilters,
    SearchResponse,
} from "./types";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";
const API_ROOT = API_BASE.replace(/\/+$/, "");

function apiUrl(path: string) {
    return `${API_ROOT}${path}`;
}

async function getJson<T>(path: string): Promise<T> {
    const response = await fetch(apiUrl(path));
    const payload = await response.json();
    if (!response.ok) {
        throw new Error(errorMessage(payload));
    }
    return payload as T;
}

async function sendJson<T>(path: string, body: unknown, token?: string, method = "POST"): Promise<T> {
    const response = await fetch(apiUrl(path), {
        method,
        headers: {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(body),
    });
    const payload = await response.json();
    if (!response.ok) {
        throw new Error(errorMessage(payload));
    }
    return payload as T;
}

async function getJsonWithAuth<T>(path: string, token?: string): Promise<T> {
    const response = await fetch(apiUrl(path), {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    const payload = await response.json();
    if (!response.ok) {
        throw new Error(errorMessage(payload));
    }
    return payload as T;
}

async function sendJsonWithMethod<T>(path: string, body: unknown, token: string | undefined, method: string): Promise<T> {
    return sendJson<T>(path, body, token, method);
}

function errorMessage(payload: { error?: string; method?: string; path?: string }) {
    if (payload.error === "Method not allowed" && payload.method && payload.path) {
        return `${payload.error}: ${payload.method} ${payload.path}`;
    }
    return payload.error ?? "Request failed";
}

export function getMetadata(): Promise<MetadataResponse> {
    return getJson<MetadataResponse>("/api/metadata");
}

export function searchGlyphs(filters: SearchFilters): Promise<SearchResponse> {
    const params = new URLSearchParams();
    params.set("q", filters.q.trim());
    if (filters.hsk !== "any") {
        params.set("hsk", filters.hsk);
    }
    if (filters.strokeMin) {
        params.set("stroke_min", filters.strokeMin);
    }
    if (filters.strokeMax) {
        params.set("stroke_max", filters.strokeMax);
    }
    params.set("limit", "60");
    return getJson<SearchResponse>(`/api/search?${params.toString()}`);
}

export function getEntry(key: string): Promise<EntryResponse> {
    return getJson<EntryResponse>(`/api/glyph/${encodeURIComponent(key)}`);
}

export function getPreferenceOverview(token?: string): Promise<PreferenceOverviewResponse> {
    return getJsonWithAuth<PreferenceOverviewResponse>("/api/preferences", token);
}

export function getComponentMeanings(glyph: string, token?: string): Promise<ComponentMeaningResponse> {
    const params = new URLSearchParams();
    params.set("glyph", glyph);
    return getJsonWithAuth<ComponentMeaningResponse>(`/api/preferences/radicals?${params.toString()}`, token);
}

export function saveComponentMeaning(input: { glyph: string; meaning: string; componentToken?: string }, token?: string): Promise<ComponentMeaningResponse> {
    return sendJson<ComponentMeaningResponse>("/api/preferences/radicals", input, token);
}

export function updateComponentMeaning(
    glyph: string,
    id: string,
    input: { meaning?: string; notes?: string; useInMnemonics?: boolean },
    token?: string,
): Promise<ComponentMeaningResponse> {
    return sendJsonWithMethod<ComponentMeaningResponse>(
        `/api/preferences/radicals/${encodeURIComponent(glyph)}/${encodeURIComponent(id)}`,
        input,
        token,
        "PATCH",
    );
}

export async function deleteComponentMeaning(glyph: string, id: string, token?: string): Promise<ComponentMeaningResponse> {
    const response = await fetch(apiUrl(`/api/preferences/radicals/${encodeURIComponent(glyph)}/${encodeURIComponent(id)}`), {
        method: "DELETE",
        headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    const payload = await response.json();
    if (!response.ok) {
        throw new Error(errorMessage(payload));
    }
    return payload as ComponentMeaningResponse;
}

export function rankComponentMeanings(glyph: string, ids: string[], token?: string): Promise<ComponentMeaningResponse> {
    return sendJson<ComponentMeaningResponse>(`/api/preferences/radicals/${encodeURIComponent(glyph)}`, { ids }, token, "PUT");
}

export function saveComponentMeaningSet(
    glyph: string,
    definitions: Array<{ id?: string; meaning: string; componentToken?: string | null; notes?: string | null; useInMnemonics?: boolean }>,
    useStandardDefinitionInMnemonics: boolean,
    token?: string,
): Promise<ComponentMeaningResponse> {
    return sendJson<ComponentMeaningResponse>(
        `/api/preferences/radicals/${encodeURIComponent(glyph)}`,
        { definitions, useStandardDefinitionInMnemonics },
        token,
        "PUT",
    );
}

export function registerAccount(input: {
    displayName: string;
    email: string;
    password: string;
}): Promise<AuthResponse> {
    return sendJson<AuthResponse>("/api/auth/register", input);
}

export function signIn(input: { identifier: string; password: string }): Promise<AuthResponse> {
    return sendJson<AuthResponse>("/api/auth/sign-in", input);
}

export function getSession(token?: string): Promise<AuthResponse> {
    return getJsonWithAuth<AuthResponse>("/api/auth/session", token);
}

export function signOut(token?: string): Promise<AuthResponse> {
    return sendJson<AuthResponse>("/api/auth/sign-out", {}, token);
}
