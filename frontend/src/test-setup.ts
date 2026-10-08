// jsdom lacks matchMedia and scrollIntoView; PrimeNG overlays (p-select) use both when they open.
window.matchMedia ??= ((query: string) => ({
  matches: false,
  media: query,
  onchange: null,
  addEventListener: () => undefined,
  removeEventListener: () => undefined,
  addListener: () => undefined,
  removeListener: () => undefined,
  dispatchEvent: () => false,
})) as typeof window.matchMedia;

Element.prototype.scrollIntoView ??= () => undefined;

// p-fileupload previews every picked image; Node's own createObjectURL rejects jsdom files.
URL.createObjectURL = () => 'blob:test';
URL.revokeObjectURL = () => undefined;
