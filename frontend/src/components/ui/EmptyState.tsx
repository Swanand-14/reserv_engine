export function EmptyState({
  title,
  message,
}: {
  title: string;
  message?: string;
}) {
  return (
    <div className="state-block">
      <h3>{title}</h3>
      {message && <p>{message}</p>}
    </div>
  );
}
