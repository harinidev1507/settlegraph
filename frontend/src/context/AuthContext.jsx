import { createContext, useContext, useState } from "react";
import * as authApi from "../api/auth";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem("settlegraph_user");
    return stored ? JSON.parse(stored) : null;
  });

  function saveSession(authResponse) {
    localStorage.setItem("settlegraph_token", authResponse.token);
    const userInfo = { userId: authResponse.userId, username: authResponse.username, name: authResponse.name };
    localStorage.setItem("settlegraph_user", JSON.stringify(userInfo));
    setUser(userInfo);
  }

  async function login(identifier, password) {
    const res = await authApi.login({ identifier, password });
    saveSession(res);
  }

  async function register(username, name, email, password) {
    const res = await authApi.register({ username, name, email, password });
    saveSession(res);
  }

  function logout() {
    localStorage.removeItem("settlegraph_token");
    localStorage.removeItem("settlegraph_user");
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
