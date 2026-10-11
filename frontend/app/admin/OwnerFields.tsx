import { AdminFormState } from "./actions";

const input = "rounded border border-ink/20 bg-bg px-2 py-1";

// Owner name, phone and email inputs shared by "create shop" and "re-link".
export default function OwnerFields({ state }: { state: AdminFormState }) {
  const v = state.values;
  return (
    <>
      <label className="flex flex-col text-sm text-ink/70">
        Owner name
        <input name="ownerName" required defaultValue={v?.ownerName ?? ""} className={input} />
      </label>
      <label className="flex flex-col text-sm text-ink/70">
        Owner phone (optional)
        <input name="phone" placeholder="+233241234567" defaultValue={v?.phone ?? ""} className={input} />
      </label>
      <label className="flex flex-col text-sm text-ink/70">
        Owner email
        <input name="email" type="email" required defaultValue={v?.email ?? ""} className={input} />
      </label>
    </>
  );
}
