import { useEffect, useId, useState } from "react";

interface PasswordFieldProps {
    autoComplete: "current-password" | "new-password";
    value: string;
    onChange: (value: string) => void;
    label?: string;
}

export function PasswordField({ autoComplete, value, onChange, label = "Password" }: PasswordFieldProps) {
    const inputId = useId();
    const [isVisible, setIsVisible] = useState(false);

    useEffect(() => {
        if (!value) {
            setIsVisible(false);
        }
    }, [value]);

    return (
        <div className="field">
            <label className="field-label" htmlFor={inputId}>
                {label}
            </label>
            <div className="password-field">
                <input
                    id={inputId}
                    autoComplete={autoComplete}
                    type={isVisible ? "text" : "password"}
                    value={value}
                    onChange={(event) => onChange(event.target.value)}
                />
                <button
                    aria-label={isVisible ? "Hide password" : "Show password"}
                    aria-pressed={isVisible}
                    className="password-toggle"
                    type="button"
                    onClick={() => setIsVisible((current) => !current)}
                >
                    {isVisible ? "Hide" : "Show"}
                </button>
            </div>
        </div>
    );
}
