import client from "./client";

export async function getMyGroups() {
  const res = await client.get("/groups");
  return res.data;
}

export async function createGroup(data) {
  const res = await client.post("/groups", data);
  return res.data;
}

export async function getBalances(groupId) {
  const res = await client.get(`/groups/${groupId}/balances`);
  return res.data;
}

export async function getGroup(groupId) {
  const res = await client.get(`/groups/${groupId}`);
  return res.data;
}

export async function getGroupMembers(groupId) {
  const res = await client.get(`/groups/${groupId}/members`);
  return res.data;
}
