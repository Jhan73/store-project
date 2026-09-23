# Bootstrap the database roles of an environment

## What this does

A freshly applied environment has an RDS instance with exactly one user: the master, which RDS generates and rotates in Secrets Manager. The applications never use it. This runbook creates the two roles they do use and publishes their credentials where ECS can inject them.

You end up with:

| Created | Where | Used by |
|---------|-------|---------|
| Role `migrator` | PostgreSQL | Flyway, at backend startup — owns all DDL |
| Role `app` | PostgreSQL | The application — DML only, cannot create or drop anything |
| Default privileges | PostgreSQL | Grant `app` its DML rights on every table `migrator` creates later |
| Four parameters under `/jugueria/<env>/db/` | SSM Parameter Store | The ECS task execution role, which injects them as environment variables |

Run it **once per environment**, after the first `terraform apply` and before the first deploy. Both environments take about 30 minutes together.

Until it runs, the environment cannot be deployed: the backend starts, Flyway tries to connect as `migrator`, and the task dies on startup.

## Why the two roles are separate

`app` has no `UPDATE` or `DELETE` on the append-only tables of the design — `audit.audit_log`, `inventory.stock_movement`, `ordering.order_status_history`, `instore.in_store_payment`, `instore.cash_movement`. Those revocations live in the migrations that create each table, not here, because the tables do not exist yet.

That split is what makes the audit trail a guarantee rather than a convention. An application running with DDL rights can rewrite its own history, and no code review catches the day it does.

## Why it is not Terraform

The state file lives in an S3 bucket shared with another project. A secret managed by Terraform is read back into the state on every refresh, even behind `ignore_changes`. So Terraform owns only the two non-secret parameters — `/jugueria/<env>/db/url` and `/jugueria/<env>/power/mode` — and the credentials are created here, out of band.

## Before you start

### Prerequisites

| Requirement | How to check | Expected |
|-------------|--------------|----------|
| Admin SSO session | `aws sts get-caller-identity --profile jugueria-admin` | An ARN, not an error |
| Session Manager plugin | `session-manager-plugin --version` | A version number ([install](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html)) |
| Docker running | `docker version` | A server version — `psql` runs in a container, nothing is installed |
| Environment applied | `terraform -chdir=infra/envs/<env> plan` | `No changes` |

The SSO session lasts one hour. If any command fails with `ExpiredToken`, run `aws sso login --profile jugueria-admin` and continue from where you were — nothing needs to be redone.

> If `~/.aws/credentials` still contains a stale `[jugueria-admin]` section, the CLI prefers it over the SSO profile and every call fails with `ExpiredToken`. Either delete that section, or point the CLI at an empty file for this session: `$env:AWS_SHARED_CREDENTIALS_FILE = "$env:TEMP\empty-aws-credentials"`.

### Two terminals, and which is which

This is the part that goes wrong. The tunnel is a foreground process that never returns, so it needs a terminal of its own, and everything else needs a *single* terminal that stays alive from start to finish.

| | Purpose | Rule |
|---|---------|------|
| **Terminal A** | Holds the tunnel | Opens it and does nothing else. You will not type in it again until you close the tunnel |
| **Terminal B** | Everything else | One session, start to finish. Every block below marked `Terminal B` runs here |

**Never open a third terminal.** `$Master`, `$AppPassword`, `$MigratorPassword`, and the `New-Password` function live only in Terminal B's memory. They are never written to disk — that is deliberate. Open a new terminal and they are gone, and if you have already created the roles, their passwords are gone with them.

Set these once in **Terminal B**, and again in **Terminal A** before opening the tunnel:

```powershell
$env:AWS_PROFILE = "jugueria-admin"
$EnvName = "test"      # or "prod"
```

### Before opening any tunnel: make sure no old one survives

