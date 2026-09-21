import client from "./client";

export async function getMyNotifications() {
  const res = await client.get("/notifications");
  return res.data;
}

export async function markNotificationRead(notificationId) {
  await client.patch(`/notifications/${notificationId}/read`);
}
