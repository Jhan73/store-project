# Runbook — move prod to the `jugueria-prod-t3` database

## Why

The original `prod` instance `jugueria-prod` (`db.t4g.micro`, us-east-1b) could not start: AWS returned `InsufficientDBInstanceCapacity` for that class in that zone. A stopped instance cannot be modified, so its class could not be changed in place. The data was restored by point in time into a new instance, `jugueria-prod-t3` (`db.t3.micro`, same zone, subnet group, parameter group and security group, deletion protection on, 14-day backups).

The repository now describes `prod` with that instance (`db_identifier` and `db_instance_class` in `infra/envs/prod/main.tf`). `test` keeps `jugueria-test` on `db.t4g.micro`.

## 1. Apply `shared` (IAM)

`jugueria-power` and `jugueria-deploy-prod` need the new instance ARN before the workflows can start or stop it. Apply the `shared` root the way you normally do (`infra/CLAUDE.md`). The old `jugueria-prod` ARN stays in the policies until you delete that instance.

## 2. Import the instance and apply `prod`

```powershell
$env:AWS_PROFILE = "jugueria-admin"
terraform -chdir=infra/envs/prod init -backend-config=backend.hcl
terraform -chdir=infra/envs/prod import module.environment.aws_db_instance.main jugueria-prod-t3
terraform -chdir=infra/envs/prod plan -out=prod.tfplan
```

Before applying, read the plan:

- `aws_db_instance.main` must show **no** `must be replaced` (`-/+`). An in-place update of tags or similar is fine. If it wants to replace, stop and do not apply.
- The old `aws_db_instance.main` of `jugueria-prod` is in the state today; the import replaces that binding, so the state forgets `jugueria-prod` without deleting it. If Terraform refuses because the address is already bound, run `terraform state rm module.environment.aws_db_instance.main` first and import again.
- The plan also creates `/jugueria/prod/power/db-instance` and updates `/jugueria/prod/db/url`.

```powershell
terraform -chdir=infra/envs/prod apply prod.tfplan
```

## 3. Database URL and the running tasks

Terraform manages `/jugueria/prod/db/url` (`aws_ssm_parameter.db_url`), so the apply points it at the new endpoint. Nothing needs a manual `put-parameter`. Confirm:

```powershell
aws ssm get-parameter --name /jugueria/prod/db/url --query Parameter.Value --output text
aws rds describe-db-instances --db-instance-identifier jugueria-prod-t3 --query "DBInstances[0].Endpoint.Address" --output text
```

The host in the URL must equal the endpoint. The backend reads the URL only at task start, so roll the backend (a `prod` deploy, or redeploy the current image) after the apply.

The application roles (`app`, `migrator`) and the data came with the point-in-time restore. Check that the backend becomes ready and that `prod` smoke checks pass.

## 4. Power control

No GitHub variable is needed. `env-control.yml`, `env-autostop.yml` and the deploy start step read the instance name from `/jugueria/prod/power/db-instance` (written by the apply in step 2) and fall back to `jugueria-prod` when it is missing. Before step 2 completes, power control for `prod` still targets the old, unusable instance.

## 5. Later: remove the old instance

AWS starts a stopped instance again after 7 days. When `jugueria-prod` can start, or you no longer need it:

```powershell
aws rds start-db-instance --db-instance-identifier jugueria-prod   # only if you want a last look; may fail with the same capacity error
aws rds modify-db-instance --db-instance-identifier jugueria-prod --no-deletion-protection --apply-immediately
aws rds delete-db-instance --db-instance-identifier jugueria-prod --final-db-snapshot-identifier jugueria-prod-final-before-swap
```

`modify-db-instance` fails while the instance is stopped, so if it cannot start, take a manual snapshot of the stopped instance (`aws rds create-db-snapshot`) and keep that as the last copy; delete the instance once it can be modified. Afterwards remove `jugueria-prod` from `database_instance_ids` in `infra/envs/shared/github-roles.tf` and apply `shared`.

## Risks

- The new instance has no final-snapshot history of its own yet; its automated backups start at the restore.
- `db.t3.micro` is burstable like `t4g`; watch `CPUCreditBalance` after traffic grows.
