# Runbook — move prod to the `jugueria-prod-t3` database

## Why

The original `prod` instance `jugueria-prod` (`db.t4g.micro`, us-east-1b) could not start: AWS returned `InsufficientDBInstanceCapacity` for that class in that zone. A stopped instance cannot be modified, so its class could not be changed in place. The data was restored by point in time into a new instance, `jugueria-prod-t3` (`db.t3.micro`, same zone, subnet group, parameter group and security group, deletion protection on, 14-day backups).

The repository now describes `prod` with that instance (`db_identifier` and `db_instance_class` in `infra/envs/prod/main.tf`). `test` keeps `jugueria-test` on `db.t4g.micro`.

## 1. Apply `shared` (IAM)

`jugueria-power` and `jugueria-deploy-prod` need the new instance ARN before the workflows can start or stop it. Apply the `shared` root the way you normally do (`infra/CLAUDE.md`). The old `jugueria-prod` ARN stays in the policies until you delete that instance.

## 2. Import the instance and apply `prod`

`terraform import` refuses an address that is already in the state, and the state binds `module.environment.aws_db_instance.main` to `jugueria-prod`. So the old binding must be removed first. State surgery cannot go through CI, so this step is an owner exception to the rule "never apply locally against `prod`": run it yourself with the admin profile.

```powershell
$env:AWS_PROFILE = "jugueria-admin"
terraform -chdir=infra/envs/prod init -backend-config=backend.hcl
terraform -chdir=infra/envs/prod state pull > prod-state-backup.json
terraform -chdir=infra/envs/prod state rm module.environment.aws_db_instance.main
terraform -chdir=infra/envs/prod import module.environment.aws_db_instance.main jugueria-prod-t3
terraform -chdir=infra/envs/prod plan -out=prod.tfplan
```

`state rm` only forgets `jugueria-prod`; it deletes nothing in AWS. Between `state rm` and `import`, do not plan or apply: with the instance missing from the state, a plan would try to create `jugueria-prod-t3` and fail with `DBInstanceAlreadyExists` (it would not destroy anything). Keep `prod-state-backup.json` outside the repo; it is the way back.

Read the plan before applying:

- `aws_db_instance.main` must show **no** `must be replaced` (`-/+`). If it does, stop and do not apply.
- Expected in-place changes on the imported instance: `skip_final_snapshot` (an import cannot know it), `final_snapshot_identifier` becoming `jugueria-prod-t3-final`, `apply_immediately`, `manage_master_user_password` false to true, and possibly tags. Anything else deserves a look first.
- **Master secret:** the point-in-time restore does not carry RDS's managed master secret, so the instance has none. Applying `manage_master_user_password = true` makes RDS create a new Secrets Manager secret and **rotate the master password**. The `app` and `migrator` database roles are unaffected, so the application keeps working. After the apply, read the new secret ARN and use it for any admin work from now on:

  ```powershell
  aws rds describe-db-instances --db-instance-identifier jugueria-prod-t3 --query "DBInstances[0].MasterUserSecret.SecretArn" --output text
  ```

  The module output `database_master_secret_arn` tolerates the missing secret while planning (it is null until the apply creates it).
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

- Deleting the old `jugueria-prod` also deletes its automated snapshots unless a manual or final snapshot is taken first. The new instance's own automated backups start at the restore.
- `db.t3.micro` is burstable like `t4g`; watch `CPUCreditBalance` after traffic grows.
