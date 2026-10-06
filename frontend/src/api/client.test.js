import { describe, it, expect, vi, afterEach } from "vitest";
import { AxiosError } from "axios";
import client from "./client";

// Requests go through the real axios client and its real interceptors; only the
// transport is replaced (a per-request adapter that fails with the given
// status), so nothing reaches the network.
function failWith(status) {
  return (config) => Promise.reject(new AxiosError(
    `Request failed with status code ${status}`, "ERR_BAD_REQUEST", config, null,
    { status, data: {}, headers: {}, config },
  ));
}

// jsdom's own location.assign can't be spied on (it's a non-configurable own
// property), so the whole location is swapped for a stub that records calls.
function stubLocation(pathname) {
  const location = { pathname, assign: vi.fn() };
  vi.stubGlobal("location", location);
  return location;
}

function signIn() {
  localStorage.setItem("settlegraph_token", "a.jwt.token");
  localStorage.setItem("settlegraph_user", JSON.stringify({ userId: 7, name: "Ana" }));
  localStorage.setItem("unrelated_key", "kept");
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("axios client — 401 interceptor", () => {
  it("on a 401 clears both session keys, redirects to /login, and still rejects so the caller's catch runs", async () => {
    signIn();
    const location = stubLocation("/groups/5");

    const request = client.get("/groups/5", { adapter: failWith(401) });

    await expect(request).rejects.toMatchObject({ response: { status: 401 } });
    expect(localStorage.getItem("settlegraph_token")).toBeNull();
    expect(localStorage.getItem("settlegraph_user")).toBeNull();
    expect(localStorage.getItem("unrelated_key")).toBe("kept"); // only the session is cleared
    expect(location.assign).toHaveBeenCalledTimes(1);
    expect(location.assign).toHaveBeenCalledWith("/login");
  });

  it("on a 401 while already on /login clears the session but does not redirect again", async () => {
    signIn();
    const location = stubLocation("/login");

    await expect(client.post("/auth/login", {}, { adapter: failWith(401) }))
      .rejects.toMatchObject({ response: { status: 401 } });

    expect(localStorage.getItem("settlegraph_token")).toBeNull();
    expect(localStorage.getItem("settlegraph_user")).toBeNull();
    expect(location.assign).not.toHaveBeenCalled();
  });

  it("a 403 (signed in, but not allowed) leaves the session alone and does not redirect", async () => {
    signIn();
    const location = stubLocation("/groups/5");

    await expect(client.get("/groups/5", { adapter: failWith(403) }))
      .rejects.toMatchObject({ response: { status: 403 } });

    expect(localStorage.getItem("settlegraph_token")).toBe("a.jwt.token");
    expect(localStorage.getItem("settlegraph_user")).not.toBeNull();
    expect(location.assign).not.toHaveBeenCalled();
  });
});
