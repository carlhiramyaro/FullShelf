// Every word typed must appear somewhere in the name, so "kivo" finds all
// Kivo products and "kivo tom" narrows to Kivo Tomato Paste. Shared by the
// staff sales screen and the owner's product lists.
export function matchesQuery(name: string, query: string): boolean {
  const words = query.toLowerCase().split(/\s+/).filter(Boolean);
  const lower = name.toLowerCase();
  return words.every((word) => lower.includes(word));
}
