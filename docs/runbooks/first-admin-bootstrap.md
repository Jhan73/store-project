# Create the first ADMIN account of an environment

## What this does

The backend never ships a default account. The first `ADMIN` is created by running the backend binary once with `--bootstrap-first-admin --admin-email=<email>`, which:

1. Does nothing if an active `ADMIN` already exists (safe to rerun).
2. Otherwise creates the account with no usable password, issues a single-use set-password token (default expiry 48h, `jugueria.identity.set-password.token-ttl`), and prints the set-password link to **stdout only** — never through the application logger, in any profile.
3. Exits the process (`SpringApplication.exit`), so the run does not stay up serving traffic.

## Why stdout too

Email transport is real (D16, docs/runbooks/ses-setup.md): the bootstrap command reuses the same staff-creation path as `POST /api/v1/staff`, so the link also arrives at the given address through SES, same as any other staff invite. Printing it to stdout as well stays a deliberate fallback for this one-off run — the account is being created before anyone has confirmed the environment can deliver mail (SES sandbox, DNS not yet verified, allowlist misconfigured), so the operator is never locked out of the very first admin. Whoever runs it can read the link directly from the command's own output if the email does not arrive, copy it somewhere safe, and the process then exits. The link is a bearer credential for 48 hours — treat it like a password, from whichever channel it arrived.

## Prerequisites

| Requirement | How to check | Expected |
|-------------|--------------|----------|
| Environment deployed and migrated | `aws ecs describe-services --cluster jugueria-<env> --services jugueria-<env>-backend --query "services[0].runningCount"` | `1` or more |
| Admin SSO session | `aws sts get-caller-identity --profile jugueria-admin` | An ARN, not an error |

## Run it (ECS one-off task)

Express Mode keeps the task definition and network configuration on the service's deployments, not on the service itself (`services[0].taskDefinition` is `null`), and names the container `Main`. Read all three from the `PRIMARY` deployment rather than assuming them.

```powershell
$env:AWS_PROFILE = "jugueria-admin"
$EnvName = "test"      # or "prod"
$Service = "jugueria-$EnvName-backend"
$Primary = "services[0].deployments[?status=='PRIMARY'] | [0]"

$TaskDef = aws ecs describe-services --cluster "jugueria-$EnvName" --services $Service --query "$Primary.taskDefinition" --output text
$ContainerName = aws ecs describe-task-definition --task-definition $TaskDef --query "taskDefinition.containerDefinitions[0].name" --output text
$Subnets = (aws ecs describe-services --cluster "jugueria-$EnvName" --services $Service --query "$Primary.networkConfiguration.awsvpcConfiguration.subnets" --output json | ConvertFrom-Json) -join ","
$Sgs     = (aws ecs describe-services --cluster "jugueria-$EnvName" --services $Service --query "$Primary.networkConfiguration.awsvpcConfiguration.securityGroups" --output json | ConvertFrom-Json) -join ","
"$TaskDef | $ContainerName | $Subnets | $Sgs"   # none of these may be empty or "None"
```

The task needs a public IP: there is no NAT gateway, so without one it cannot pull the image, read SSM, or reach SES.

The overrides go through a file because PowerShell mangles JSON quotes passed to native commands.

```powershell
$AdminEmail = "owner@example.com"
@{ containerOverrides = @(@{ name = $ContainerName; command = @("--bootstrap-first-admin", "--admin-email=$AdminEmail") }) } |
  ConvertTo-Json -Depth 5 | Set-Content -Encoding ascii overrides.json

$TaskArn = aws ecs run-task `
  --cluster "jugueria-$EnvName" `
  --task-definition $TaskDef `
  --launch-type FARGATE `
  --network-configuration "awsvpcConfiguration={subnets=[$Subnets],securityGroups=[$Sgs],assignPublicIp=ENABLED}" `
  --overrides file://overrides.json `
  --query "tasks[0].taskArn" --output text
