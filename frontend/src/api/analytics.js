import client from "./client";

// Both return { bucket: totalAmount }, e.g. { Food: 1200.00 } / { "2026-09": 1200.00 }.
export async function getSpendByCategory(groupId) {
  const res = await client.get(`/groups/${groupId}/analytics/by-category`);
  return res.data;
}

export async function getSpendByMonth(groupId) {
  const res = await client.get(`/groups/${groupId}/analytics/by-month`);
  return res.data;
}
