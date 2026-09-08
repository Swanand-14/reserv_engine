import { apiFetch } from "./client";
import type { UserResponse } from "../types";

export function signup(email: string, password: string): Promise<UserResponse> {
  return apiFetch<UserResponse>("/auth/signup", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export function login(email: string, password: string): Promise<UserResponse> {
  return apiFetch<UserResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export function logout(): Promise<void> {
  return apiFetch<void>("/auth/logout", { method: "POST" });
}
export function getCurrentUser(): Promise<UserResponse> {
  return apiFetch<UserResponse>("/users/me"); // confirm exact path once you paste the controller
}