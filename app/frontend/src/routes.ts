export type Page = "home" | "explore" | "detail" | "create" | "preferences" | "auth" | "register";

export interface RouteState {
    page: Page;
    glyph?: string;
}

export const pages: Page[] = ["home", "explore", "detail", "create", "preferences", "auth", "register"];

export const navItems: Array<{ page: Page; label: string }> = [
    { page: "home", label: "Home" },
    { page: "explore", label: "Explore" },
    { page: "detail", label: "Character" },
    { page: "create", label: "Create" },
];
