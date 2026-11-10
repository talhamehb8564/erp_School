import type { Role, User } from "./types";

export const LOGIN_PORTALS = "/portals";

export function homeFor(user: User) {
  if (user.role === "ERP_OWNER") return "/admin/app";
  if (user.mustChangePassword) return "/app/password";
  return "/app";
}

export function isProtectedPath(pathname: string) {
  return pathname.startsWith("/app") || pathname.startsWith("/admin/app");
}

export function can(role: Role | undefined, allowed: Role[]) {
  return !!role && allowed.includes(role);
}
