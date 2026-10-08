# Serve an environment on its custom domain

## What this does

The app expects its own hostnames: the frontend derives the API origin as `api.<page hostname>`, and the backend allows only the matching origin (CORS and `/ws`). The generated Express endpoints cannot satisfy that, so each environment is also served on its custom hosts. The ALB that Express Mode creates stays the front door (tech-spec D18): the project certificate is attached to its HTTPS listener, the custom hosts are added to the host-header rules Express made for each service, and Route 53 aliases point the hosts at the ALB.

| | `test` | `prod` |
|---|--------|--------|
| Frontend | `test.jugueria.jhanantezana.com` | `jugueria.jhanantezana.com` |
| API | `api.test.jugueria.jhanantezana.com` | `api.jugueria.jhanantezana.com` |
| State | wired | **not wired** |

The ALB, its listener, and its rules are shared by `test` and `prod` (D15). CD never touches the listener certificate or the rules: Express rewrites only the rule **actions** on a deploy (it flips the target group weights), and the custom host condition survives. CD only passes the custom frontend host to Angular (`NG_ALLOWED_HOSTS`) and checks the custom hosts in the smoke test. That check makes at most four attempts per URL with a 15 s request limit, so even if the ALB blackholes it ends within about 5 minutes, inside the 6 minute step limit. CD has no ELB permission, because rule permissions cannot be scoped per environment.

## Prerequisites

- The AWS CLI with an admin profile: `$env:AWS_PROFILE = "jugueria-admin"`. The commands below run on a workstation, not in CD.
- The ACM certificate of the project in `us-east-1`, issued, and covering all four hosts (check its `SubjectAlternativeNames`; a wildcard for `*.jugueria.jhanantezana.com` does not cover `api.test.jugueria.jhanantezana.com`).
- The environment is running (`env-control.yml`) and both services are deployed, so their endpoints exist.
- `gh` authenticated with rights to set environment variables.

Windows Git Bash rewrites any argument that starts with `/` into a Windows path. Set `MSYS_NO_PATHCONV=1` before a command whose argument starts with `/` (for example `--name /jugueria/...`). PowerShell is unaffected.

## 1. Attach the certificate to the listener

Done once for the shared ALB; `prod` reuses it.

```powershell
aws elbv2 describe-load-balancers --query "LoadBalancers[].[LoadBalancerName,LoadBalancerArn,DNSName,VpcId]" --output table
$Alb = "<ARN of the ALB in the jugueria VPC>"
$Listener = aws elbv2 describe-listeners --load-balancer-arn $Alb --query 'Listeners[?Port==`443`].ListenerArn' --output text

aws acm list-certificates --region us-east-1 --query "CertificateSummaryList[].[CertificateArn,DomainName]" --output table
$Cert = "<ARN of the project certificate>"
aws acm describe-certificate --certificate-arn $Cert --query "Certificate.[Status,SubjectAlternativeNames]"

aws elbv2 add-listener-certificates --listener-arn $Listener --certificates CertificateArn=$Cert
aws elbv2 describe-listener-certificates --listener-arn $Listener --query "Certificates[].CertificateArn"
```

The listener keeps the certificate Express created as its default; the project certificate is added next to it and chosen by SNI.

## 2. Add the custom hosts to the Express rules

Each service has one host-header rule on the HTTPS listener whose value is the service's generated endpoint. Rule priorities are **not** stable identifiers (Express picks them), so find the rule by the raw host:

```powershell
$Account = aws sts get-caller-identity --query Account --output text
foreach ($App in "frontend", "backend") {
  $Raw = aws ecs describe-express-gateway-service `
    --service-arn "arn:aws:ecs:us-east-1:${Account}:service/jugueria-test/jugueria-test-$App" `
    --query "service.activeConfigurations[0].ingressPaths[0].endpoint" --output text
  "$App  $Raw"
}
```

