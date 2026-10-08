# Enable the Playwright suite in the test pipeline

`cd-test.yml` runs the `e2e/` suite after the smoke checks and before the digests are recorded, so a red suite stops the commit from being promoted to `prod`. The job runs only when the `test` environment variable `E2E_ENABLED` is `true`. With it unset, the pipeline behaves as before.

The suite signs in as a dedicated `ADMIN` account of `test`. Its password lives in SSM Parameter Store, is read by the job through the existing `test` deploy role, and is masked before use. Never enable this for `prod`: the suite refuses production hosts, and the `prod` deploy role has no access to the parameter.

## Cost

The password is a Standard SecureString parameter under the default `aws/ssm` key: no charge for the parameter or the key. The job only adds runner minutes.

## 1. Create the account

An existing `ADMIN` invites it at `/admin/users` (or with `POST /api/v1/staff`, see "Set the password, sign in, and add staff" in `first-admin-bootstrap.md`), role `ADMIN`. The address must be in `NOTIFICATIONS_RECIPIENT_ALLOWLIST` and, while SES is in the sandbox, a verified identity (`ses-setup.md`). The new account opens the link from the email and sets its password. Use a long random password from a password manager.

## 2. Apply the permission

The `ReadE2eCredentials` statement of the `jugueria-deploy-test` role (`infra/envs/shared/github-roles.tf`) allows `ssm:GetParameter` on `/jugueria/test/e2e/*` only. Apply the `shared` root before the first run.

## 3. Store the password

The parameter is created by hand, never in Terraform, so the password is not in code or state. Read it from a prompt so it does not land in shell history:

```powershell
$env:AWS_PROFILE = "jugueria-admin"
$Secure = Read-Host "E2E admin password" -AsSecureString
$Password = [System.Net.NetworkCredential]::new("", $Secure).Password
aws ssm put-parameter --name /jugueria/test/e2e/admin-password --type SecureString --overwrite --value $Password
Remove-Variable Password, Secure
```

## 4. Set the environment variables

`E2E_BASE_URL` and `E2E_API_URL` must be the custom hosts of `test`, not the raw ECS endpoints: the frontend derives its API origin as `api.<page hostname>`, so from a raw endpoint it cannot reach its API. The custom domain of `test` must be wired first (`custom-domains.md`).

The services must be running (`test` is on-demand, see `env-control.yml`). Then:

```bash
gh variable set E2E_BASE_URL --env test --body "https://test.jugueria.jhanantezana.com"
gh variable set E2E_API_URL --env test --body "https://api.test.jugueria.jhanantezana.com"
gh variable set E2E_ADMIN_EMAIL --env test --body "<the account's email>"
gh variable set E2E_ENABLED --env test --body true
```

Set `E2E_ENABLED` last: the account from step 1 must exist and have set its password, because the first run signs in with it. The custom hosts do not change when a service is recreated, but its rule hosts must be redone (`custom-domains.md`). To switch the suite off, set `E2E_ENABLED` to `false` or delete it.

## Rotation

Change the account's password (or reissue its link), then run the `put-parameter` command of step 3 again. The next run reads the new value.

## Cleanup

The API can only deactivate products and categories, so inactive `e2e-*` rows accumulate in `test`. Each run also sweeps leftovers of earlier runs by prefix, which is why the job shares the `deploy-test` concurrency group and two suites never overlap.

## Output

The job uses the `github` and `line` reporters only and uploads no report or `test-results`: typed values, including the password, are recorded in them. The password is masked in the log, but do not add reporters, traces, videos or screenshots.
