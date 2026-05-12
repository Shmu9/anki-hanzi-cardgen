import { useState, type FormEvent } from "react";
import { AuthFormMessage } from "../components/AuthFormMessage";
import { AuthLayout } from "../components/AuthLayout";
import { PasswordField } from "../components/PasswordField";
import type { AuthUser } from "../types";

interface SignInPageProps {
    user: AuthUser | null;
    message: string;
    isBusy: boolean;
    onNavigateToRegister: () => void;
    onSignIn: (input: { identifier: string; password: string }) => Promise<boolean>;
}

export function SignInPage({ user, message, isBusy, onNavigateToRegister, onSignIn }: SignInPageProps) {
    const [identifier, setIdentifier] = useState("");
    const [password, setPassword] = useState("");

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const succeeded = await onSignIn({ identifier, password });
        if (succeeded) {
            setPassword("");
        }
    }

    return (
        <AuthLayout user={user}>
            <form className="panel form-panel" onSubmit={handleSubmit}>
                <div className="section-heading">
                    <h2>Sign in</h2>
                </div>
                <AuthFormMessage message={message} />
                <label className="field">
                    <span>Email</span>
                    <input autoComplete="username" type="email" value={identifier} onChange={(event) => setIdentifier(event.target.value)} />
                </label>
                <PasswordField autoComplete="current-password" value={password} onChange={setPassword} />
                <button className="primary-button" disabled={isBusy} type="submit">
                    Sign in
                </button>
                <button className="text-button" type="button" onClick={onNavigateToRegister}>
                    Create an account
                </button>
            </form>
        </AuthLayout>
    );
}
