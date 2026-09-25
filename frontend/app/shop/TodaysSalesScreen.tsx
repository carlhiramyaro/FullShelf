"use client";

import { useEffect, useState } from "react";
import { API_BASE_URL } from "@/lib/api";
import ReceiptView, { ReceiptData } from "./ReceiptView";

const STAFF_SESSION_KEY = "fullshelf_staff_session";

type SaleSummary = {
  receiptNumber: number;
  createdAt: string;
  staffName: string;
  total: number;
  voided: boolean;
};

function sessionHeaders(): HeadersInit {
  const token = sessionStorage.getItem(STAFF_SESSION_KEY);
  return token ? { "X-Staff-Session-Token": token } : {};
}

// "Staff see today's sales only" (mvp.md) — GET /api/staff/sales/today and
// GET /api/staff/sales/{receiptNumber} both enforce that server-side, so
// this screen doesn't need its own date filtering, just to render what
// comes back.
export default function TodaysSalesScreen({
  onBack,
  onSwitchUser,
}: {
  onBack: () => void;
  onSwitchUser: () => void;
}) {
  const [sales, setSales] = useState<SaleSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState<ReceiptData | null>(null);

  useEffect(() => {
    fetch(`${API_BASE_URL}/api/staff/sales/today`, { headers: sessionHeaders() }).then(async (res) => {
      if (res.status === 401) {
        onSwitchUser();
        return;
      }
      if (res.ok) setSales(await res.json());
      setLoading(false);
    });
  }, [onSwitchUser]);

  async function openReceipt(receiptNumber: number) {
    const res = await fetch(`${API_BASE_URL}/api/staff/sales/${receiptNumber}`, { headers: sessionHeaders() });
    if (res.status === 401) {
      onSwitchUser();
      return;
    }
    if (res.ok) setDetail((await res.json()) as ReceiptData);
  }

  if (detail) {
    return (
      <ReceiptView
        receipt={detail}
        footer={
          <button
            onClick={() => setDetail(null)}
            className="rounded bg-primary px-4 py-2 text-on-dark"
          >
            Back to today&apos;s sales
          </button>
        }
      />
    );
  }

  return (
    <div className="flex min-h-screen flex-col gap-4 bg-bg p-4">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-semibold text-ink">Today&apos;s sales</h1>
        <button onClick={onBack} className="text-sm text-secondary underline">
          Back to selling
        </button>
      </div>

      {loading && <p className="text-sm text-ink/50">Loading…</p>}
      {!loading && sales.length === 0 && <p className="text-sm text-ink/50">No sales yet today.</p>}

      <ul className="flex flex-col gap-2">
        {sales.map((sale) => (
          <li key={sale.receiptNumber}>
            <button
              onClick={() => openReceipt(sale.receiptNumber)}
              className="flex w-full items-center justify-between rounded-lg border border-ink/10 p-3 text-left shadow-sm hover:bg-secondary/10"
            >
              <span>
                <span className="font-medium text-ink">#{sale.receiptNumber}</span>{" "}
                <span className="text-xs text-ink/50">
                  {sale.staffName} ·{" "}
                  {new Date(sale.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                  {sale.voided && " · voided"}
                </span>
              </span>
              <span className="font-medium text-ink">GH₵{sale.total.toFixed(2)}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
