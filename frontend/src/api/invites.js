import client from "./client";

export async function getMyInvites() {
  const res = await client.get("/invites/mine");
  return res.data;
}

export async function acceptInvite(inviteId) {
  const res = await client.post(`/invites/${inviteId}/accept`);
  return res.data;
}

export async function declineInvite(inviteId) {
  const res = await client.post(`/invites/${inviteId}/decline`);
  return res.data;
}

export async function inviteToGroup(groupId, username) {
  const res = await client.post(`/groups/${groupId}/invites`, { username });
  return res.data;
}

export async function getGroupInvites(groupId) {
  const res = await client.get(`/groups/${groupId}/invites`);
  return res.data;
}
