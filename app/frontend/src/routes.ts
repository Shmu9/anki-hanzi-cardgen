export type Page = "home" | "explore" | "detail" | "create" | "preferences" | "auth";

export interface RouteState {
    page: Page;
    glyph?: string;
}

export const navItems: Array<{ page: Page; label: string }> = [
    { page: "home", label: "Home" },
    { page: "explore", label: "Explore" },
    { page: "detail", label: "Character" },
    { page: "create", label: "Create" },
    { page: "preferences", label: "Preferences" },
    { page: "auth", label: "Sign in" },
];
