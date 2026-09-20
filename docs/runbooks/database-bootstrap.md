# Bootstrap the database roles of an environment

After the first `terraform apply` of an environment, its RDS instance has only the master user, which the applications never use. This runbook creates the two roles the backend needs — `migrator` (DDL, used by Flyway) and `app` (DML, used by the application) — and stores their credentials in SSM, out of band, so no secret ever reaches the Terraform state.

Run it **once per environment**, after the first apply and before the first deploy. It takes about 15 minutes.

## Why it is not Terraform

The state file lives in a bucket shared with another project. A secret managed by Terraform is read back into the state on every refresh, even behind `ignore_changes`, so the credentials are created here instead. Terraform owns only `/jugueria/<env>/db/url` and `/jugueria/<env>/power/mode`.

## Prerequisites

| Requirement | Check |
|-------------|-------|
| Admin SSO session | `aws sts get-caller-identity --profile jugueria-admin` |
| Session Manager plugin | `session-manager-plugin --version` ([install](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html)) |
| Docker running | `docker version` — used to run `psql` without installing PostgreSQL locally |
| The environment is applied | `terraform -chdir=infra/envs/<env> plan` reports no changes |

All commands are PowerShell 7 and assume:

```powershell
$env:AWS_PROFILE = "jugueria-admin"
$Env = "test"          # or "prod"
```

## Quick path

1. Open a tunnel to the private RDS instance through a temporary bastion.
2. Read the RDS master password from Secrets Manager.
3. Generate the `app` and `migrator` passwords.
4. Create the roles and their default privileges.
5. Store the four credentials in SSM.
6. Destroy the bastion.

## 1. Open a tunnel

RDS lives in private subnets with no route to the internet and `publicly_accessible = false`, so there is no path from a laptop. A throwaway EC2 instance in a public subnet bridges it: Session Manager reaches the instance outbound over 443, and the instance forwards TCP 5432 to RDS.

The instance reuses the environment's existing `backend` security group, which the database already accepts. **No security group, subnet, or Terraform change is needed.** Nothing is ever exposed to the internet: the bastion opens no inbound port, has no key pair, and is terminated at the end. A `t4g.nano` alive for 15 minutes costs well under one cent.

Create the instance profile — IAM only, no cost:

```powershell
$Trust = '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}'
$Trust | Out-File -Encoding ascii trust.json

aws iam create-role --role-name jugueria-bootstrap-bastion --assume-role-policy-document file://trust.json
aws iam attach-role-policy --role-name jugueria-bootstrap-bastion --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore
aws iam create-instance-profile --instance-profile-name jugueria-bootstrap-bastion
aws iam add-role-to-instance-profile --instance-profile-name jugueria-bootstrap-bastion --role-name jugueria-bootstrap-bastion
Remove-Item trust.json
```

Launch it:

```powershell
$Subnet = aws ec2 describe-subnets --filters "Name=tag:Tier,Values=public" --query "Subnets[0].SubnetId" --output text
$Sg     = aws ec2 describe-security-groups --filters "Name=group-name,Values=jugueria-$Env-backend" --query "SecurityGroups[0].GroupId" --output text
$Ami    = aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64 --query "Parameter.Value" --output text

$Bastion = aws ec2 run-instances `
  --image-id $Ami --instance-type t4g.nano `
  --subnet-id $Subnet --security-group-ids $Sg --associate-public-ip-address `
  --iam-instance-profile Name=jugueria-bootstrap-bastion `
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=jugueria-bootstrap-bastion}]" `
  --query "Instances[0].InstanceId" --output text
```

Wait until Systems Manager sees it — this takes two to three minutes after the instance reaches `running`:

```powershell
aws ssm describe-instance-information --filters "Key=InstanceIds,Values=$Bastion" --query "InstanceInformationList[0].PingStatus" --output text
```

When it prints `Online`, open the tunnel. **Leave this terminal open** and run the remaining steps in a second one:

```powershell
$DbHost = aws rds describe-db-instances --db-instance-identifier "jugueria-$Env" --query "DBInstances[0].Endpoint.Address" --output text

aws ssm start-session --target $Bastion `
  --document-name AWS-StartPortForwardingSessionToRemoteHost `
  --parameters "host=$DbHost,portNumber=5432,localPortNumber=15432"
```

## 2. Read the master password

RDS generates and rotates it in Secrets Manager. It is used only here and never leaves this session.

```powershell
$SecretArn = aws rds describe-db-instances --db-instance-identifier "jugueria-$Env" --query "DBInstances[0].MasterUserSecret.SecretArn" --output text
$Master    = (aws secretsmanager get-secret-value --secret-id $SecretArn --query SecretString --output text | ConvertFrom-Json).password
```

## 3. Generate the role passwords

Alphanumeric on purpose: these values travel through JDBC URLs, shell variables, and container environments, where `@`, `/`, `:`, and `%` are parsing hazards. Thirty-two characters keep the entropy well above what the lost symbols cost.

```powershell
function New-Password { -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 32 | ForEach-Object { [char]$_ }) }
$AppPassword      = New-Password
$MigratorPassword = New-Password
```

## 4. Create the roles

`migrator` owns every object it creates; `app` only reads and writes rows. The default privileges are declared **without `IN SCHEMA`**, so they apply to every schema `migrator` creates later — that is what lets each module's first migration create its own schema without a second visit to this runbook.

Schema `USAGE` is not covered by default privileges, so every migration that creates a schema must also grant it. That is deliberate: it keeps the grant next to the schema it protects.

```powershell
$Sql = @"
CREATE ROLE migrator LOGIN PASSWORD '$MigratorPassword';
CREATE ROLE app      LOGIN PASSWORD '$AppPassword';

