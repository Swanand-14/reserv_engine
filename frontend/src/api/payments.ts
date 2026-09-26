import { apiFetch } from "./client";

// Matches com.reserv_engine.dto.PaymentAttemptResponse exactly. There's no
// real payment gateway wired in — simulateSuccess is how the caller tells
// this mock step what outcome to record, so the frontend already knows
// which way an attempt will go before the response comes back; the
// returned status is shown to the user but isn't parsed to drive logic.
export interface PaymentAttemptResponse {
  id: string;
  holdId: string;
  status: string;
  amount: number;
  createdAt: string;
}

export function attemptPayment(
  holdId: string,
  amount: number,
  simulateSuccess: boolean
): Promise<PaymentAttemptResponse> {
  return apiFetch<PaymentAttemptResponse>("/api/v1/payment-attempts", {
    method: "POST",
    body: JSON.stringify({ holdId, idempotencyKey: crypto.randomUUID(), amount, simulateSuccess }),
  });
}