Remove-Item overrides.json
$TaskArn
```

Replace `owner@example.com` with the real owner's address before running. In `test` it must also be in `NOTIFICATIONS_RECIPIENT_ALLOWLIST`, and while SES is in the sandbox it must be a verified identity, or only the stdout fallback delivers the link. Watch the task's CloudWatch log group (`/ecs/jugueria-<env>-backend` — confirm with `aws ecs describe-task-definition --task-definition $TaskDef --query "taskDefinition.containerDefinitions[0].logConfiguration"`) for the two `System.out` lines — `First ADMIN created: ...` and `Set-password link (expires ...): ...` — then open the link before it expires. CloudWatch retains the log group like any other backend output, so remove or expire that stream's entries afterward if the link's exposure window matters for your compliance posture; this is a known trade-off of printing to stdout under ECS rather than to an interactive terminal only.

## Verify

```powershell
aws ecs wait tasks-stopped --cluster "jugueria-$EnvName" --tasks $TaskArn
aws ecs describe-tasks --cluster "jugueria-$EnvName" --tasks $TaskArn --query "tasks[0].containers[0].exitCode"
```

Expected: `0`. A non-zero exit means either the admin email argument was missing (see the task's log for `Missing required --admin-email=<email> argument.`) or the account creation failed (duplicate email, etc.).

Rerunning after a successful bootstrap prints `An active ADMIN already exists; nothing to do.` and exits `0` — safe to rerun by mistake.

## Set the password, sign in, and add staff

The link points to the frontend's `/set-password` page. Until the frontend has it (and a sign-in page), do these steps against the API. They are the same for every staff member, not only the first admin.

Find the backend's base URL:

```powershell
$env:AWS_PROFILE = "jugueria-admin"
$EnvName = "test"      # or "prod"
$BackendHost = aws ecs describe-express-gateway-service `
  --service-arn "arn:aws:ecs:us-east-1:$(aws sts get-caller-identity --query Account --output text):service/jugueria-$EnvName/jugueria-$EnvName-backend" `
  --query "service.activeConfigurations[0].ingressPaths[0].endpoint" --output text
$Api = "https://$BackendHost/api/v1"
```

### 1. Set the password

Take the `token` from the link in the email. If the email did not arrive, read it from this run's log instead:

```powershell
$TaskId = $TaskArn.Split("/")[-1]
$Line = aws logs get-log-events --log-group-name "/ecs/jugueria-$EnvName-backend" `
  --log-stream-name "ecs/Main/$TaskId" --query "events[].message" --output text | Select-String "Set-password link"
$Token = [regex]::Match($Line, "token=([A-Za-z0-9_\-]+)").Groups[1].Value
```

```powershell
Invoke-RestMethod -Method Post "$Api/auth/set-password" -ContentType "application/json" `
  -Headers @{ "X-Requested-With" = "XMLHttpRequest" } `
  -Body (@{ token = $Token; newPassword = "<8-50 chars: upper, lower, digit, special>" } | ConvertTo-Json)
```

No output means success (`204`). A `401 auth.invalid-set-password-token` means the link expired, was already used, or was replaced by a newer one.

### 2. Sign in

```powershell
$Login = Invoke-RestMethod -Method Post "$Api/auth/login" -ContentType "application/json" `
  -Body (@{ email = "<admin email>"; password = "<password>" } | ConvertTo-Json)
$Auth = @{ Authorization = "Bearer $($Login.accessToken)" }
$Login.role    # ADMIN
```

The access token lasts 15 minutes; sign in again on a `401 auth.unauthenticated`. Five wrong passwords lock the account for 15 minutes (`409 auth.account-locked`).

### 3. Add a staff member

```powershell
Invoke-RestMethod -Method Post "$Api/staff" -ContentType "application/json" -Headers $Auth `
  -Body (@{ email = "<staff email>"; role = "CASHIER" } | ConvertTo-Json)
```

Staff roles: `ADMIN`, `CASHIER`, `SERVER` (`CUSTOMER` is rejected). The new member receives their own set-password link and repeats step 1. If a link expires, reissue it with `POST $Api/staff/<id>/set-password-link` (not for your own account).

In `test`, the address must be in `NOTIFICATIONS_RECIPIENT_ALLOWLIST` (then redeploy, see `ses-setup.md`), and while SES is in the sandbox it must also be a verified identity.

### Troubleshooting the email

The account is still created when the email fails; the link can be reissued.

| Backend log (`/ecs/jugueria-<env>-backend`) | Cause | Fix |
|---|---|---|
| `Failed to send the set-password email` + `not authorized to perform 'ses:SendEmail'` | Recipient not a verified identity while SES is in the sandbox, or the task role policy is missing | `aws sesv2 create-email-identity --email-identity <address>` and click the link; check `terraform apply` ran |
| `Skipped an email outside the test recipient allowlist` | Address not in `NOTIFICATIONS_RECIPIENT_ALLOWLIST` of the running deployment | Add it, then `gh workflow run cd-test.yml --ref develop -f redeploy=backend` |
| No error, no email | Spam folder, or SES delivered it | `aws sesv2 get-account --query SendQuota.SentLast24Hours` counts messages SES accepted |

## Risk and expiry

- The link is a bearer credential valid for the configured `token-ttl` (48h by default) and single-use: opening it and setting a password consumes it; a second attempt with the same link fails with `auth.invalid-set-password-token`.
- Only the email and this one-off run's stdout contain the link. If it leaks (shared terminal, unredacted log export), rerun the command after deactivating the compromised account, or use `PATCH /api/v1/staff/{id}/role` / `POST /api/v1/staff/{id}/deactivate` once a working ADMIN session exists.