-- ALTER DEFAULT PRIVILEGES FOR ROLE requires membership in that role.
GRANT migrator TO jugueria_admin;

REVOKE ALL ON DATABASE jugueria FROM PUBLIC;
GRANT CONNECT ON DATABASE jugueria TO app, migrator;
GRANT CREATE  ON DATABASE jugueria TO migrator;

ALTER DEFAULT PRIVILEGES FOR ROLE migrator GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES    TO app;
ALTER DEFAULT PRIVILEGES FOR ROLE migrator GRANT USAGE, SELECT                  ON SEQUENCES TO app;
"@

$Sql | docker run --rm -i -e PGPASSWORD=$Master -e PGSSLMODE=require postgres:18 `
  psql -h host.docker.internal -p 15432 -U jugueria_admin -d jugueria -v ON_ERROR_STOP=1
```

`PGSSLMODE=require` is mandatory: the parameter group sets `rds.force_ssl=1`, so the server rejects plaintext connections. Use `require` rather than `verify-full` — the tunnel presents the certificate under `host.docker.internal`, which will never match the RDS hostname.

> **If the container cannot reach the tunnel.** Session Manager binds the forwarded port to `127.0.0.1`, and depending on the Docker Desktop version `host.docker.internal` may not reach a loopback-only listener. If the connection is refused, install the client locally with `winget install PostgreSQL.psqlODBC` or `scoop install postgresql`, and replace each `docker run … psql` with a direct `psql -h localhost -p 15432 …`. Do not work around it by running `psql` on the bastion: that would put the new passwords on a host you are about to throw away, and in its shell history.

Append-only tables (`audit.audit_log`, `inventory.stock_movement`, `ordering.order_status_history`, `instore.in_store_payment`, `instore.cash_movement`) revoke `UPDATE` and `DELETE` from `app` in their own migrations, not here — the tables do not exist yet.

## 5. Store the credentials in SSM

The task execution role reads `/jugueria/<env>/*` and decrypts through SSM only, so these four parameters are all the backend needs.

```powershell
aws ssm put-parameter --name "/jugueria/$Env/db/app/username"      --type String       --value "app"              --overwrite
aws ssm put-parameter --name "/jugueria/$Env/db/app/password"      --type SecureString --value $AppPassword       --overwrite
aws ssm put-parameter --name "/jugueria/$Env/db/migrator/username" --type String       --value "migrator"         --overwrite
aws ssm put-parameter --name "/jugueria/$Env/db/migrator/password" --type SecureString --value $MigratorPassword  --overwrite
```

They map to the backend environment variables one to one:

| SSM parameter | Environment variable | Used by |
|---------------|---------------------|---------|
| `/jugueria/<env>/db/url` | `DB_URL` | Both — created by Terraform |
| `/jugueria/<env>/db/app/username` | `DB_USERNAME` | Application |
| `/jugueria/<env>/db/app/password` | `DB_PASSWORD` | Application |
| `/jugueria/<env>/db/migrator/username` | `DB_MIGRATOR_USERNAME` | Flyway |
| `/jugueria/<env>/db/migrator/password` | `DB_MIGRATOR_PASSWORD` | Flyway |

## 6. Verify

Confirm each role can log in and that `app` cannot create objects:

```powershell
"SELECT current_user; CREATE SCHEMA smoke_test;" | docker run --rm -i -e PGPASSWORD=$AppPassword -e PGSSLMODE=require postgres:18 `
  psql -h host.docker.internal -p 15432 -U app -d jugueria
```

Expected: `current_user` is `app`, then `ERROR: permission denied for database jugueria`. An error here is the pass condition — if the schema is created instead, `app` has more rights than it should and the grants above did not apply.

```powershell
"SELECT current_user;" | docker run --rm -i -e PGPASSWORD=$MigratorPassword -e PGSSLMODE=require postgres:18 `
  psql -h host.docker.internal -p 15432 -U migrator -d jugueria
```

Expected: `migrator`.

## 7. Destroy the bastion

Close the tunnel terminal with `Ctrl+C`, then:

```powershell
aws ec2 terminate-instances --instance-ids $Bastion
aws iam remove-role-from-instance-profile --instance-profile-name jugueria-bootstrap-bastion --role-name jugueria-bootstrap-bastion
aws iam delete-instance-profile --instance-profile-name jugueria-bootstrap-bastion
aws iam detach-role-policy --role-name jugueria-bootstrap-bastion --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore
aws iam delete-role --role-name jugueria-bootstrap-bastion
```

Confirm nothing is left running:

```powershell
aws ec2 describe-instances --filters "Name=tag:Name,Values=jugueria-bootstrap-bastion" "Name=instance-state-name,Values=running" --query "Reservations[].Instances[].InstanceId" --output text
```

Expected: empty.

## Checklist

- [ ] Both roles log in over SSL
- [ ] `app` cannot create schemas
- [ ] Four SSM parameters exist, with the two passwords as `SecureString`
- [ ] Passwords are not in any shell history file, note, or commit
- [ ] The bastion instance is terminated and its IAM role deleted
- [ ] The PowerShell session holding the passwords is closed

## If you need to rotate later

Repeat steps 1 to 3, then `ALTER ROLE app PASSWORD '<new>'`, update SSM, and redeploy the environment so the tasks pick up the new value. Rotating `migrator` only affects the next deploy, since Flyway connects at startup.

## Next step

The environment can now be deployed. Continue with work package M0-06 (CD), which creates the ECS services that read these parameters.
