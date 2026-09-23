import { friendlyErrorMessage } from "../../utils/errors";

export function ErrorState({
  error,
  onRetry,
}: {
  error: unknown;
  onRetry?: () => void;
}) {
  return (
    <div className="state-block state-block--error">
      <h3>Couldn't load this</h3>
      <p>{friendlyErrorMessage(error)}</p>
      {onRetry && (
        <button className="secondary" onClick={onRetry} style={{ marginTop: "0.5rem" }}>
          Try again
        </button>
      )}
    </div>
  );
}
