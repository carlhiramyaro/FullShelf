export type ReceiptLine = {
  productName: string;
  quantity: number;
  unit: string;
  unitPrice: number;
  discount: number;
  lineTotal: number;
};

export type ReceiptData = {
  receiptNumber: number;
  total: number;
  lines: ReceiptLine[];
  createdAt?: string;
  staffName?: string;
  voided?: boolean;
};

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

// Shared by the just-confirmed receipt in SalesScreen and a looked-up one in
// TodaysSalesScreen — same data shape either way (SaleController's confirm
// response and SaleQueryService's detail response both return receiptNumber/
// staffName/lines/total with identical line fields). "voided" is a plain
// text label, not a colour, since the staff screen is cream + green only
// per CLAUDE.md — red/orange are reserved for the owner's screens.
export default function ReceiptView({
  receipt,
  footer,
}: {
  receipt: ReceiptData;
  footer: React.ReactNode;
}) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 bg-bg p-6">
      <p className="text-sm text-ink/50">Receipt #{receipt.receiptNumber}</p>
      {(receipt.staffName || receipt.createdAt) && (
        <p className="text-xs text-ink/40">
          {receipt.staffName}
          {receipt.staffName && receipt.createdAt && " · "}
          {receipt.createdAt && formatTime(receipt.createdAt)}
        </p>
      )}
      {receipt.voided && <p className="text-xs font-medium text-ink/60">Voided — record kept for reference</p>}
      <p className="text-5xl font-bold text-primary">GH₵{receipt.total.toFixed(2)}</p>
      <ul className="w-full max-w-sm text-sm text-ink/70">
        {receipt.lines.map((line, i) => (
          <li key={i} className="flex justify-between border-b border-ink/10 py-1">
            <span>
              {line.productName} × {line.quantity}
            </span>
            <span>GH₵{line.lineTotal.toFixed(2)}</span>
          </li>
        ))}
      </ul>
      {footer}
    </div>
  );
}