`Ctrl+C` does not reliably terminate `session-manager-plugin.exe`. A surviving process keeps port 15432 bound, so the next tunnel starts, prints its session id, and then silently never opens the port. The symptom is confusing: Systems Manager reports the instance `Online`, but `pg_isready` gets no response.

Run this in **Terminal B** before opening a tunnel, and again before each later one. It is safe when nothing is listening:

```powershell
Get-NetTCPConnection -LocalPort 15432 -State Listen -ErrorAction SilentlyContinue |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

Kill by PID, as above — never a blanket `taskkill` by image name.

## Quick path

1. Create the bastion — `Terminal B`
2. Open the tunnel — `Terminal A`
3. Probe the tunnel — `Terminal B`
4. Read the RDS master password — `Terminal B`
5. Generate the role passwords — `Terminal B`
6. Create the roles — `Terminal B`
7. Store the credentials in SSM — `Terminal B`
8. Verify — `Terminal B`
9. Repeat for the other environment, same bastion
10. Destroy the bastion — `Terminal B`

## 1. Create the bastion — `Terminal B`

RDS lives in private subnets with no route to the internet and `publicly_accessible = false`, so there is no path from a laptop. A throwaway EC2 instance in a public subnet bridges it: Session Manager reaches the instance outbound over 443, and the instance forwards TCP 5432 to RDS.

The instance reuses the environment's existing `backend` security group, which the database already accepts, so **no security group, subnet, or Terraform change is needed**. Nothing is exposed: the bastion opens no inbound port and has no key pair.

Create the instance profile — IAM only, free:

```powershell
$Trust = '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}'
$Trust | Out-File -Encoding ascii trust.json

aws iam create-role --role-name jugueria-bootstrap-bastion --assume-role-policy-document file://trust.json
aws iam attach-role-policy --role-name jugueria-bootstrap-bastion --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore
aws iam create-instance-profile --instance-profile-name jugueria-bootstrap-bastion
aws iam add-role-to-instance-profile --instance-profile-name jugueria-bootstrap-bastion --role-name jugueria-bootstrap-bastion
Remove-Item trust.json
```

Launch the instance:

```powershell
$Subnet = aws ec2 describe-subnets --filters "Name=tag:Tier,Values=public" --query "Subnets[0].SubnetId" --output text
$Sg     = aws ec2 describe-security-groups --filters "Name=group-name,Values=jugueria-$EnvName-backend" --query "SecurityGroups[0].GroupId" --output text
$Ami    = aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64 --query "Parameter.Value" --output text

$Bastion = aws ec2 run-instances `
  --image-id $Ami --instance-type t4g.nano `
  --subnet-id $Subnet --security-group-ids $Sg --associate-public-ip-address `
  --iam-instance-profile Name=jugueria-bootstrap-bastion `
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=jugueria-bootstrap-bastion}]" `
  --query "Instances[0].InstanceId" --output text

$Bastion
```

Write down that instance id. Terminal A needs it, and `$Bastion` does not exist there.

Wait for Systems Manager to register it — up to three minutes after the instance reaches `running`:

```powershell
aws ssm describe-instance-information --filters "Key=InstanceIds,Values=$Bastion" --query "InstanceInformationList[0].PingStatus" --output text
```

Repeat until it prints `Online`. Also collect the database endpoint, which Terminal A needs:

```powershell
aws rds describe-db-instances --db-instance-identifier "jugueria-$EnvName" --query "DBInstances[0].Endpoint.Address" --output text
```

## 2. Open the tunnel — `Terminal A`

Open a second terminal. Paste the instance id and endpoint from step 1 as literals — Terminal A has none of Terminal B's variables:

```powershell
$env:AWS_PROFILE = "jugueria-admin"

aws ssm start-session --target i-xxxxxxxxxxxxxxxxx `
  --document-name AWS-StartPortForwardingSessionToRemoteHost `
  --parameters "host=jugueria-test.xxxxxxxx.us-east-1.rds.amazonaws.com,portNumber=5432,localPortNumber=15432"
