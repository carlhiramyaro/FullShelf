// The shop device's pairing token, saved to localStorage once when the
// owner pairs the browser (see docs/decisions.md, Phase B) and read
// wherever a page needs to know "is this a paired shop device" — the
// front door router and the shop screen itself. One shared constant so
// the two can't drift onto different key names.
export const DEVICE_TOKEN_KEY = "fullshelf_device_token";
