export function text(value: unknown, fallback = "-"): string {
    return value === null || value === undefined || value === "" ? fallback : String(value);
}

export function compactDefinition(value: unknown, length = 95): string {
    const definition = text(value, "No definition");
    return definition.length > length ? `${definition.slice(0, length - 1)}...` : definition;
}