```

It must print **both** lines:

```
Port 15432 opened for sessionId ...
Waiting for connections...
```

If it stops after `Starting session with SessionId`, the port is still held by an earlier tunnel. Go back to "make sure no old one survives", then try again.

**Leave this terminal alone from here on.** Everything below is Terminal B.

## 3. Probe the tunnel — `Terminal B`

One second, and it rules out the entire class of connectivity failures before any password is involved:

```powershell
docker run --rm postgres:18 pg_isready -h host.docker.internal -p 15432
```

Expected: `accepting connections`.

Session Manager binds the forwarded port to `127.0.0.1`. Reaching it from a container works on Docker Desktop 29.6.1, but other versions may not resolve `host.docker.internal` to a loopback-only listener. If the probe fails, install the client locally (`scoop install postgresql`) and replace every `docker run … psql` below with a direct `psql -h localhost -p 15432 …`.

Do not work around it by running `psql` on the bastion. That puts the new passwords on a host you are about to destroy, and in its shell history.

## 4. Read the RDS master password — `Terminal B`

Used only in this runbook, only in this session:

```powershell
$SecretArn = aws rds describe-db-instances --db-instance-identifier "jugueria-$EnvName" --query "DBInstances[0].MasterUserSecret.SecretArn" --output text
$Master    = (aws secretsmanager get-secret-value --secret-id $SecretArn --query SecretString --output text | ConvertFrom-Json).password
```

## 5. Generate the role passwords — `Terminal B`

Alphanumeric on purpose: these values travel through JDBC URLs, shell variables, container environments, and single-quoted SQL literals, where `@`, `/`, `:`, `%`, and `'` are parsing hazards. Thirty-two characters more than cover the entropy the dropped symbols would have added.

```powershell
function New-Password { -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 32 | ForEach-Object { [char]$_ }) }
$AppPassword      = New-Password
$MigratorPassword = New-Password

"EnvName=$EnvName | MasterSet=$([bool]$Master) | AppLen=$($AppPassword.Length) | MigratorLen=$($MigratorPassword.Length)"
```

That last line is a guard against the most common mistake — being in the wrong terminal. It must print the environment you intend, `MasterSet=True`, and two lengths of `32`. Anything else means you are not where you think you are. Stop and fix it before running step 6.

## 6. Create the roles — `Terminal B`

`migrator` owns every object it creates; `app` only reads and writes rows. The default privileges are declared **without `IN SCHEMA`**, so they apply to every schema `migrator` creates later — that is what lets each module's first migration create its own schema without another visit to this runbook.

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

Expected: `CREATE ROLE` twice, `GRANT ROLE`, `REVOKE`, `GRANT` twice, `ALTER DEFAULT PRIVILEGES` twice. No errors.

`PGSSLMODE=require` is mandatory — the parameter group sets `rds.force_ssl=1` and the server rejects plaintext connections. Use `require`, not `verify-full`: the tunnel presents the certificate under `host.docker.internal`, which will never match the RDS hostname.

### If it fails with `role "migrator" already exists`

A previous run got this far and stopped before step 7, leaving roles whose passwords nobody holds — they were never written anywhere. `ON_ERROR_STOP=1` aborted before changing anything, so nothing is damaged.

Do not drop the roles. Replace the two `CREATE ROLE` lines with `ALTER ROLE` and run the block again; every other statement is idempotent:

```sql
ALTER ROLE migrator LOGIN PASSWORD '$MigratorPassword';
ALTER ROLE app      LOGIN PASSWORD '$AppPassword';
```

A `NOTICE` saying `jugueria_admin has already been granted membership in role migrator` is expected on a re-run and is not an error.

## 7. Store the credentials in SSM — `Terminal B`

The task execution role reads `/jugueria/<env>/*` and decrypts through SSM only, so these four parameters are all the backend needs:

