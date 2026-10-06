import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import AddExpenseForm from "./AddExpenseForm";
import { addExpense } from "../api/expenses";

// The API module is mocked at the import boundary: these tests check what the
// form shows and exactly what it would send, never a real request.
vi.mock("../api/expenses", () => ({ addExpense: vi.fn() }));

const ANA = { userId: 1, name: "Ana" };
const BEN = { userId: 2, name: "Ben" };
const CAL = { userId: 3, name: "Cal" };

function renderForm(members = [ANA, BEN]) {
  const onAdded = vi.fn();
  render(<AddExpenseForm groupId="28" members={members} onAdded={onAdded} />);
  return { onAdded };
}

const type = (el, value) => fireEvent.change(el, { target: { value } });

// The main amount box is the first "Amount" field; in EXACT mode each member
// row adds another, in member order.
function fillHeader({ description = "Test expense", amount, splitType }) {
  type(screen.getByPlaceholderText("What was it for?"), description);
  type(screen.getAllByPlaceholderText("Amount")[0], amount);
  type(screen.getByLabelText("Split type"), splitType);
}

function fillSplits(splitType, values) {
  const inputs = splitType === "EXACT"
    ? screen.getAllByPlaceholderText("Amount").slice(1)
    : screen.getAllByPlaceholderText(splitType === "PERCENTAGE" ? "%" : "Shares");
  values.forEach((v, i) => type(inputs[i], v));
}

function submit() {
  fireEvent.click(screen.getByRole("button", { name: "Add expense" }));
}

beforeEach(() => {
  addExpense.mockReset();
  addExpense.mockResolvedValue({});
});

describe("AddExpenseForm — SHARES preview line", () => {
  it("shows a prompt until there is an amount, then what each person pays, updating as shares change", () => {
    renderForm();
    type(screen.getByLabelText("Split type"), "SHARES");
    expect(screen.getByText("Enter an amount and shares to see what each person pays")).toBeTruthy();

    type(screen.getAllByPlaceholderText("Amount")[0], "75");
    // Shares start at 1 each.
    expect(screen.getByText("Each pays: Ana 37.50 · Ben 37.50")).toBeTruthy();

    type(screen.getAllByPlaceholderText("Shares")[1], "2");
    expect(screen.getByText("Each pays: Ana 25.00 · Ben 50.00")).toBeTruthy();
    expect(screen.queryByText("Each pays: Ana 37.50 · Ben 37.50")).toBeNull();
  });
});

describe("AddExpenseForm — PERCENTAGE validation", () => {
  it("percentages totalling 90 are rejected with the running total, and nothing is sent", async () => {
    renderForm();
    fillHeader({ amount: "99.99", splitType: "PERCENTAGE" });
    fillSplits("PERCENTAGE", ["30", "60"]);
    submit();

    expect(await screen.findByText("Percentages must add up to 100 (currently 90)")).toBeTruthy();
    expect(addExpense).not.toHaveBeenCalled();
  });

  it("percentages totalling exactly 100 are sent as typed, with no participant list", async () => {
    const { onAdded } = renderForm();
    fillHeader({ description: "Internet", amount: "99.99", splitType: "PERCENTAGE" });
    fillSplits("PERCENTAGE", ["30", "70"]);
    submit();

    await waitFor(() => expect(onAdded).toHaveBeenCalledTimes(1));
    expect(addExpense).toHaveBeenCalledTimes(1);
    expect(addExpense).toHaveBeenCalledWith({
      groupId: 28, amount: 99.99, currency: "INR", category: "Other", description: "Internet",
      splitType: "PERCENTAGE", splits: { 1: 30, 2: 70 },
      recurring: false, recurrenceFrequency: undefined, participantIds: undefined,
    });
  });

  it("33.33 / 33.33 / 33.34 is not falsely rejected by floating-point noise", async () => {
    const { onAdded } = renderForm([ANA, BEN, CAL]);
    fillHeader({ amount: "10", splitType: "PERCENTAGE" });
    fillSplits("PERCENTAGE", ["33.33", "33.33", "33.34"]);
    submit();

    await waitFor(() => expect(onAdded).toHaveBeenCalledTimes(1));
    expect(addExpense.mock.calls[0][0].splits).toEqual({ 1: 33.33, 2: 33.33, 3: 33.34 });
  });
});

describe("AddExpenseForm — EXACT validation", () => {
  it("exact amounts that don't add up to the total are rejected with both figures, and nothing is sent", async () => {
    renderForm();
    fillHeader({ amount: "90", splitType: "EXACT" });
    fillSplits("EXACT", ["60", "20"]);
    submit();

    expect(await screen.findByText("Exact amounts (80.00) must add up to the total (90.00)")).toBeTruthy();
    expect(addExpense).not.toHaveBeenCalled();
  });

  it("a sub-cent exact amount is rejected even though the typed values sum to the total, and nothing is sent", async () => {
    // Frontend twin of the backend's exactSplit_withSubCentValues_isRejected_...:
    // numeric(12,2) would silently round 50.005 on insert.
    renderForm();
    fillHeader({ amount: "100.01", splitType: "EXACT" });
    fillSplits("EXACT", ["50.005", "50.005"]);
    submit();

    expect(await screen.findByText("Exact amount for Ana can have at most 2 decimal places")).toBeTruthy();
    expect(addExpense).not.toHaveBeenCalled();
  });

  it("exact amounts matching the total to the cent are sent as typed", async () => {
    const { onAdded } = renderForm();
    fillHeader({ description: "Electricity", amount: "90", splitType: "EXACT" });
    fillSplits("EXACT", ["60", "30"]);
    submit();

    await waitFor(() => expect(onAdded).toHaveBeenCalledTimes(1));
    expect(addExpense).toHaveBeenCalledWith({
      groupId: 28, amount: 90, currency: "INR", category: "Other", description: "Electricity",
      splitType: "EXACT", splits: { 1: 60, 2: 30 },
      recurring: false, recurrenceFrequency: undefined, participantIds: undefined,
    });
  });

  it("0.10 + 0.20 matches a total of 0.30 (compared in whole cents, not floats)", async () => {
    const { onAdded } = renderForm();
    fillHeader({ amount: "0.30", splitType: "EXACT" });
    fillSplits("EXACT", ["0.10", "0.20"]);
    submit();

    await waitFor(() => expect(onAdded).toHaveBeenCalledTimes(1));
    expect(screen.queryByText(/must add up to the total/)).toBeNull();
  });
});
