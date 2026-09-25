import { auth } from "@clerk/nextjs/server";
import { API_BASE_URL } from "@/lib/api";
import { formatCash } from "@/lib/format";
import StatTile from "./_components/StatTile";

type StockLevel = {
  productId: number;
  name: string;
  unit: "KG" | "UNIT";
  currentBalance: number;
  alertLevel: number;
  status: "NEGATIVE" | "LOW" | "OK";
};

type DashboardSummary = {
  salesToday: number;
  discountsToday: number;
  expectedCash: number;
  lowStock: StockLevel[];
  negativeStock: StockLevel[];
};

async function fetchDashboard(): Promise<DashboardSummary> {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/dashboard`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Failed to load dashboard: ${res.status}`);
  }
  return res.json();
}

function unitLabel(unit: StockLevel["unit"]) {
  return unit === "KG" ? "kg" : "units";
}

// mvp.md's Dashboard — the owner's landing screen. Reuses the same
// StockLevelStatus split as "Stock per system" (Phase F's first slice),
// since the backend derives both from the same StockLevelService call: the
// negative-stock banner is its own severity above the low-stock list, same
// priority order as that screen's "Needs attention" grouping.
export default async function DashboardPage() {
  const summary = await fetchDashboard();

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-6 text-2xl font-semibold text-ink">Dashboard</h1>

      <div className="mb-8 grid grid-cols-1 gap-3 sm:grid-cols-3">
        <StatTile label="Sales today" value={formatCash(summary.salesToday)} />
        <StatTile label="Discounts today" value={formatCash(summary.discountsToday)} />
        <StatTile label="Expected cash" value={formatCash(summary.expectedCash)} />
      </div>

      {summary.negativeStock.length > 0 && (
        <div className="mb-6 rounded-lg bg-error p-4">
          <p className="mb-2 text-sm font-semibold text-on-dark">Below zero</p>
          <ul className="flex flex-col gap-1">
            {summary.negativeStock.map((level) => (
              <li key={level.productId} className="text-sm text-on-dark">
                {level.name}: {level.currentBalance} {unitLabel(level.unit)}
              </li>
            ))}
          </ul>
        </div>
      )}

      <div>
        <h2 className="mb-3 text-sm font-medium text-ink/70">Low stock</h2>
        {summary.lowStock.length === 0 && (
          <p className="text-sm text-ink/50">Nothing below its alert level.</p>
        )}
        <ul className="flex flex-col gap-2">
          {summary.lowStock.map((level) => (
            <li
              key={level.productId}
              className="flex items-center justify-between rounded-lg border border-ink/10 p-3"
            >
              <span>
                <span className="font-medium text-ink">{level.name}</span>{" "}
                <span className="text-xs text-ink/50">
                  alert at {level.alertLevel} {unitLabel(level.unit)}
                </span>
              </span>
              <span className="flex items-center gap-3">
                <span className="text-sm text-ink/70">
                  {level.currentBalance} {unitLabel(level.unit)}
                </span>
                <span className="rounded bg-alert px-2 py-1 text-xs font-medium text-on-dark">Low stock</span>
              </span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
