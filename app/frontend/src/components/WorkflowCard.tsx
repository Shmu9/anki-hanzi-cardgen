interface WorkflowCardProps {
    title: string;
    body: string;
    onClick: () => void;
}

export function WorkflowCard({ title, body, onClick }: WorkflowCardProps) {
    return (
        <button className="workflow-card" type="button" onClick={onClick}>
            <strong>{title}</strong>
            <span>{body}</span>
        </button>
    );
}
