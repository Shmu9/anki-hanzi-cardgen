import type { ReactNode } from "react";
import type { AuthUser } from "../types";

interface AuthLayoutProps {
    children: ReactNode;
    user: AuthUser | null;
}

export function AuthLayout({ children, user }: AuthLayoutProps) {
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
                        </div>
                    ) : null}
                </div>
                <div className="auth-panels">{children}</div>
            </section>
        </main>
    );
}
