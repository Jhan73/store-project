// Test helpers that drive PrimeNG form controls through the DOM, the way a user does.

interface Stable {
  whenStable(): Promise<unknown>;
}

function selectRoot(host: HTMLElement, id: string): HTMLElement {
  const root = host.querySelector<HTMLElement>(`p-select #${id}`)?.closest<HTMLElement>('p-select');
  if (!root) {
    throw new Error(`No p-select with inputId "${id}"`);
  }
  return root;
}

const WAIT = { timeout: 5000 };

function optionNodes(root: HTMLElement): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>('li[role="option"]'));
}

function isOpen(root: HTMLElement): boolean {
  return root.querySelector('[role="combobox"]')?.getAttribute('aria-expanded') === 'true';
}

export async function openSelect(fixture: Stable, host: HTMLElement, id: string): Promise<void> {
  const root = selectRoot(host, id);
  if (!isOpen(root)) {
    root.click();
    await fixture.whenStable();
    await vi.waitFor(() => {
      if (!isOpen(root)) {
        throw new Error(`The select "${id}" did not open`);
      }
    }, WAIT);
  }
}

export async function closeSelect(fixture: Stable, host: HTMLElement, id: string): Promise<void> {
  const root = selectRoot(host, id);
  if (isOpen(root)) {
    root
      .querySelector<HTMLElement>('[role="combobox"]')!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await fixture.whenStable();
  }
}

/** Opens the select and clicks the option with this label. */
export async function chooseOption(
  fixture: Stable,
  host: HTMLElement,
  id: string,
  label: string,
): Promise<void> {
  await openSelect(fixture, host, id);
  const root = selectRoot(host, id);
  const find = () => optionNodes(root).find((node) => node.textContent?.trim() === label);
  try {
    await vi.waitFor(() => {
      if (!find()) {
        throw new Error('missing');
      }
    }, WAIT);
  } catch {
    throw new Error(`No option "${label}" in the select "${id}"`);
  }
  find()!.click();
  await fixture.whenStable();
}

/** The labels of the options the select offers, in order. */
export async function optionLabels(
  fixture: Stable,
  host: HTMLElement,
  id: string,
): Promise<string[]> {
  await openSelect(fixture, host, id);
  const labels = optionNodes(selectRoot(host, id)).map((node) => node.textContent?.trim() ?? '');
  await closeSelect(fixture, host, id);
  return labels;
}

/** What the closed select shows: the chosen label, or its placeholder. */
export function selectedLabel(host: HTMLElement, id: string): string {
  return host.querySelector(`p-select #${id}`)?.textContent?.trim() ?? '';
}

export function selectIsInvalid(host: HTMLElement, id: string): boolean {
  const root = selectRoot(host, id);
  const marked = root.classList.contains('p-invalid');
  const announced =
    root.querySelector('[role="combobox"]')?.getAttribute('aria-invalid') === 'true';
  if (marked !== announced) {
    throw new Error(
      `The select "${id}" is ${marked ? '' : 'not '}marked p-invalid but aria-invalid is ${announced ? '' : 'not '}"true"`,
    );
  }
  return marked;
}

export function selectIsDisabled(host: HTMLElement, id: string): boolean {
  return selectRoot(host, id).classList.contains('p-disabled');
}

/** Types into a p-inputnumber and commits it with a blur. */
export function typeNumber(host: HTMLElement, id: string, value: string): void {
  const input = host.querySelector<HTMLInputElement>(`p-inputnumber #${id}`)!;
  input.focus();
  input.value = value;
  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.dispatchEvent(new Event('blur'));
}

export function numberIsInvalid(host: HTMLElement, id: string): boolean {
  const input = host.querySelector(`p-inputnumber #${id}`)!;
  const marked = input.classList.contains('p-invalid');
  const announced = input.getAttribute('aria-invalid') === 'true';
  if (marked !== announced) {
    throw new Error(
      `The number "${id}" is ${marked ? '' : 'not '}marked p-invalid but aria-invalid is ${announced ? '' : 'not '}"true"`,
    );
  }
  return marked;
}

/** The text a p-inputnumber shows. */
export function numberText(host: HTMLElement, id: string): string {
  return host.querySelector<HTMLInputElement>(`p-inputnumber #${id}`)!.value;
}
