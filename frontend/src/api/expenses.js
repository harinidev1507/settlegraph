import client from "./client";

export async function getExpensesForGroup(groupId) {
  const res = await client.get(`/expenses/group/${groupId}`);
  return res.data;
}

export async function addExpense(data) {
  const res = await client.post("/expenses", data);
  return res.data;
}
