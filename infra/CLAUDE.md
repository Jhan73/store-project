# CLAUDE.md — infra

Terraform for AWS foundation resources. Repo-wide rules are in the root `CLAUDE.md`; design details in `docs/tech-spec.md` §8, §10.

This folder is empty today; the layout below is the target.

## Ownership boundary

| Owned by Terraform (`infra/`) | Owned by the deploy pipelines (`cd-test.yml`, `cd-prod.yml`) | Owned by Express Mode |
|-------------------------------|---------------------------------------------------------------|-----------------------|
| VPC, ECR, RDS, security groups, S3 + CloudFront, SES identity, SSM parameters (placeholders only), CloudWatch log groups, Route 53 / ACM, GitHub OIDC provider, IAM roles, Identity Center permission set `jugueria-dev` | ECS Express Mode services `backend` and `frontend` (`aws-actions/amazon-ecs-deploy-express-service`) | The shared ALB and its listener rules |

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
  - The RDS master password is managed by RDS in Secrets Manager (`manage_master_user_password`): Terraform, CI, and the applications never read it; only the admin uses it to bootstrap the database roles. If Terraform ever has to generate a secret itself, use an `ephemeral` resource plus write-only arguments (`*_wo`).
  - Terraform manages only non-secret parameters (DB URL, power mode).
- **IAM:** least privilege per environment. Each environment's execution role reads only its own SSM path. No long-lived access keys — including for local development, which uses the `jugueria-dev` Identity Center permission set (read/write `jugueria-dev-media` only).
- **GitHub roles** (in `shared`): every trust condition is built from `local.github_subject_prefix`, which carries the owner and repository numeric IDs because the repository issues immutable OIDC subject claims. Read it from `GET /repos/<owner>/<repo>/actions/oidc/customization/sub` — the classic `repo:<owner>/<repo>:…` form never matches and fails with a bare `Not authorized to perform sts:AssumeRoleWithWebIdentity`.

   `jugueria-ci-plan` (trust `<subject-prefix>:pull_request`, `ReadOnlyAccess` plus explicit denies on any state object outside `jugueria/*` and on `kms:Decrypt`/`secretsmanager:GetSecretValue` — `ReadOnlyAccess` includes `ssm:Get*`, and the AWS-managed `aws/ssm` key lets any account principal decrypt through SSM, so without the deny any PR could read every SecureString in the account; plans run with `-lock=false` because the role cannot write lock files) and `jugueria-infra-apply-<env>` (trust `…:environment:<env>`, `AdministratorAccess`, protected by the GitHub environment rules). `shared` is applied through the `prod` environment. `jugueria-deploy-<env>` (trust `…:environment:<env>`) builds and pushes images to ECR, drives the Express Mode APIs, passes only `jugueria-<env>-*` roles, and controls that environment's power mode — never `AdministratorAccess`.

`jugueria-power` (trust `<subject-prefix>:ref:refs/heads/develop`) starts and stops both environments for `env-control.yml` and `env-autostop.yml`: SSM on `/jugueria/<env>/power/*`, `rds:Start/StopDBInstance` on both instances, and `application-autoscaling:RegisterScalableTarget` limited to the `ecs` namespace (plus the `ecs:UpdateService` it requires, and an unconditioned `DescribeScalableTargets`, whose requests carry no condition keys) to scale services to zero and back. It is trusted on the branch rather than an environment because a scheduled workflow cannot wait for the `prod` reviewer every hour. It can never touch an image: it has no `ecs:RegisterTaskDefinition` and an explicit deny on any `ecs:UpdateService` that names a task definition. It must never get `ecs:UpdateExpressGatewayService` either: every Express update registers a new task definition, even one that only changes the scaling target, and that call accepts an image.

The account also needs the ECS **service-linked role** `AWSServiceRoleForECS`, created in `shared` (`aws_iam_service_linked_role`). AWS normally creates it on the first console-made ECS service; nothing here ever did, and the deploy role deliberately lacks `iam:CreateServiceLinkedRole`, so Express Mode fails with `Unable to assume the service linked role`. The Application Auto Scaling one is not declared: the infrastructure role's managed policy may create SLRs for `ecs.application-autoscaling.amazonaws.com` and `elasticloadbalancing.amazonaws.com`, but not for `ecs.amazonaws.com`.

