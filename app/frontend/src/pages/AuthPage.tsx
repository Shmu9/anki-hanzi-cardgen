import { useEffect, useState, type FormEvent } from "react";
import { getSession, registerAccount, signIn, signOut } from "../api";
import type { AuthResponse, AuthUser } from "../types";

const SESSION_TOKEN_KEY = "hanzi.sessionToken";

export function AuthPage() {
    const [user, setUser] = useState<AuthUser | null>(null);
    const [token, setToken] = useState(() => localStorage.getItem(SESSION_TOKEN_KEY) ?? "");
    const [signInIdentifier, setSignInIdentifier] = useState("");
    const [signInPassword, setSignInPassword] = useState("");
    const [displayName, setDisplayName] = useState("");
    const [registerEmail, setRegisterEmail] = useState("");
    const [registerPassword, setRegisterPassword] = useState("");
    const [message, setMessage] = useState("");
    const [isBusy, setIsBusy] = useState(false);

    useEffect(() => {
        if (!token) {
            setUser(null);
            return;
        }
        let isCurrent = true;
        getSession(token)
            .then((response) => {
                if (!isCurrent) {
                    return;
                }
                if (response.authenticated && response.user) {
                    setUser(response.user);
                    setMessage("");
                } else {
                    clearSession();
                }
            })
            .catch((error: Error) => {
                if (isCurrent) {
                    setMessage(error.message);
                }
            });
        return () => {
            isCurrent = false;
        };
    }, [token]);

    async function handleSignIn(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        await submitAuth(async () => signIn({ identifier: signInIdentifier, password: signInPassword }));
    }

    async function handleRegister(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        await submitAuth(async () =>
            registerAccount({
                displayName,
                email: registerEmail,
                password: registerPassword,
            })
        );
    }

    async function handleSignOut() {
        setIsBusy(true);
        try {
            await signOut(token);
        } finally {
            clearSession();
            setIsBusy(false);
        }
    }

    async function submitAuth(request: () => Promise<AuthResponse>) {
        setIsBusy(true);
        setMessage("");
        try {
            const response = await request();
            if (response.authenticated && response.user && response.token) {
                localStorage.setItem(SESSION_TOKEN_KEY, response.token);
                setToken(response.token);
                setUser(response.user);
                setSignInPassword("");
                setRegisterPassword("");
                setMessage("Signed in.");
            }
        } catch (error) {
            setMessage(error instanceof Error ? error.message : "Authentication failed.");
        } finally {
            setIsBusy(false);
        }
    }

    function clearSession() {
        localStorage.removeItem(SESSION_TOKEN_KEY);
        setToken("");
        setUser(null);
    }

    return (
        <main className="page-content auth-content">
            <section className="auth-layout">
                <div className="page-hero auth-copy">
                    <p className="eyebrow">Accounts</p>
                    <h1>Keep cards, preferences, and mnemonic history tied to the learner.</h1>
                    <p>
                        Accounts allow radical meanings, drafts, generated mnemonic snapshots, deck choices, and sync history to stay separate from
                        shared dictionary data.
                    </p>
                    {user ? (
                        <div className="auth-status">
                            <strong>{user.displayName || user.email || user.username}</strong>
                            <span>{user.email || user.username}</span>
                            <button className="secondary-button" disabled={isBusy} type="button" onClick={handleSignOut}>
                                Sign out
                            </button>
                        </div>
                    ) : null}
                    {message ? <p className="auth-message">{message}</p> : null}
                </div>
                <div className="auth-panels">
                    <form className="panel form-panel" onSubmit={handleSignIn}>
                        <div className="section-heading">
                            <h2>Sign in</h2>
                        </div>
                        <label className="field">
                            <span>Email or username</span>
                            <input
                                autoComplete="username"
                                value={signInIdentifier}
                                onChange={(event) => setSignInIdentifier(event.target.value)}
                            />
                        </label>
                        <label className="field">
                            <span>Password</span>
                            <input
                                autoComplete="current-password"
                                type="password"
                                value={signInPassword}
                                onChange={(event) => setSignInPassword(event.target.value)}
                            />
                        </label>
                        <button className="primary-button" disabled={isBusy} type="submit">
                            Sign in
                        </button>
                    </form>
                    <form className="panel form-panel" onSubmit={handleRegister}>
                        <div className="section-heading">
                            <h2>Register</h2>
                        </div>
                        <label className="field">
                            <span>Display name</span>
                            <input autoComplete="name" value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
                        </label>
                        <label className="field">
                            <span>Email</span>
                            <input
                                autoComplete="email"
                                type="email"
                                value={registerEmail}
                                onChange={(event) => setRegisterEmail(event.target.value)}
                            />
                        </label>
                        <label className="field">
                            <span>Password</span>
                            <input
                                autoComplete="new-password"
                                type="password"
                                value={registerPassword}
                                onChange={(event) => setRegisterPassword(event.target.value)}
                            />
                        </label>
                        <button className="secondary-button" disabled={isBusy} type="submit">
                            Create account
                        </button>
                    </form>
                </div>
            </section>
        </main>
    );
}
