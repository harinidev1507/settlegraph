import client from "./client";

export async function getAuditLog(groupId) {
  const res = await client.get(`/groups/${groupId}/audit-log`);
  return res.data;
}
