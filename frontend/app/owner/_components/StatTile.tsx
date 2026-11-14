// Shared by Dashboard and Day close — both show the same three sales/
// discounts/expected-cash figures, and this keeps their tile markup and
// number formatting from drifting apart across the two screens.
export default function StatTile({
  label,
  value,
  hint,
  tone,
}: {
  label: string;
  value: string;
  hint?: string;
  // "error" = something is wrong (a loss). Paired with a label in the hint, never colour alone.
  tone?: "error";
}) {
  return (
    <div className="rounded-lg border border-ink/10 bg-bg p-4">
      <p className="text-xs text-ink/60">{label}</p>
      <p className={`mt-1 text-2xl font-semibold ${tone === "error" ? "text-error" : "text-primary"}`}>{value}</p>
      {hint && <p className="mt-1 text-xs text-ink/60">{hint}</p>}
    </div>
  );
}
