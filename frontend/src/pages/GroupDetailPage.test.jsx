import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import GroupDetailPage from "./GroupDetailPage";
import { getBalances, getGroup, getGroupMembers } from "../api/groups";
import { getExpensesForGroup } from "../api/expenses";
import { generateSettlementPlan, getSettlements } from "../api/settlements";
import { getGroupInvites } from "../api/invites";

// Every API module the page (and its child components) imports is automocked,
// so no request can leave the test; each test sets only what it needs.
vi.mock("../api/groups");
vi.mock("../api/expenses");
vi.mock("../api/settlements");
vi.mock("../api/invites");
vi.mock("../api/users");
vi.mock("../api/analytics");
vi.mock("../api/auditLog");

const httpError = (status, error) => ({ response: { status, data: error ? { error } : {} } });

const GROUP = { id: 7, name: "Flat share" };
const MEMBERS = [
  { userId: 1, name: "Ana", username: "ana" },
  { userId: 2, name: "Ben", username: "ben" },
];
const EXPENSES = [
  { id: 61, description: "Groceries", category: "Food", paidBy: 2, amount: 100, currency: "INR", recurring: false },
];

function loadSucceeds() {
  getGroup.mockResolvedValue(GROUP);
  getBalances.mockResolvedValue({ 1: -50, 2: 50 });
  getExpensesForGroup.mockResolvedValue(EXPENSES);
  getSettlements.mockResolvedValue([]);
  getGroupMembers.mockResolvedValue(MEMBERS);
  getGroupInvites.mockResolvedValue([]);
}

function renderPage(groupId = 7) {
  render(
    <MemoryRouter initialEntries={[`/groups/${groupId}`]}>
      <Routes>
        <Route path="/groups/:groupId" element={<GroupDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.resetAllMocks();
  loadSucceeds();
});

describe("GroupDetailPage — load errors", () => {
  it("a 403 (not a member) shows the access message instead of a stuck loading state or a false 'settled up'", async () => {
    getGroup.mockRejectedValue(httpError(403, "You are not a member of this group"));

    renderPage();

    expect(await screen.findByText("You don't have access to this group.")).toBeTruthy();
    expect(screen.queryByText("Loading…")).toBeNull();
    expect(screen.queryByText("Everyone's settled up.")).toBeNull();
    expect(screen.queryByText("Flat share")).toBeNull(); // no group content at all
    expect(screen.getByText(/Back to Dashboard/)).toBeTruthy(); // a way out
  });

  it("a failed refresh keeps the data already on screen and adds an error banner", async () => {
    generateSettlementPlan.mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText("Groceries")).toBeTruthy();
    expect(getGroup).toHaveBeenCalledTimes(1);

    // Settle Up succeeds, then the reload it triggers fails.
    getGroup.mockRejectedValue(httpError(500, "Database unavailable"));
    fireEvent.click(screen.getByRole("button", { name: "Simplify & Generate Settlement Plan" }));

    expect(await screen.findByText("Database unavailable")).toBeTruthy();
    expect(getGroup).toHaveBeenCalledTimes(2); // the refresh really ran
    // Everything from the first load is still there.
    expect(screen.getByRole("heading", { name: "Flat share" })).toBeTruthy();
    expect(screen.getByText("Groceries")).toBeTruthy();
    expect(screen.getByText("owes 50")).toBeTruthy();
    expect(screen.getByText("is owed 50")).toBeTruthy();
    // ...and it wasn't swapped for the full-page error or a loading state.
    expect(screen.queryByText("Loading…")).toBeNull();
    await waitFor(() => expect(screen.getByRole("button", { name: "Simplify & Generate Settlement Plan" }).disabled).toBe(false));
  });
});
