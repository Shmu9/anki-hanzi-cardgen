interface AuthFormMessageProps {
    message: string;
}

export function AuthFormMessage({ message }: AuthFormMessageProps) {
    if (!message) {
        return null;
    }
    return (
        <div className="auth-form-message" role="alert">
            {message}
        </div>
    );
}
