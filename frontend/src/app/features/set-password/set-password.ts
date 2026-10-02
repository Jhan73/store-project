import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  Injector,
  signal,
} from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { IconEye, IconEyeOff, TablerIconComponent } from '@tabler/icons-angular';
import { Button, ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from '../../core/auth/auth-api';
import { ApiError } from '../../core/errors/api-error';
import { ERROR_MESSAGES, errorMessage, supportCodeMessage } from '../../core/errors/error-messages';
import { applyFieldErrors } from '../../core/errors/field-errors';
import { passwordPolicy, PasswordRule } from './password-policy';

// The backend issues URL-safe Base64 tokens; anything else cannot be a valid link.
const TOKEN_FORMAT = /^[A-Za-z0-9_-]{1,256}$/;

interface Failure {
  readonly message: string;
  readonly supportCode: string | null;
}

type View = 'form' | 'invalid-link' | 'done';

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const password = group.get('newPassword')?.value as string;
  const confirmation = group.get('confirmPassword')?.value as string;
  return password && confirmation && password !== confirmation ? { passwordMismatch: true } : null;
}

@Component({
  selector: 'app-set-password',
  imports: [
    ReactiveFormsModule,
    Button,
    ButtonDirective,
    InputText,
    Message,
    RouterLink,
    TablerIconComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../login/login.scss', './set-password.scss'],
  template: `
    <main class="login">
      @switch (view()) {
        @case ('form') {
          <form class="card" [formGroup]="form" (ngSubmit)="submit()" novalidate>
            <h1 i18n="@@setPassword.title">Crea tu contraseña</h1>
            <p id="new-password-hint" class="hint" i18n="@@setPassword.hint">
              Entre 8 y 50 caracteres, con una mayúscula, una minúscula, un número y un carácter
              especial.
            </p>

            <div class="field">
              <label for="new-password" i18n="@@setPassword.password.label">Contraseña nueva</label>
              <div class="password-row">
                <input
                  pInputText
                  id="new-password"
                  formControlName="newPassword"
                  autocomplete="new-password"
                  [type]="visible() ? 'text' : 'password'"
                  [attr.aria-invalid]="invalid(form.controls.newPassword) ? 'true' : null"
                  [attr.aria-describedby]="
                    invalid(form.controls.newPassword)
                      ? 'new-password-hint new-password-errors'
                      : 'new-password-hint'
                  "
                  fluid
                />
                <button
                  pButton
                  type="button"
                  severity="secondary"
                  [text]="true"
                  [attr.aria-pressed]="visible()"
                  aria-label="Mostrar contraseñas"
                  i18n-aria-label="@@setPassword.toggle.label"
                  (click)="visible.set(!visible())"
                >
                  <tabler-icon [icon]="visible() ? icons.hide : icons.show" aria-hidden="true" />
                </button>
              </div>
              @if (invalid(form.controls.newPassword)) {
                <ul id="new-password-errors" class="errors">
                  @if (form.controls.newPassword.hasError('required')) {
                    <li i18n="@@setPassword.password.required">Escribe una contraseña.</li>
                  }
                  @for (rule of brokenRules(); track rule) {
                    <li>
                      @switch (rule) {
                        @case ('length') {
                          <ng-container i18n="@@setPassword.rule.length"
                            >Debe tener entre 8 y 50 caracteres.</ng-container
                          >
                        }
                        @case ('lowercase') {
                          <ng-container i18n="@@setPassword.rule.lowercase"
                            >Incluye al menos una letra minúscula.</ng-container
                          >
                        }
                        @case ('uppercase') {
                          <ng-container i18n="@@setPassword.rule.uppercase"
                            >Incluye al menos una letra mayúscula.</ng-container
                          >
                        }
                        @case ('digit') {
                          <ng-container i18n="@@setPassword.rule.digit"
                            >Incluye al menos un número.</ng-container
                          >
                        }
                        @case ('special') {
                          <ng-container i18n="@@setPassword.rule.special"
                            >Incluye al menos un carácter especial, como ! ? # $ o %.</ng-container
                          >
                        }
                      }
                    </li>
                  }
                  @if (form.controls.newPassword.hasError('server')) {
                    <li i18n="@@setPassword.password.rejected">
                      La contraseña no cumple los requisitos de seguridad.
                    </li>
                  }
                </ul>
              }
            </div>

            <div class="field">
              <label for="confirm-password" i18n="@@setPassword.confirm.label"
                >Repite la contraseña</label
              >
              <input
                pInputText
                id="confirm-password"
                formControlName="confirmPassword"
                autocomplete="new-password"
                [type]="visible() ? 'text' : 'password'"
                [attr.aria-invalid]="invalidConfirmation() ? 'true' : null"
                [attr.aria-describedby]="invalidConfirmation() ? 'confirm-password-errors' : null"
                fluid
              />
              @if (invalidConfirmation()) {
                <ul id="confirm-password-errors" class="errors">
                  @if (form.controls.confirmPassword.hasError('required')) {
                    <li i18n="@@setPassword.confirm.required">Repite la contraseña.</li>
                  } @else {
                    <li i18n="@@setPassword.confirm.mismatch">Las contraseñas no coinciden.</li>
                  }
                </ul>
              }
            </div>

            <div role="alert">
              @if (failure(); as failure) {
                <p-message severity="error">
                  {{ failure.message }}
                  @if (failure.supportCode; as code) {
                    <small class="support">{{ code }}</small>
                  }
                </p-message>
              }
            </div>

            <p-button
              type="submit"
              label="Guardar contraseña"
              i18n-label="@@setPassword.submit"
              [loading]="submitting()"
              fluid
            />
          </form>
        }
        @case ('invalid-link') {
          <section class="card">
            <h1 tabindex="-1" i18n="@@setPassword.invalid.title">Enlace no válido</h1>
            <p>{{ invalidLinkMessage }}</p>
            <p i18n="@@setPassword.invalid.help">
              Pide a un administrador que te envíe un enlace nuevo. Los enlaces vencen a las 48
              horas y solo sirven una vez.
            </p>
            <a pButton routerLink="/login" severity="secondary" i18n="@@setPassword.toLogin"
              >Ir a iniciar sesión</a
            >
          </section>
        }
        @case ('done') {
          <section class="card">
            <h1 tabindex="-1" i18n="@@setPassword.done.title">Contraseña creada</h1>
            <p i18n="@@setPassword.done.body">Ya puedes iniciar sesión con tu nueva contraseña.</p>
            <a pButton routerLink="/login" i18n="@@setPassword.toLogin">Ir a iniciar sesión</a>
          </section>
        }
      }
    </main>
  `,
})
export class SetPassword {
  private readonly api = inject(AuthApi);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  // Held only here: never in a signal, storage, or the URL.
  private token: string | null;

  protected readonly invalidLinkMessage = ERROR_MESSAGES['auth.invalid-set-password-token'];
  protected readonly icons = { show: IconEye, hide: IconEyeOff };
  protected readonly form = new FormGroup(
    {
      newPassword: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, passwordPolicy],
      }),
      confirmPassword: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required],
      }),
    },
    { validators: passwordsMatch },
  );
  protected readonly view = signal<View>('form');
  protected readonly visible = signal(false);
  protected readonly submitting = signal(false);
  protected readonly failure = signal<Failure | null>(null);

  constructor() {
    const route = inject(ActivatedRoute);
    const raw = route.snapshot.queryParamMap.get('token');
    this.token = raw !== null && TOKEN_FORMAT.test(raw) ? raw : null;
    if (this.token === null) {
      this.view.set('invalid-link');
    }
    if (raw !== null) {
      // Replace, don't push: the token must not stay in history, screenshots or Referer.
      void this.router.navigate([], {
        relativeTo: route,
        queryParams: { token: null },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
    }
  }

  protected invalid(control: AbstractControl): boolean {
    return control.invalid && control.touched;
  }

  protected invalidConfirmation(): boolean {
    const control = this.form.controls.confirmPassword;
    return control.touched && (control.invalid || this.form.hasError('passwordMismatch'));
  }

  protected brokenRules(): readonly PasswordRule[] {
    return (this.form.controls.newPassword.getError('passwordPolicy') as PasswordRule[]) ?? [];
  }

  protected async submit(): Promise<void> {
    if (this.submitting() || this.token === null) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.focus(this.form.controls.newPassword.invalid ? '#new-password' : '#confirm-password');
      return;
    }
    this.submitting.set(true);
    this.failure.set(null);
    try {
      await firstValueFrom(
        this.api.setPassword({
          token: this.token,
          newPassword: this.form.controls.newPassword.value,
        }),
      );
      this.finish('done');
    } catch (error) {
      this.fail(error);
    } finally {
      this.submitting.set(false);
    }
  }

  private fail(error: unknown): void {
    if (error instanceof ApiError && error.code === 'auth.invalid-set-password-token') {
      this.finish('invalid-link');
      return;
    }
    const unmatched = error instanceof ApiError ? applyFieldErrors(this.form, error) : [];
    const matched = error instanceof ApiError && unmatched.length < error.fieldErrors.length;
    if (matched) {
      this.focus('#new-password');
      return;
    }
    const correlationId =
      error instanceof ApiError && error.status >= 500 ? error.correlationId : null;
    this.failure.set({
      message: errorMessage(error),
      supportCode: correlationId ? supportCodeMessage(correlationId) : null,
    });
  }

  // The token is spent or dead, and the password must not outlive the form.
  private finish(view: 'done' | 'invalid-link'): void {
    this.token = null;
    this.form.reset();
    this.view.set(view);
    afterNextRender(() => this.focus('h1'), { injector: this.injector });
  }

  private focus(selector: string): void {
    this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus();
  }
}
