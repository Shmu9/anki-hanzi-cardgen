export interface PasswordRuleState {
    label: string;
    satisfied?: boolean;
    status?: "satisfied" | "unsatisfied" | "pending";
}

const numberOrSymbolPattern = /[^A-Za-z]/;

interface PasswordRuleListProps {
    rules: PasswordRuleState[];
    ariaLabel: string;
}

export function ValidationRuleList({ rules, ariaLabel }: PasswordRuleListProps) {
    return (
        <ul className="password-rules" aria-label={ariaLabel}>
            {rules.map((rule) => {
                const status = rule.status ?? (rule.satisfied ? "satisfied" : "unsatisfied");
                return (
                    <li className={status} key={rule.label}>
                        <span aria-hidden="true">{statusLabel(status)}</span>
                        {rule.label}
                    </li>
                );
            })}
        </ul>
    );
}

export function PasswordRuleList({ rules, ariaLabel = "Password rules" }: Partial<PasswordRuleListProps> & { rules: PasswordRuleState[] }) {
    return <ValidationRuleList rules={rules} ariaLabel={ariaLabel} />;
}

function statusLabel(status: PasswordRuleState["status"]) {
    if (status === "satisfied") {
        return "OK";
    }
    if (status === "pending") {
        return "...";
    }
    return "-";
}

export function passwordRuleStates(password: string, email: string, displayName: string): PasswordRuleState[] {
    const lowerPassword = password.toLowerCase();
    const normalizedDisplayName = displayName.trim().toLowerCase();
    return [
        {
            label: "At least 8 characters",
            satisfied: password.length >= 8,
        },
        {
            label: "Includes a number or symbol",
            satisfied: numberOrSymbolPattern.test(password),
        },
        {
            label: "Does not include your display name",
            satisfied: !normalizedDisplayName || !lowerPassword.includes(normalizedDisplayName),
        },
        {
            label: "Does not include 5-character pieces of your email",
            satisfied: !containsEmailSubstring(lowerPassword, email),
        },
    ];
}

function containsEmailSubstring(lowerPassword: string, email: string) {
    const normalizedEmail = email.trim().toLowerCase();
    if (normalizedEmail.length < 5) {
        return false;
    }
    for (let start = 0; start <= normalizedEmail.length - 5; start += 1) {
        for (let end = start + 5; end <= normalizedEmail.length; end += 1) {
            if (lowerPassword.includes(normalizedEmail.slice(start, end))) {
                return true;
            }
        }
    }
    return false;
}