```powershell
aws ssm put-parameter --name "/jugueria/$EnvName/db/app/username"      --type String       --value "app"              --overwrite
aws ssm put-parameter --name "/jugueria/$EnvName/db/app/password"      --type SecureString --value $AppPassword       --overwrite
aws ssm put-parameter --name "/jugueria/$EnvName/db/migrator/username" --type String       --value "migrator"         --overwrite
aws ssm put-parameter --name "/jugueria/$EnvName/db/migrator/password" --type SecureString --value $MigratorPassword  --overwrite
```

They map to the backend environment variables one to one:

| SSM parameter | Environment variable | Used by |
|---------------|---------------------|---------|
| `/jugueria/<env>/db/url` | `DB_URL` | Both — created by Terraform |
| `/jugueria/<env>/db/app/username` | `DB_USERNAME` | Application |
| `/jugueria/<env>/db/app/password` | `DB_PASSWORD` | Application |
| `/jugueria/<env>/db/migrator/username` | `DB_MIGRATOR_USERNAME` | Flyway |
| `/jugueria/<env>/db/migrator/password` | `DB_MIGRATOR_PASSWORD` | Flyway |

## 8. Verify — `Terminal B`

`app` must log in and must not be able to create anything:

```powershell
"SELECT current_user; CREATE SCHEMA smoke_test;" | docker run --rm -i -e PGPASSWORD=$AppPassword -e PGSSLMODE=require postgres:18 `
  psql -h host.docker.internal -p 15432 -U app -d jugueria
```

Expected: `current_user` is `app`, then `ERROR: permission denied for database jugueria`. **The error is the pass condition.** If the schema is created instead, `app` has more rights than it should.

`migrator` must log in:

```powershell
"SELECT current_user;" | docker run --rm -i -e PGPASSWORD=$MigratorPassword -e PGSSLMODE=require postgres:18 `
  psql -h host.docker.internal -p 15432 -U migrator -d jugueria
```

Expected: `migrator`.

The default privileges must exist. This is the check worth caring about — without these rows Flyway creates tables the application cannot read, and nothing fails until the first deploy, far from the cause:

```powershell
"SELECT defaclobjtype, defaclacl FROM pg_default_acl;" | docker run --rm -i -e PGPASSWORD=$Master -e PGSSLMODE=require postgres:18 `
  psql -h host.docker.internal -p 15432 -U jugueria_admin -d jugueria
```

Expected: two rows — `r` (tables) granting `app=arwd/migrator`, and `S` (sequences) granting `app=rU/migrator`.

And the parameters must be in SSM with the right types:

```powershell
aws ssm get-parameters-by-path --path "/jugueria/$EnvName" --recursive --query "sort_by(Parameters,&Name)[].{name:Name,type:Type}" --output table
```

Expected: six rows, with `db/app/password` and `db/migrator/password` as `SecureString`.

## 9. The second environment

Reuse the same bastion. Three things change, in this order.

**Close the tunnel and confirm the process is gone.** In Terminal A press `Ctrl+C`, then in Terminal B:

```powershell
Get-NetTCPConnection -LocalPort 15432 -State Listen -ErrorAction SilentlyContinue |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

**Move the bastion to the other environment's security group.** Each database accepts only its own environment's `backend` group:

```powershell
$EnvName = "prod"
$Sg = aws ec2 describe-security-groups --filters "Name=group-name,Values=jugueria-$EnvName-backend" --query "SecurityGroups[0].GroupId" --output text
aws ec2 modify-instance-attribute --instance-id $Bastion --groups $Sg
```

**Reopen the tunnel with the other endpoint**, then repeat steps 3 to 8 in the same Terminal B. Start at step 4: `$Master` is the *other* instance's password, and a stale value fails authentication.

If you ever doubt which database you are connected to, ask the server rather than the tunnel:

```powershell
"SELECT inet_server_addr();" | docker run --rm -i -e PGPASSWORD=$Master -e PGSSLMODE=require postgres:18 `
  psql -h host.docker.internal -p 15432 -U jugueria_admin -d jugueria
