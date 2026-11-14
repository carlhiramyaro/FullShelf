// Mirrors the backend's ExpenseCategory / ExpensePaidFrom (fixed lists for v1).
export const CATEGORIES = [
  { value: "RENT", label: "Rent" },
  { value: "SALARIES", label: "Salaries" },
  { value: "TRANSPORT", label: "Transport" },
  { value: "ELECTRICITY_WATER", label: "Electricity & water" },
  { value: "PACKAGING", label: "Packaging" },
  { value: "SUPPLIES", label: "Supplies" },
  { value: "OTHER", label: "Other" },
];

export const PAID_FROM = [
  { value: "CASH", label: "Cash" },
  { value: "MOMO", label: "MoMo" },
  { value: "OTHER", label: "Other (bank, own pocket, credit)" },
];

export function paidFromLabel(value: string) {
  return value === "CASH" ? "Cash" : value === "MOMO" ? "MoMo" : "Other";
}
