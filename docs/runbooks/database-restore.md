# Runbook — restore the database of an environment

Target: back in service within 2 hours (NFR-07), losing at most the minutes between the last restorable moment and the incident.

Use this when the **data** is lost or corrupted (a bad migration that destroyed something, a mistaken bulk change, a lost instance or zone). It is not a rollback: if the previous image still understands the schema, use `rollback.md` and do not touch the database. It follows the same method as `prod-database-capacity-swap.md`, which is the procedure that was actually run once; read its step 2 before you start.

The restore never overwrites the damaged instance. It creates a **new** instance from the continuous backups (point in time), you move the application to it, and the old one stays untouched until you have decided what to do with it.

## What you need

| Requirement | How to check |
|-------------|--------------|
| Admin SSO session | `aws sts get-caller-identity --profile jugueria-admin` |
| The instance name | `prod` is `jugueria-prod-t3` today, `test` is `jugueria-test`; the truth is `/jugueria/<env>/power/db-instance` |
| Backups on | `aws rds describe-db-instances --db-instance-identifier <name> --query "DBInstances[0].[BackupRetentionPeriod,LatestRestorableTime]"` (`prod` keeps 14 days, `test` 1 day) |

```powershell
$env:AWS_PROFILE = "jugueria-admin"
$EnvName = "prod"                                                       # or "test"
$Source  = aws ssm get-parameter --name "/jugueria/$EnvName/power/db-instance" --query Parameter.Value --output text
$Target  = "$Source-restore"
```

## 1. Decide and stop the writes

1. Decide the restore moment: the last instant **before** the damage. Write it in UTC (`2026-10-22T14:05:00Z`). Anything later than that moment is lost, so be precise and err early by a minute or two. If you do not know, ask: the audit log (`Auditoría`) and the shift summaries show when the last good activity was.
2. Stop the damage from growing. If the store is open, switch to paper (`paper-fallback.md`) and tell the floor. Then take the application down so nothing writes to the old database while you work: Actions → **Environment control** → *Run workflow* → the environment, state *stop*. (It stops the services and the database; automated backups and point-in-time restore keep working while it is stopped.)
3. Write down the time you started. The 2-hour target is measured from the incident, not from here.

## 2. Restore into a new instance

A point-in-time restore keeps the data, the encryption and the `app` and `migrator` roles with their passwords. It does **not** keep the network settings, the parameter group (`rds.force_ssl=1`), the deletion protection or the managed master password, so give them again. Read them from the source instead of typing them:

```powershell
aws rds describe-db-instances --db-instance-identifier $Source `
  --query "DBInstances[0].[DBInstanceClass,DBSubnetGroup.DBSubnetGroupName,VpcSecurityGroups[0].VpcSecurityGroupId,DBParameterGroups[0].DBParameterGroupName,AvailabilityZone]" --output text
```

Use the values it prints (class, subnet group, security group, parameter group, zone) below, and the restore moment from step 1:

```powershell
aws rds restore-db-instance-to-point-in-time `
  --source-db-instance-identifier $Source `
  --target-db-instance-identifier $Target `
  --restore-time 2026-10-22T14:05:00Z `
  --db-instance-class <class> --db-subnet-group-name <subnet-group> `
  --vpc-security-group-ids <security-group> --db-parameter-group-name <parameter-group> `
  --availability-zone <zone> --no-publicly-accessible `
  --deletion-protection --backup-retention-period 14   # 1 for test

aws rds wait db-instance-available --db-instance-identifier $Target
```

The wait takes from about ten minutes up to well over half an hour, depending on the size and on how far back the restore point is. If AWS answers `InsufficientDBInstanceCapacity`, choose another class (the swap runbook lists `db.t3.micro`) or zone and run it again.

If the source instance is gone altogether (not just damaged), restore from the latest automated backup instead: add `--source-db-instance-automated-backups-arn` for the retained backup, or restore a manual snapshot with `restore-db-instance-from-db-snapshot` and the same network arguments. Snapshots are the only way back to a moment that point-in-time cannot reach.

## 3. Check the data before you switch

Open a tunnel to the **new** instance and look around before anything depends on it. The tunnel method is in `database-bootstrap.md` (bastion, Session Manager, `psql` from a container); use `$Target`'s endpoint:

```powershell
aws rds describe-db-instances --db-instance-identifier $Target --query "DBInstances[0].Endpoint.Address" --output text
```

Connect to the `jugueria` database as `migrator` (its password is `/jugueria/<env>/db/migrator/password` in SSM) and check:

