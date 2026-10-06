import { createContext, useContext } from "react";

// The context object and its hook live here, not in AuthContext.jsx, so that
// file exports only a component (required for React fast refresh). Kept out of
// a separate "authContext.js": on a case-insensitive filesystem that name
// collides with AuthContext.jsx when resolving "./AuthContext".
export const AuthContext = createContext(null);

export function useAuth() {
  return useContext(AuthContext);
}
