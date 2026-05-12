import { useMemo, useState, type FormEvent } from "react";
import { AuthFormMessage } from "../components/AuthFormMessage";
import { AuthLayout } from "../components/AuthLayout";
import { PasswordField } from "../components/PasswordField";
import { ValidationRuleList, passwordRuleStates, type PasswordRuleState } from "../components/PasswordRuleList";
import type { AuthUser } from "../types";

interface RegisterPageProps {
    user: AuthUser | null;
    message: string;
    isBusy: boolean;
    onNavigateToSignIn: () => void;
    onRegister: (input: { displayName: string; email: string; password: string }) => Promise<boolean>;
}

const displayNameCharactersPattern = /^[A-Za-z0-9_-]+$/;
const emailPattern = /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,63}$/i;

export function RegisterPage({ user, message, isBusy, onNavigateToSignIn, onRegister }: RegisterPageProps) {
    const [displayName, setDisplayName] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");

    const passwordRules = useMemo(() => passwordRuleStates(password, email, displayName), [displayName, email, password]);
    const displayNameRules = useMemo(() => displayNameRuleStates(displayName), [displayName]);
    const emailRules = useMemo(() => emailRuleStates(email), [email]);
    const displayNameStarted = displayName.length > 0;
    const emailStarted = email.length > 0;
    const passwordStarted = password.length > 0;
    const canRegister =
        displayNameStarted &&
        emailStarted &&
        passwordStarted &&
        displayNameRules.every(isRuleSatisfied) &&
        emailRules.every(isRuleSatisfied) &&
        passwordRules.every(isRuleSatisfied);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const succeeded = await onRegister({ displayName, email, password });
        if (succeeded) {
            setPassword("");
        }
    }

    return (
        <AuthLayout user={user}>
            <form className="panel form-panel" onSubmit={handleSubmit}>
                <div className="section-heading">
                    <h2>Register</h2>
                </div>
                <AuthFormMessage message={message} />
                <label className="field">
                    <span>Display name</span>
                    <input
                        autoComplete="name"
                        pattern="[A-Za-z0-9_-]{3,20}"
                        value={displayName}
                        onChange={(event) => setDisplayName(event.target.value)}
                    />
                </label>
                {displayNameStarted ? <ValidationRuleList ariaLabel="Display name rules" rules={displayNameRules} /> : null}
                <label className="field">
                    <span>Email</span>
                    <input autoComplete="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} />
                </label>
                {emailStarted ? <ValidationRuleList ariaLabel="Email rules" rules={emailRules} /> : null}
                <PasswordField autoComplete="new-password" value={password} onChange={setPassword} />
                {passwordStarted ? <ValidationRuleList ariaLabel="Password rules" rules={passwordRules} /> : null}
                <button className="primary-button" disabled={isBusy || !canRegister} type="submit">
                    Create account
                </button>
                <button className="text-button" type="button" onClick={onNavigateToSignIn}>
                    Already have an account
                </button>
            </form>
        </AuthLayout>
    );
}

function displayNameRuleStates(displayName: string): PasswordRuleState[] {
    const normalized = displayName.trim();
    return [
        {
            label: "At least 3 characters",
            satisfied: normalized.length >= 3,
        },
        {
            label: "No more than 20 characters",
            satisfied: normalized.length <= 20,
        },
        {
            label: "Uses letters, numbers, underscores, or hyphens",
            satisfied: displayNameCharactersPattern.test(normalized),
        },
    ];
}

function emailRuleStates(email: string): PasswordRuleState[] {
    return [
        {
            label: "Valid email address",
            satisfied: emailPattern.test(email.trim()),
        },
    ];
}

function isRuleSatisfied(rule: PasswordRuleState) {
    return (rule.status ?? (rule.satisfied ? "satisfied" : "unsatisfied")) === "satisfied";
}
