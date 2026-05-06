import type { AuthResponse, EntryResponse, MetadataResponse, SearchFilters, SearchResponse } from "./types";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

async function getJson<T>(path: string): Promise<T> {
    const response = await fetch(`${API_BASE}${path}`);
    const payload = await response.json();
    if (!response.ok) {
        throw new Error(payload.error ?? "Request failed");
    }
    return payload as T;
}

async function sendJson<T>(path: string, body: unknown, token?: string): Promise<T> {
    const response = await fetch(`${API_BASE}${path}`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(body),
    });
    const payload = await response.json();
    if (!response.ok) {
        throw new Error(payload.error ?? "Request failed");
    }
    return payload as T;
}

async function getJsonWithAuth<T>(path: string, token?: string): Promise<T> {
    const response = await fetch(`${API_BASE}${path}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    const payload = await response.json();
    if (!response.ok) {
        throw new Error(payload.error ?? "Request failed");
    }
    return payload as T;
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
