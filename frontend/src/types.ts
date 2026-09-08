export type Role = "CUSTOMER" | "ORGANIZER" | "PLATFORM_ADMIN";

export interface UserResponse {
  id: string;
  email: string;
  roles: Role[];
  createdAt: string; // LocalDateTime serializes as ISO string over JSON
}

export interface LoginResponse {
  token: string;
  user: UserResponse;
}