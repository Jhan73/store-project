# Create the first ADMIN account of an environment

## What this does

The backend never ships a default account. The first `ADMIN` is created by running the backend binary once with `--bootstrap-first-admin --admin-email=<email>`, which:

1. Does nothing if an active `ADMIN` already exists (safe to rerun).
2. Otherwise creates the account with no usable password, issues a single-use set-password token (default expiry 48h, `jugueria.identity.set-password.token-ttl`), and prints the set-password link to **stdout only** — never through the application logger, in any profile.
3. Exits the process (`SpringApplication.exit`), so the run does not stay up serving traffic.

## Why stdout only

Every other adapter in `notifications` refuses to expose a set-password link (`UnavailableEmailSender`, tech-spec §3 — no SMTP/SES dependency is approved yet). The bootstrap command is the one deliberate exception, scoped to this one-off run: whoever runs it reads the link directly from the command's own output, copies it somewhere safe, and the process then exits. The link is a bearer credential for 48 hours — treat it like a password.

## Prerequisites

| Requirement | How to check | Expected |
|-------------|--------------|----------|
| Environment deployed and migrated | `aws ecs describe-services --cluster jugueria-<env> --services backend --query "services[0].runningCount"` | `1` or more |
| Admin SSO session | `aws sts get-caller-identity --profile jugueria-admin` | An ARN, not an error |

## Run it (ECS one-off task)

```powershell
$env:AWS_PROFILE = "jugueria-admin"
$EnvName = "test"      # or "prod"

$TaskDef = aws ecs describe-services --cluster "jugueria-$EnvName" --services backend --query "services[0].taskDefinition" --output text
$Subnets = (aws ecs describe-services --cluster "jugueria-$EnvName" --services backend --query "services[0].networkConfiguration.awsvpcConfiguration.subnets" --output json | ConvertFrom-Json) -join ","
$Sgs     = (aws ecs describe-services --cluster "jugueria-$EnvName" --services backend --query "services[0].networkConfiguration.awsvpcConfiguration.securityGroups" --output json | ConvertFrom-Json) -join ","

aws ecs run-task `
  --cluster "jugueria-$EnvName" `
  --task-definition $TaskDef `
  --launch-type FARGATE `
  --network-configuration "awsvpcConfiguration={subnets=[$Subnets],securityGroups=[$Sgs],assignPublicIp=DISABLED}" `
  --overrides '{"containerOverrides":[{"name":"backend","command":["--bootstrap-first-admin","--admin-email=owner@example.com"]}]}'
```

Replace `owner@example.com` with the real owner's address before running. Watch the task's CloudWatch log stream (`/ecs/jugueria-<env>/backend`) for the two `System.out` lines — `First ADMIN created: ...` and `Set-password link (expires ...): ...` — then open the link before it expires. CloudWatch retains the log group like any other backend output, so remove or expire that stream's entries afterward if the link's exposure window matters for your compliance posture; this is a known trade-off of printing to stdout under ECS rather than to an interactive terminal only.

## Verify

```powershell
aws ecs describe-tasks --cluster "jugueria-$EnvName" --tasks <task-arn> --query "tasks[0].containers[0].exitCode"
```

Expected: `0`. A non-zero exit means either the admin email argument was missing (see the task's log for `Missing required --admin-email=<email> argument.`) or the account creation failed (duplicate email, etc.).

Rerunning after a successful bootstrap prints `An active ADMIN already exists; nothing to do.` and exits `0` — safe to rerun by mistake.

## Risk and expiry

- The link is a bearer credential valid for the configured `token-ttl` (48h by default) and single-use: opening it and setting a password consumes it; a second attempt with the same link fails with `auth.invalid-set-password-token`.
- Only stdout of this one-off run ever contains the link. If it leaks (shared terminal, unredacted log export), rerun the command after deactivating the compromised account, or use `PATCH /api/v1/staff/{id}/role` / `POST /api/v1/staff/{id}/deactivate` once a working ADMIN session exists.
