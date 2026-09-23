export function LoadingState({ label = "Loading..." }: { label?: string }) {
  return (
    <div className="state-block">
      <div className="spinner" />
      <p>{label}</p>
    </div>
  );
}
