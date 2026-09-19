# CLAUDE.md — infra

Terraform for AWS foundation resources. Repo-wide rules are in the root `CLAUDE.md`; design details in `docs/tech-spec.md` §8, §10.

This folder is empty today; the layout below is the target.

## Ownership boundary

| Owned by Terraform (`infra/`) | Owned by the deploy pipelines (`cd-test.yml`, `cd-prod.yml`) | Owned by Express Mode |
|-------------------------------|---------------------------------------------------------------|-----------------------|
| VPC, ECR, RDS, security groups, S3 + CloudFront, SES identity, SSM parameters (placeholders only), Route 53 / ACM, GitHub OIDC provider, IAM roles, Identity Center permission set `jugueria-dev` | ECS Express Mode services `backend` and `frontend` (`aws-actions/amazon-ecs-deploy-express-service`) | The shared ALB and its listener rules |

Never manage ECS Express services or their ALB in Terraform — two tools owning the same resource causes drift.

## Layout

```
infra/
├── modules/          shared modules
└── envs/
    ├── shared/       VPC 10.40.0.0/16, ECR, jugueria* records in the existing jhanantezana.com zone, OIDC provider, SES domain identity, jugueria-dev-media, jugueria-dev permission set, budget
    ├── test/
    └── prod/
```

`test` and `prod` use the same modules; differences are variables only.

**State:** the pre-existing bucket `acme-tfstate-dev-463470979604-us-east-1` (versioned, encrypted, private), **shared with another project**. This repo only uses keys under `jugueria/`; its IAM roles must never be granted anything outside `jugueria/*`. Each root has a committed `backend.hcl` (partial backend configuration, S3 native locking with `use_lockfile`).

**DNS:** `jhanantezana.com` is a Route 53 zone in this same account. Terraform reads it with a data source and manages **only** its own `jugueria*` records — never import or manage the whole zone.

**Bootstrap:** the first `apply` of each root runs locally with the admin profile (`AWS_PROFILE=jugueria-admin`), because the GitHub OIDC roles do not exist yet. Until they do, CI only runs `fmt` and `validate` (`init -backend=false`).

## Commands

```bash
export AWS_PROFILE=jugueria-admin                        # after: aws sso login --profile jugueria-admin
terraform -chdir=infra/envs/<root> init -backend-config=backend.hcl
terraform fmt -check -recursive infra
terraform -chdir=infra/envs/<root> validate
terraform -chdir=infra/envs/<root> plan -out=<root>.tfplan   # shared also needs TF_VAR_budget_alert_email
terraform -chdir=infra/envs/<root> providers lock -platform=linux_amd64 -platform=windows_amd64 -platform=darwin_arm64
```

`plan` runs in CI on PRs touching `infra/**` (posted as a PR comment). `apply` runs **only** manually through the protected GitHub environment — never from a local machine against `prod`.

## Rules

- **Region** `us-east-1`. Environments: `test` and `prod` only (plus the `shared` root).
- **Secrets:** SSM `SecureString` under `/jugueria/<env>/…`. **No secret value may ever reach the Terraform state** (it lives in a shared bucket):
  - Never manage a secret parameter as a normal resource, not even a "placeholder" with `ignore_changes` — refresh reads the real value into state. Application secrets (DB `app`/`migrator` credentials, JWT keys) are created out of band (runbook).
  - Secrets Terraform must generate use an `ephemeral` resource plus write-only arguments (`password_wo`, `value_wo`, bumped via `*_wo_version`), like the RDS master password in `modules/environment`.
  - Terraform manages only non-secret parameters (DB URL, power mode).
- **IAM:** least privilege per environment. Each environment's execution role reads only its own SSM path. No long-lived access keys — including for local development, which uses the `jugueria-dev` Identity Center permission set (read/write `jugueria-dev-media` only).
- **GitHub roles** (in `shared`): `jugueria-ci-plan` (trust `repo:Jhan73/store-project:pull_request`, `ReadOnlyAccess` plus an explicit deny on any state object outside `jugueria/*`; plans run with `-lock=false` because the role cannot write lock files) and `jugueria-infra-apply-<env>` (trust `…:environment:<env>`, `AdministratorAccess`, protected by the GitHub environment rules). `shared` is applied through the `prod` environment. Deploy roles for ECS arrive with M0-06.
- **Networking (D15):** one VPC and one Express Mode ALB shared by `test` and `prod`. Isolation comes from per-environment security groups, task roles, SSM paths, RDS instances, and buckets — never assume the network separates environments. No NAT gateway. Tasks in public subnets across 2 AZs, accepting traffic only from the ALB. RDS in private subnets; `5432` only from the backend security group of the **same** environment.
- **RDS:** PostgreSQL 18, `db.t4g.micro`, single-AZ, 20 GB gp3, encrypted (KMS), `rds.force_ssl=1`, not publicly accessible. `prod`: 14-day backups + PITR + deletion protection. `test`: 1-day backups.
- **Power modes (tech-spec §8.1):** environments are `on-demand` (off by default, ECS at 0 and RDS stopped), `store-hours`, or `always-on`, stored in SSM `/jugueria/<env>/power/mode` and applied by `env-control.yml` / `env-autostop.yml`. `test` is always `on-demand`; `prod` is `on-demand` until launch, `store-hours` during the M2 pilot, `always-on` from launch. Stopping never deletes resources.
- **S3 media:** `jugueria-<env>-media` (`test`, `prod`) plus `jugueria-dev-media` for local development. Fully private, readable only through CloudFront Origin Access Control; uploads only by the backend task role (or the `jugueria-dev` permission set for the dev bucket). Versioning on in `prod`.
- **SES:** one domain identity for the account, shared by `test` and `prod`. The application's recipient allowlist, not SES sandbox, keeps `test` from emailing real people.
- **ECR:** `jugueria/backend`, `jugueria/frontend` — immutable tags, scan on push, lifecycle keeps the last 30 images.
- **Logs:** CloudWatch retention 14 days (`test`), 90 days (`prod`).
- **Cost (tech-spec §8.3):** `test` + `prod` ≤ USD 130/month (ceiling). Budget alert `budget_limit_usd`: 70 before launch (estimate ~USD 51), 120 from launch (estimate ~USD 93), alerting at 80% actual / 100% forecasted. Most cost is hourly (Fargate, ALB, RDS, **public IPv4 at ~USD 3.60/month each**), so low traffic does not lower it — only power modes do. Do not count on the free tier. Justify every resource that adds recurring cost, especially anything billed while an environment is off (it raises the ~USD 30 floor).
- **Scaling** is configuration only (ECS min/max tasks, task size, RDS class) — never a code change.