- `SELECT max(occurred_at) FROM audit.audit_log;` is close to the restore moment and not later.
- `SELECT max(created_at) FROM instore.ticket;` and `SELECT status, count(*) FROM instore.register_shift GROUP BY status;` match what the store remembers.
- `SELECT version, success FROM flyway.flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;` ends at the migration the running image expects. If the restore point is older than a migration the current image carries, the backend will apply it at start; that is normal for expand-only migrations.
- The thing that was damaged is intact.

If something is wrong, delete the new instance and restore again from a different moment; the original is untouched.

## 4. Switch the application

1. **Point the SSM parameter at the new endpoint.** In an emergency do it by hand so the store does not wait for Terraform:

   ```powershell
   aws ssm get-parameter --name "/jugueria/$EnvName/db/url" --query Parameter.Value --output text   # the old value
   $Url = "jdbc:postgresql://<new endpoint>:5432/jugueria?sslmode=require"   # same value, only the host changes
   aws ssm put-parameter --name "/jugueria/$EnvName/db/url" --type String --value $Url --overwrite
   ```

   Also write the new instance name to `/jugueria/$EnvName/power/db-instance`, or the power workflows will start and stop the old one.
2. **Start the environment** (Actions → **Environment control** → state *start*), then **redeploy the current image** so the tasks read the new URL (the backend reads it only at start): a normal deploy, or **Rollback** with the SHA that is running.
3. Check: the backend answers ready, smoke passes, an `ADMIN` can sign in, **Caja** shows the open shifts you expect.
4. Lift paper.

## 5. Make the repository describe reality

Right after the service is back, and before anyone applies `infra/envs/<env>`: a plan would try to put `db_identifier` back to the old instance and rewrite the SSM URL. Follow `prod-database-capacity-swap.md` steps 1 and 2 with `$Target` as the new instance: apply `shared` (IAM needs the new ARN), remove the old binding from the state, import the new instance, set `db_identifier` in `infra/envs/<env>/main.tf` in a PR, and read the plan for `must be replaced` before applying. The restore does not carry RDS's managed master secret; applying `manage_master_user_password = true` creates a new one and rotates the master password (the application roles are unaffected).

## 6. Account for the lost minutes

Everything between the restore moment and the stop is gone from the system, whatever the instance says.

1. Take the paper from that window (`paper-fallback.md` section 3) and key it in. Sales taken during the lost window may have partly survived on the customer's side (receipts, card terminal slips): reconcile against those, not against memory.
2. Open shifts come back as they were at the restore moment. A shift that the system shows as open but that was closed afterwards is closed again by its cashier, or force-closed by an `ADMIN` (`stuck-register-shift.md`).
3. Tell the owner the window and what was re-keyed. Idempotency keys and queued events of the lost window are gone too, so a client that retries an old request will be treated as new: staff must read **Pagado** before repeating any payment.

## 7. Afterwards

- Keep the old instance, stopped, until the cause is understood. Delete it by the last step of the swap runbook (`--final-db-snapshot-identifier`), never before. A deleted instance takes its automated backups with it unless a final snapshot is taken.
- Write down the restore moment, the time it took by step, and what you would change in this page.

## Rehearse it

A restore nobody has tried is a guess. Rehearse once before launch and after any change of instance class or network, on `test` (one day of backups is enough, and it costs a few cents per hour). The drill uses only the new instance, so nothing running is touched.

1. Note the time. Run the commands of step 2 against `jugueria-test` with `$Target = "jugueria-test-drill"`, a restore time about 30 minutes in the past, and `--backup-retention-period 1`. Write down how long `aws rds wait db-instance-available` took.
2. Open the tunnel to the drill instance (`database-bootstrap.md`) and run the checks of step 3. They must pass with the real `test` data.
3. Optional, and the part that proves the application: set `/jugueria/test/db/url` to the drill endpoint, start `test`, redeploy, run the smoke checks and `e2e` against it, then put the URL back and redeploy again.
4. Delete the drill instance: `aws rds delete-db-instance --db-instance-identifier jugueria-test-drill --skip-final-snapshot --delete-automated-backups`.
5. Compare the total with the 2-hour target and write the figures at the end of this page (date, restore point, time to available, time to verify, who ran it). If it is more than half the target for `test`, it will not fit for `prod`: fix the slowest step first.

## What not to do

- Do not restore over the damaged instance, and do not delete it to "start clean".
- Do not apply `infra/envs/<env>` before step 5: the plan will undo the switch.
- Do not edit data on the new instance by hand to patch the lost minutes; key them in through the application so they are audited.
- Do not leave the old instance running next to the new one with the same SSM parameters: tasks that start before step 4 would write to it.
