import type { Role, User } from "./types";

export function homeFor(user: User) {
  if (user.role === "ERP_OWNER") return "/admin/app";
  if (user.mustChangePassword) return "/app/password";
  return "/app";
}

export function can(role: Role | undefined, allowed: Role[]) {
  return !!role && allowed.includes(role);
}
