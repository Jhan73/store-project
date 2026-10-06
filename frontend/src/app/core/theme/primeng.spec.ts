import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { Button } from 'primeng/button';
import { Checkbox } from 'primeng/checkbox';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Menu } from 'primeng/menu';
import { Message } from 'primeng/message';
import { Password } from 'primeng/password';
import { Select } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { Toast } from 'primeng/toast';
import { Tooltip } from 'primeng/tooltip';

import { AppPreset } from './app-preset';
import { primeTranslation } from './prime-translation';

@Component({
  selector: 'app-primeng-harness',
  imports: [
    ReactiveFormsModule,
    Button,
    Checkbox,
    Dialog,
    InputText,
    Menu,
    Message,
    Password,
    Select,
    TableModule,
    Tag,
    Toast,
    Tooltip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-button label="Entrar" (onClick)="clicked = clicked + 1" />
    <input pInputText [formControl]="name" />
    <p-password [formControl]="secret" [feedback]="false" />
    <p-select
      [formControl]="role"
      [options]="roles"
      optionLabel="label"
      optionValue="value"
      [filter]="true"
      filterBy="label"
    />
    <p-checkbox [formControl]="agreed" [binary]="true" />
    <p-tag value="Listo" />
    <p-message severity="error" text="Falló" />
    <p-menu [model]="items" />
    <span pTooltip="Ayuda">?</span>
    <p-table [value]="rows">
      <ng-template #body let-row>
        <tr>
          <td>{{ row.name }}</td>
        </tr>
      </ng-template>
    </p-table>
    <p-dialog header="Aviso" [visible]="true" [modal]="true"><p>Contenido</p></p-dialog>
    <p-toast />
  `,
})
class Harness {
  clicked = 0;
  readonly name = new FormControl('Ana');
  readonly secret = new FormControl('');
  readonly role = new FormControl('CASHIER');
  readonly agreed = new FormControl(false);
  readonly roles = [
    { label: 'Cajero', value: 'CASHIER' },
    { label: 'Mozo', value: 'SERVER' },
  ];
  readonly items = [{ label: 'Salir' }];
  readonly rows = [{ name: 'Fila 1' }];
}

describe('PrimeNG 21 on Angular 22', () => {
  async function render() {
    await TestBed.configureTestingModule({
      imports: [Harness],
      providers: [
        MessageService,
        providePrimeNG({
          theme: { preset: AppPreset, options: { darkModeSelector: '.app-dark' } },
          translation: primeTranslation,
        }),
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(Harness);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('renders the form and display components', async () => {
    const fixture = await render();
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('p-button button')?.textContent).toContain('Entrar');
    expect((host.querySelector('input[pInputText]') as HTMLInputElement).value).toBe('Ana');
    expect(host.querySelector('p-password input[type="password"]')).not.toBeNull();
    expect(host.querySelector('p-select')?.textContent).toContain('Cajero');
    expect(host.querySelector('p-checkbox input[type="checkbox"]')).not.toBeNull();
    expect(host.querySelector('p-tag')?.textContent).toContain('Listo');
    expect(host.querySelector('p-message')?.textContent).toContain('Falló');
    expect(host.querySelector('p-menu')?.textContent).toContain('Salir');
    expect(host.querySelector('p-table')?.textContent).toContain('Fila 1');
  }, 15_000);

  it('handles events and form bindings', async () => {
    const fixture = await render();
    const host = fixture.nativeElement as HTMLElement;

    (host.querySelector('p-button button') as HTMLButtonElement).click();
    fixture.componentInstance.agreed.setValue(true);
    fixture.detectChanges();

    expect(fixture.componentInstance.clicked).toBe(1);
    expect(host.querySelector('p-checkbox')?.getAttribute('data-p-checked')).toBe('true');
  });

  it('tells the user in Spanish when the search matches nothing', async () => {
    const fixture = await render();
    const host = fixture.nativeElement as HTMLElement;
    const root = host.querySelector<HTMLElement>('p-select')!;

    root.click();
    await fixture.whenStable();
    await vi.waitFor(() => expect(root.querySelector('input[role="searchbox"]')).not.toBeNull());
    const input = root.querySelector<HTMLInputElement>('input[role="searchbox"]')!;
    input.value = 'zzz';
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await fixture.whenStable();

    expect(root.querySelector('.p-select-empty-message')?.textContent?.trim()).toBe(
      'No hay resultados',
    );
  });

  it('renders the dialog as a modal overlay', async () => {
    await render();

    expect(document.body.querySelector('p-dialog [role="dialog"], [role="dialog"]')).not.toBeNull();
    expect(document.body.textContent).toContain('Contenido');
  });

  it('injects the preset as CSS variables backed by the app palette', async () => {
    await render();

    const styles = Array.from(document.head.querySelectorAll('style'))
      .map((style) => style.textContent ?? '')
      .join('\n');

    expect(styles).toContain('var(--palette-primary-700)');
    expect(styles).toContain('.app-dark');
  });
});
