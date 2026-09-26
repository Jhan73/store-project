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
| Environment deployed and migrated | `aws ecs describe-services --cluster jugueria-<env> --services jugueria-<env>-backend --query "services[0].runningCount"` | `1` or more |
| Admin SSO session | `aws sts get-caller-identity --profile jugueria-admin` | An ARN, not an error |

## Run it (ECS one-off task)

Express Mode generates the task definition, so it does not necessarily name the container `backend` — read the real name from the task definition rather than assuming it, or the container override silently matches nothing and `run-task` fails.

```powershell
$env:AWS_PROFILE = "jugueria-admin"
$EnvName = "test"      # or "prod"
$Service = "jugueria-$EnvName-backend"

$TaskDef = aws ecs describe-services --cluster "jugueria-$EnvName" --services $Service --query "services[0].taskDefinition" --output text
$ContainerName = aws ecs describe-task-definition --task-definition $TaskDef --query "taskDefinition.containerDefinitions[0].name" --output text
$Subnets = (aws ecs describe-services --cluster "jugueria-$EnvName" --services $Service --query "services[0].networkConfiguration.awsvpcConfiguration.subnets" --output json | ConvertFrom-Json) -join ","
$Sgs     = (aws ecs describe-services --cluster "jugueria-$EnvName" --services $Service --query "services[0].networkConfiguration.awsvpcConfiguration.securityGroups" --output json | ConvertFrom-Json) -join ","

aws ecs run-task `
  --cluster "jugueria-$EnvName" `
  --task-definition $TaskDef `
  --launch-type FARGATE `
  --network-configuration "awsvpcConfiguration={subnets=[$Subnets],securityGroups=[$Sgs],assignPublicIp=DISABLED}" `
  --overrides "{\"containerOverrides\":[{\"name\":\"$ContainerName\",\"command\":[\"--bootstrap-first-admin\",\"--admin-email=owner@example.com\"]}]}"
```

Replace `owner@example.com` with the real owner's address before running. Watch the task's CloudWatch log group (`/ecs/jugueria-<env>-backend` — confirm with `aws ecs describe-task-definition --task-definition $TaskDef --query "taskDefinition.containerDefinitions[0].logConfiguration"`) for the two `System.out` lines — `First ADMIN created: ...` and `Set-password link (expires ...): ...` — then open the link before it expires. CloudWatch retains the log group like any other backend output, so remove or expire that stream's entries afterward if the link's exposure window matters for your compliance posture; this is a known trade-off of printing to stdout under ECS rather than to an interactive terminal only.

## Verify

```powershell
aws ecs describe-tasks --cluster "jugueria-$EnvName" --tasks <task-arn> --query "tasks[0].containers[0].exitCode"
```

Expected: `0`. A non-zero exit means either the admin email argument was missing (see the task's log for `Missing required --admin-email=<email> argument.`) or the account creation failed (duplicate email, etc.).

Rerunning after a successful bootstrap prints `An active ADMIN already exists; nothing to do.` and exits `0` — safe to rerun by mistake.

## Risk and expiry

- The link is a bearer credential valid for the configured `token-ttl` (48h by default) and single-use: opening it and setting a password consumes it; a second attempt with the same link fails with `auth.invalid-set-password-token`.
- Only stdout of this one-off run ever contains the link. If it leaks (shared terminal, unredacted log export), rerun the command after deactivating the compromised account, or use `PATCH /api/v1/staff/{id}/role` / `POST /api/v1/staff/{id}/deactivate` once a working ADMIN session exists.
