import axios from "axios";

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api",
});

client.interceptors.request.use((config) => {
  const token = localStorage.getItem("settlegraph_token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 401 means the API didn't accept our token at all (missing, expired, forged) —
// not "you can't see this" (that's 403). The session is dead, so clear it and
// send the user to log in again instead of leaving pages stuck half-loaded.
// A full navigation (not a router push) also resets AuthContext's in-memory user.
client.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem("settlegraph_token");
      localStorage.removeItem("settlegraph_user");
      if (window.location.pathname !== "/login") {
        window.location.assign("/login");
      }
    }
    return Promise.reject(error);
  }
);

export default client;