Each environment also has `jugueria-<env>-ecs-infrastructure`, assumed by `ecs.amazonaws.com` with the managed policy `service-role/AmazonECSInfrastructureRoleforExpressGatewayServices`. Express Mode uses it to create the load balancer, its certificate, security groups, scaling and alarms. Those resources are ECS-owned: never import or manage them.
- **Networking (D15):** one VPC and one Express Mode ALB shared by `test` and `prod`. Isolation comes from per-environment security groups, task roles, SSM paths, RDS instances, and buckets — never assume the network separates environments. No NAT gateway. Tasks in public subnets across 2 AZs, accepting traffic only from the ALB. RDS in private subnets; `5432` only from the backend security group of the **same** environment.
- **RDS:** PostgreSQL 18, `db.t4g.micro`, single-AZ, 20 GB gp3, encrypted (KMS), `rds.force_ssl=1`, not publicly accessible. `prod`: 14-day backups + PITR + deletion protection. `test`: 1-day backups.
- **Power modes (tech-spec §8.1):** environments are `on-demand` (off by default, ECS at 0 and RDS stopped), `store-hours`, or `always-on`, stored in SSM `/jugueria/<env>/power/mode` and applied by `env-control.yml` / `env-autostop.yml`. `test` is always `on-demand`; `prod` is `on-demand` until launch, `store-hours` during the M2 pilot, `always-on` from launch. Stopping never deletes resources. An Express service is scaled to zero through the Application Auto Scaling target Express creates for it (`register-scalable-target --min-capacity 0 --max-capacity 0` on `service/jugueria-<env>/jugueria-<env>-<app>`), never by the task count, which that target would push straight back up. Express keeps reporting its own scaling target meanwhile; the next deploy reapplies it. Stopping saves the current target to `/jugueria/<env>/power/scaling` and starting restores it, so the task counts stay defined only where a deploy sets them. `env-autostop.yml` counts an environment as up while its database is available **or** any service target has capacity, so services left running beside a database stopped by hand are still swept.
- **S3 media:** `jugueria-<env>-media` (`test`, `prod`) plus `jugueria-dev-media` for local development. Fully private, readable only through CloudFront Origin Access Control; uploads only by the backend task role (or the `jugueria-dev` permission set for the dev bucket). Versioning on in `prod`.
- **Certificate:** one ACM certificate covering `jugueria.jhanantezana.com`, `*.jugueria…`, and `*.test.jugueria…` (a single wildcard level does not cover `api.test.…`). It belongs to the **CloudFront distributions** (D18), not to the ALB: Express Mode requests and owns its own certificate and exposes no input for ours. It must stay in `us-east-1`, which CloudFront requires. Replacing it (e.g. changing SANs) creates the new certificate first; point each distribution at the new ARN before the destroy step.
- **SES:** one domain identity (`jugueria.jhanantezana.com`, Easy DKIM 2048, custom MAIL FROM `mail.jugueria…`, DMARC `p=none` — tighten to `quarantine` once sending is verified) shared by `test` and `prod`. The account is still in the SES sandbox; production access is requested manually (tech-spec R5).
- **Local development access:** after `shared` is applied, configure the CLI profile once with `aws configure sso --profile jugueria-dev` choosing the `jugueria-dev` permission set. The application's recipient allowlist, not SES sandbox, keeps `test` from emailing real people.
- **ECR:** `jugueria/backend`, `jugueria/frontend` — immutable tags, scan on push, lifecycle keeps the last 30 images.
- **Logs:** CloudWatch retention 14 days (`test`), 90 days (`prod`).
- **Cost (tech-spec §8.3):** `test` + `prod` ≤ USD 130/month (ceiling). Budget alert `budget_limit_usd`: 70 before launch (estimate ~USD 51), 120 from launch (estimate ~USD 93), alerting at 80% actual / 100% forecasted. Most cost is hourly (Fargate, ALB, RDS, **public IPv4 at ~USD 3.60/month each**), so low traffic does not lower it — only power modes do. Do not count on the free tier. Justify every resource that adds recurring cost, especially anything billed while an environment is off (it raises the ~USD 30 floor).
- **Scaling** is configuration only (ECS min/max tasks, task size, RDS class) — never a code change.
