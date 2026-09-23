import { ApiError } from "../api/client";

const STATUS_FALLBACKS: Record<number, string> = {
  400: "That request wasn't valid — check the details and try again.",
  401: "You need to log in again.",
  403: "You don't have permission to do that.",
  404: "That couldn't be found — it may have been removed.",
  409: "That conflicts with the current state — try refreshing and retrying.",
};

/**
 * Turns any thrown error into a message safe to show a user.
 * Handles two known backend gaps defensively rather than displaying
 * raw/blank text: AccessDeniedException 403s currently return an empty
 * body, and @Valid failures fall back to Spring's default (unparsed) shape.
 */
export function friendlyErrorMessage(err: unknown, fallback = "Something went wrong. Please try again."): string {
  if (err instanceof ApiError) {
    const trimmed = err.message?.trim();
    if (!trimmed) {
      return STATUS_FALLBACKS[err.status] ?? fallback;
    }
    // Spring's default validation error body is a JSON blob, not a plain
    // message — don't dump it raw into the UI.
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
      return STATUS_FALLBACKS[err.status] ?? fallback;
    }
    return trimmed;
  }
  return fallback;
}