Then, for one app at a time (`$Raw` is that app's endpoint without a scheme, `$Custom` the host to add):

```powershell
$Raw = "<raw endpoint host>"
$Custom = "test.jugueria.jhanantezana.com"

$Rules = (aws elbv2 describe-rules --listener-arn $Listener --output json | ConvertFrom-Json).Rules
$Rule = $Rules | Where-Object { $_.Conditions | Where-Object { $_.Field -eq "host-header" -and $_.HostHeaderConfig.Values -contains $Raw } }
$Rule.RuleArn; $Rule.Priority; ($Rule.Conditions | ConvertTo-Json -Depth 10)

$Conditions = foreach ($Condition in $Rule.Conditions) {
  if ($Condition.Field -eq "host-header") {
    @{ Field = "host-header"; HostHeaderConfig = @{ Values = @($Raw, $Custom) } }
  } else {
    $Condition | Select-Object -Property * -ExcludeProperty Values
  }
}
ConvertTo-Json -InputObject @($Conditions) -Depth 10 | Set-Content -Encoding ascii conditions.json
aws elbv2 modify-rule --rule-arn $Rule.RuleArn --conditions file://conditions.json
Remove-Item conditions.json
```

**WARNING: change only the conditions, never the actions.** `modify-rule` without `--actions` leaves them alone; do not pass `--actions`. Express owns the actions and flips the forward weights between the two target groups on every canary deploy, so writing them back would point the rule at a stale target group. `--conditions` replaces **all** conditions of the rule, which is why the script keeps the raw host and any other condition as they were.

Repeat for the other app (the backend rule gets `api.test.jugueria.jhanantezana.com`). Confirm:

```powershell
aws elbv2 describe-rules --rule-arns $Rule.RuleArn --query "Rules[].Conditions[].HostHeaderConfig.Values" --output json
```

## 3. Allow the frontend host in Angular

Angular answers `400` for a `Host` that is not in `NG_ALLOWED_HOSTS`. `_deploy-ecs.yml` builds it as `<raw endpoint>,<custom host>` when the GitHub environment variable `CUSTOM_FRONTEND_HOST` is set, on every frontend deploy including a forced one and `rollback.yml`. With the variable unset, only the raw endpoint is allowed.

```bash
gh variable set CUSTOM_FRONTEND_HOST --env test --body "test.jugueria.jhanantezana.com"
```

The variable only reaches the container on a new deployment:

- `test`: `gh workflow run cd-test.yml --ref develop -f redeploy=frontend`.
- `prod`: `cd-prod.yml` deploys only when the digest differs, so run **Rollback** (`rollback.yml`, which forces the deploy) with environment `prod`, application `frontend`, and the commit `prod` runs now (see `rollback.md`). For example: `gh workflow run rollback.yml -f environment=prod -f app=frontend -f sha=<40-character sha>`.

Check the result without a browser (before DNS, see step 6): the raw endpoint and the custom host must both answer `200` on `/healthz`.

## 4. DNS

The `A` aliases to the ALB are Terraform in the `shared` root, switched on per environment by the `custom_domain_environments` variable (default `["test"]`; `infra/CLAUDE.md` has the details). Apply it with the usual `shared` workflow. Adding `"prod"` creates the `prod` records, which is the last step of wiring `prod`.

```powershell
Resolve-DnsName test.jugueria.jhanantezana.com -Type A
Resolve-DnsName api.test.jugueria.jhanantezana.com -Type A
```

Both must resolve to the ALB addresses. Resolver caches can lag a few minutes.

## 5. Switch the smoke checks on

Set these **after** DNS exists, otherwise the next deploy fails its smoke test:

```bash
gh variable set CUSTOM_API_HOST --env test --body "api.test.jugueria.jhanantezana.com"
```

`CUSTOM_FRONTEND_HOST` was set in step 3. When both are set, `smoke` (after the raw-endpoint checks) requires `200` from `https://<frontend>/healthz`, `https://<frontend>/login` and `https://<api>/actuator/health/readiness`, and a CORS preflight to `https://<api>/api/v1/auth/login` with `Origin: https://<frontend>` that answers 2xx with `Access-Control-Allow-Origin` equal to that origin. A failure names the check and what to look at (rule host condition, listener certificate, DNS record, `NG_ALLOWED_HOSTS`) and blocks the digest recording. When either variable is unset the checks are skipped with a notice.

## 6. Verify

Before DNS exists, send the request to the ALB while keeping the custom name for SNI and the `Host` header:

```bash
ALB=<DNS name of the ALB>
curl -sS -o /dev/null -w '%{http_code}\n' --connect-to test.jugueria.jhanantezana.com:443:$ALB:443 https://test.jugueria.jhanantezana.com/healthz
curl -sS -o /dev/null -w '%{http_code}\n' --connect-to api.test.jugueria.jhanantezana.com:443:$ALB:443 https://api.test.jugueria.jhanantezana.com/actuator/health/readiness
```

Both must print `200`. After DNS, drop `--connect-to`.

CORS preflight with an allowed and a disallowed origin. The first must answer 2xx with `access-control-allow-origin` equal to the origin; the second must not echo it:

```bash
API=https://api.test.jugueria.jhanantezana.com/api/v1/auth/login
for ORIGIN in https://test.jugueria.jhanantezana.com https://evil.example.com; do
  echo "== $ORIGIN"
  curl -sS -i -X OPTIONS "$API" -H "Origin: $ORIGIN" \
    -H 'Access-Control-Request-Method: POST' \
    -H 'Access-Control-Request-Headers: content-type,x-requested-with' | grep -iE '^(HTTP|access-control-allow-origin)'
done
```

Login round trip and cookie attributes (the password comes from a prompt, not from history):

```bash
read -rsp 'Password: ' PASSWORD; echo
curl -sS -i "$API" -H 'Origin: https://test.jugueria.jhanantezana.com' -H 'X-Requested-With: XMLHttpRequest' \
  -H 'Content-Type: application/json' --data "{\"email\":\"<email>\",\"password\":\"$PASSWORD\"}" | grep -iE '^(HTTP|set-cookie)'
unset PASSWORD
```

Expect `200` and `Set-Cookie: __Host-refresh-token=...; Path=/; Secure; HttpOnly; SameSite=Strict`, with no `Domain` attribute. Then sign in from a browser at the frontend host and confirm the session survives a reload (the refresh cookie) and the board receives live updates (`/ws`).

WebSocket route, without a browser:

```bash
curl -sS -i --http1.1 -m 5 https://api.test.jugueria.jhanantezana.com/ws \
  -H 'Connection: Upgrade' -H 'Upgrade: websocket' -H 'Sec-WebSocket-Version: 13' \
  -H "Sec-WebSocket-Key: $(openssl rand -base64 16)" -H 'Origin: https://test.jugueria.jhanantezana.com' | head -n 5
```

`curl -m 5` against `/ws` exits with 28 (timeout) even when it received `101 Switching Protocols`, because the connection stays open; the status line is what matters. Any answer from the backend proves the route (`101`, or `401`/`403` if the handshake requires a token). A `404` or `503` from the ALB, or a `400` from Angular, means the host is on the wrong rule.

Real client IP: make an audited change (for example rename a category in the admin UI) from a known network, then read the newest `audit.audit_log` row (`database-bootstrap.md` explains database access) and compare its `client_ip` with `curl https://checkip.amazonaws.com`. It must be your address, not a private ALB address.

## 7. Roll back a step

| Step | How to undo |
|------|-------------|
| 5 Smoke variables | `gh variable delete CUSTOM_API_HOST --env test` (the checks are skipped again) |
| 4 DNS | Remove the environment from `custom_domain_environments` and apply `shared` |
| 3 Angular host | `gh variable delete CUSTOM_FRONTEND_HOST --env test`, then redeploy the frontend as in step 3; the custom host answers `400` again |
| 2 Rule hosts | Run the step 2 script with `@($Raw)` as the values. Conditions only, never actions |
| 1 Certificate | `aws elbv2 remove-listener-certificates --listener-arn $Listener --certificates CertificateArn=$Cert`. This affects every environment on the shared ALB: do it only when none uses a custom host |

Undo in the reverse order. A rollback of an application (`rollback.yml`) keeps all of this: it deploys through the same workflow, which re-applies `CUSTOM_FRONTEND_HOST`, and Express leaves the rule hosts alone.

## 8. When Express recreates a service or the ALB

A service Express recreates (deleted and created again, or a changed network configuration that forces it) gets a **new generated endpoint**, and a replaced ALB loses the listener certificate and every rule host. Nothing heals these, and the smoke test is the guard: after such an event the next deploy fails with a message naming the check.

- New endpoint on a service: redo step 2 for that app (the new raw host, plus the custom host), then redeploy the frontend if it was the frontend (step 3 builds `NG_ALLOWED_HOSTS` from the new endpoint and the variable). No variable changes.
- New ALB: redo steps 1 and 2 for every app of every wired environment, and update the Route 53 aliases (Terraform reads the ALB; apply `shared`).

## 9. Values

| | `test` | `prod` |
|---|--------|--------|
| `CUSTOM_FRONTEND_HOST` | `test.jugueria.jhanantezana.com` | `jugueria.jhanantezana.com` |
| `CUSTOM_API_HOST` | `api.test.jugueria.jhanantezana.com` | `api.jugueria.jhanantezana.com` |
| Frontend rule | the rule whose host is the frontend service's raw endpoint | same, in `prod` |
| Backend rule | the rule whose host is the backend service's raw endpoint | same, in `prod` |
| Service ARN | `arn:aws:ecs:us-east-1:<account>:service/jugueria-test/jugueria-test-<app>` | `.../jugueria-prod/jugueria-prod-<app>` |
| `E2E_BASE_URL` / `E2E_API_URL` | the two `test` hosts above | never (the suite refuses production) |

`prod` is **not wired yet**. Before enabling it:

1. `test` has been running on its custom hosts through at least one forced redeploy with a green smoke test.
2. The `prod` environment is running and its services are deployed.
3. Add the `prod` hosts to the rules (step 2) and set `CUSTOM_FRONTEND_HOST` in the `prod` environment, then redeploy the frontend through **Rollback** (step 3), with the owner approving the `prod` run.
4. Apply `shared` with `custom_domain_environments = ["test", "prod"]` (step 4).
5. Only then set `CUSTOM_API_HOST` in the `prod` environment (step 5) and run the verification of step 6 against the `prod` hosts.

The URL for the SES production access request is the `prod` frontend host, so wire `prod` before it.

## 10. Shell notes

- Windows Git Bash: `MSYS_NO_PATHCONV=1` before any command with an argument that starts with `/`.
- PowerShell: JMESPath literals use backticks; keep the query in single quotes (as above) or double the backtick inside double quotes.
- Never pass `--actions` to `modify-rule` (step 2).
