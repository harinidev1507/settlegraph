import client from "./client";

export async function generateSettlementPlan(groupId) {
  const res = await client.post(`/groups/${groupId}/settlements/generate`);
  return res.data;
}

export async function getSettlements(groupId) {
  const res = await client.get(`/groups/${groupId}/settlements`);
  return res.data;
}

export async function markSettlementPaid(groupId, settlementId) {
  const res = await client.patch(`/groups/${groupId}/settlements/${settlementId}/mark-paid`);
  return res.data;
}
