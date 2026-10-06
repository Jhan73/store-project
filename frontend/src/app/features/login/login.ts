import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { IconLogin, TablerIconComponent } from '@tabler/icons-angular';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Password } from 'primeng/password';
import { Tooltip } from 'primeng/tooltip';
import { passwordAriaInvalid } from '../../shared/forms/aria-invalid';
import { AuthStore } from '../../core/auth/auth-store';
import { homeRouteFor, safeReturnUrl } from '../../core/auth/navigation';
import { ApiError } from '../../core/errors/api-error';
import { errorMessage, supportCodeMessage } from '../../core/errors/error-messages';
import { applyFieldErrors } from '../../core/errors/field-errors';

interface Failure {
  readonly message: string;
  readonly supportCode: string | null;
}

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, Button, InputText, Message, Password, TablerIconComponent, Tooltip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './login.scss',
  template: `
    <main class="login">
      <form class="card" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <h1 i18n="@@login.title">Iniciar sesión</h1>

        <div class="field">
          <label for="email" i18n="@@login.email.label">Correo electrónico</label>
          <input
            pInputText
            id="email"
            type="email"
            formControlName="email"
            autocomplete="username"
            [attr.aria-invalid]="invalid(form.controls.email) ? 'true' : null"
            fluid
          />
        </div>

        <div class="field">
          <label for="password" i18n="@@login.password.label">Contraseña</label>
          <p-password
            inputId="password"
            formControlName="password"
            autocomplete="current-password"
            [feedback]="false"
            [invalid]="invalid(form.controls.password)"
            [pt]="passwordAriaInvalid(invalid(form.controls.password))"
            fluid
          />
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
          [loading]="submitting()"
          pTooltip="Iniciar sesión con tu correo y contraseña"
          i18n-pTooltip="@@login.submit.tooltip"
          tooltipPosition="top"
          fluid
        >
          <tabler-icon [icon]="icons.login" aria-hidden="true" />
          <span i18n="@@login.submit">Entrar</span>
        </p-button>
      </form>
    </main>
  `,
})
export class Login {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly form = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  protected readonly submitting = signal(false);
  protected readonly icons = { login: IconLogin };
  protected readonly passwordAriaInvalid = passwordAriaInvalid;
  protected readonly failure = signal<Failure | null>(null);

  constructor() {
    void this.leaveIfSignedIn();
  }

  // A valid refresh cookie means the visitor is already signed in; an outage is shown, but the form stays usable.
  private async leaveIfSignedIn(): Promise<void> {
    await this.auth.restore();
    const role = this.auth.role();
    if (role) {
      await this.router.navigateByUrl(homeRouteFor(role));
      return;
    }
    const outage = this.auth.refreshError();
    if (outage && !this.submitting() && !this.failure()) {
      this.failure.set({ message: errorMessage(outage), supportCode: null });
    }
  }

  protected invalid(control: AbstractControl): boolean {
    return control.invalid && control.touched;
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.failure.set(null);
    try {
      await this.auth.login(this.form.getRawValue());
      const returnUrl = safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));
      await this.router.navigateByUrl(returnUrl ?? homeRouteFor(this.auth.role() ?? 'CUSTOMER'));
    } catch (error) {
      if (error instanceof ApiError) {
        applyFieldErrors(this.form, error);
      }
      const correlationId =
        error instanceof ApiError && error.status >= 500 ? error.correlationId : null;
      this.failure.set({
        message: errorMessage(error),
        supportCode: correlationId ? supportCodeMessage(correlationId) : null,
      });
    } finally {
      this.submitting.set(false);
    }
  }
}
