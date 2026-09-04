import client from "./client";

export async function searchUsers(query) {
  const res = await client.get("/users/search", { params: { q: query } });
  return res.data;
}