```

Compare it with the instance's private address:

```powershell
aws ec2 describe-network-interfaces --filters "Name=description,Values=RDSNetworkInterface" `
  --query "NetworkInterfaces[?contains(Groups[].GroupName|join(',',@),'jugueria-$EnvName-database')].PrivateIpAddress" --output text
```

## 10. Destroy the bastion — `Terminal B`

Close the tunnel in Terminal A with `Ctrl+C` first, then:

```powershell
aws ec2 terminate-instances --instance-ids $Bastion
aws iam remove-role-from-instance-profile --instance-profile-name jugueria-bootstrap-bastion --role-name jugueria-bootstrap-bastion
aws iam delete-instance-profile --instance-profile-name jugueria-bootstrap-bastion
aws iam detach-role-policy --role-name jugueria-bootstrap-bastion --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore
aws iam delete-role --role-name jugueria-bootstrap-bastion
```

Confirm nothing survives:

```powershell
aws ec2 describe-instances --filters "Name=tag:Name,Values=jugueria-bootstrap-bastion" "Name=instance-state-name,Values=running,pending" --query "Reservations[].Instances[].InstanceId" --output text
aws iam get-role --role-name jugueria-bootstrap-bastion
Get-NetTCPConnection -LocalPort 15432 -State Listen -ErrorAction SilentlyContinue
```

Expected: empty, `NoSuchEntity`, empty.

Then close Terminal B. The passwords die with it, which is the point — from here on they exist only in SSM.

## Checklist

Per environment:

- [ ] `app` logs in over SSL and cannot create schemas
- [ ] `migrator` logs in over SSL
- [ ] `pg_default_acl` has the `r` and `S` rows granting to `app`
- [ ] Six parameters under `/jugueria/<env>/`, the two passwords as `SecureString`

Once, at the end:

- [ ] Bastion instance terminated
- [ ] Instance profile and IAM role deleted
- [ ] No `session-manager-plugin.exe` holding port 15432
- [ ] Terminal B closed
- [ ] No password in a file, a note, a commit, or a shell history

## Cost

us-east-1 on-demand rates at the time of writing, for the whole runbook covering both environments — roughly 30 minutes of bastion uptime.

| Item | Rate | Both environments |
|------|------|-------------------|
| `t4g.nano` instance | USD 0.0042 / hour | ~USD 0.002 |
| Public IPv4 address | USD 0.005 / hour | ~USD 0.003 |
| 8 GB gp3 root volume | USD 0.08 / GB-month | ~USD 0.001 |
| IAM role and instance profile | free | — |
| Session Manager and its plugin | free | — |
| SSM Standard parameters | free | — |
| `SecureString` encryption with the `aws/ssm` managed key | USD 0.03 / 10,000 requests | negligible |
| Secrets Manager reads | USD 0.05 / 10,000 requests | negligible |
| **Total** | | **under USD 0.01** |

**Recurring cost added: none.** Every resource this runbook creates is destroyed in step 10.

Not caused by this runbook, but worth knowing while you are here: each environment's RDS managed master password is a Secrets Manager secret at USD 0.40 per month, so USD 0.80 per month for `test` and `prod` together. That is the price of never having the master password in Terraform state, a GitHub secret, or a developer's machine.

Rejected alternatives and what they would have cost, in case anyone reconsiders: a permanent bastion at roughly USD 3 per month, or Systems Manager VPC interface endpoints at roughly USD 21 per month. For a procedure that runs twice in the lifetime of the project, neither earns its keep.

## If you need to rotate later

Steps 1 to 5, then `ALTER ROLE app PASSWORD '<new>'`, update SSM, and redeploy the environment so the tasks pick up the new value. Rotating `migrator` only takes effect on the next deploy, since Flyway connects at startup.

## Next step

The environment can now be deployed. Continue with work package M0-06 (CD), which creates the ECS services that read these parameters.
