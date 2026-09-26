# Set up SES email delivery for an environment

## What this does

The backend sends the staff set-password email (and every later transactional email, M2-B6/M3-B2) through Amazon SES API v2, using the ECS task role — no stored SMTP credentials (D16). This runbook takes the domain identity from `terraform apply` to an environment that can actually deliver mail, and requests the production access that lets `prod` email real people.

You end up with:

| Created | Where | Used by |
|---------|-------|---------|
| SES domain identity `jugueria.jhanantezana.com`, DKIM (2048-bit), custom MAIL FROM, DMARC `p=none` | `infra/envs/shared` (already applied at M0-05) | Every environment — SES is account-level, shared by `test` and `prod` |
| `ses:SendEmail` on the backend task role, limited to `ses:FromAddress = no-reply@jugueria.jhanantezana.com` (any identity: in the sandbox SES also authorizes against the recipient's identity) | `infra/envs/<env>` | The backend's SES v2 adapter |
| SES production access | Requested manually in the SES console (tech-spec R5) | `prod` only |
| `NOTIFICATIONS_RECIPIENT_ALLOWLIST` GitHub environment variable | `test` only | Keeps `test` from emailing real people while the account is in the sandbox, and afterward |

## 1. Apply the Terraform changes

The domain identity, DKIM records, and MAIL FROM/DMARC records already exist from M0-05 (`infra/envs/shared/email.tf`) — nothing to do there. This slice only adds the IAM permission in each environment root:

```powershell
$env:AWS_PROFILE = "jugueria-admin"   # once the jugueria-infra-apply-<env> role exists, CI applies this instead
terraform -chdir=infra/envs/test init -backend-config=backend.hcl
terraform -chdir=infra/envs/test plan -out=test.tfplan
terraform -chdir=infra/envs/test apply test.tfplan

terraform -chdir=infra/envs/prod init -backend-config=backend.hcl
terraform -chdir=infra/envs/prod plan -out=prod.tfplan
terraform -chdir=infra/envs/prod apply prod.tfplan
```

Expected plan: one new resource per environment, `aws_iam_role_policy.backend_task_send_email`. Nothing in `shared` changes.

## 2. Verify the domain identity and DKIM

```powershell
aws sesv2 get-email-identity --email-identity jugueria.jhanantezana.com --query "{Verified:VerifiedForSendingStatus,DkimStatus:DkimAttributes.Status}"
```

Expected: `Verified: true`, `DkimStatus: SUCCESS`. DKIM verification is DNS propagation, not an API call — it can take up to 72 hours after the first `apply`, though it is typically much faster since the zone is already delegated. If `DkimStatus` stays `PENDING`, check the three CNAME records Terraform created (`aws_route53_record.dkim` in `infra/envs/shared/email.tf`) resolve:

```powershell
aws sesv2 get-email-identity --email-identity jugueria.jhanantezana.com --query "DkimAttributes.Tokens" --output text |
  ForEach-Object { $_.Split() } |
  ForEach-Object { Resolve-DnsName "$_._domainkey.jugueria.jhanantezana.com" -Type CNAME -ErrorAction SilentlyContinue }
```

## 3. The SES sandbox, and how it interacts with the `test` allowlist

A new SES account starts in the **sandbox**: it can only send *to* verified identities (individually verified email addresses, or a verified domain), regardless of any application-level allowlist. Until production access is granted (step 4):

- Verify the addresses you will use for manual testing: `aws sesv2 create-email-identity --email-identity you@example.com`, then click the confirmation link SES emails to that address.
- Set `NOTIFICATIONS_RECIPIENT_ALLOWLIST` (step 5) to those same verified addresses. The allowlist and the sandbox are two independent gates — the allowlist decides which recipients the *application* will attempt to email; the sandbox decides which of those attempts SES will actually accept. An address that is allowlisted but not sandbox-verified fails at SES with `MessageRejected`; the backend logs that failure the same way it logs any other transport error (`StaffProvisioningService` catches it, the account still exists, never logs the link).
- `prod` also starts in the sandbox until step 4 completes — its empty allowlist does not bypass it.

## 4. Request SES production access (R5, do during M1)

Console, since the CLI operation (`sesv2 put-account-details`) asks for the same information with no shortcut:

1. SES console → **Account dashboard** → **Request production access**.
2. Mail type: **Transactional**. Website URL: `https://jugueria.jhanantezana.com`.
3. Use case description: transactional email for a juice bar ordering platform — set-password links for staff accounts, and (from M3) order confirmations, verification, password reset, and refund notices for customers. Approximate volume: low (tens of emails per day at this scale).
4. Compliance: describe bounce/complaint handling as "SES default reputation monitoring; no additional list-management system yet" — accurate for this release, revisit if AWS asks for more before approving.
5. Submit. AWS typically answers within 24 hours; check status with:

```powershell
aws sesv2 get-account --query "{ProductionAccessEnabled:ProductionAccessEnabled,SendingEnabled:SendingEnabled,SendQuota:SendQuota}"
```

Once `ProductionAccessEnabled` is `true`, the sandbox restriction is gone for the whole account (`test` included) — the `test` allowlist becomes the only thing keeping `test` from emailing real people, exactly as tech-spec §8.1 describes.

## 5. GitHub environment variables

Set once per repository environment (`Settings → Environments → test / prod → Variables`), or with `gh`:

```powershell
gh variable set NOTIFICATIONS_RECIPIENT_ALLOWLIST --env test --body "you@example.com,@jugueria.pe"
```

| Variable | `test` | `prod` |
|----------|--------|--------|
| `NOTIFICATIONS_RECIPIENT_ALLOWLIST` | Comma-separated addresses or `@domain` suffixes (matched case-insensitively) that may receive mail from `test`. Leave unset while relying only on sandbox-verified addresses (step 3) — set it once a broader group needs to see `test` email | Leave unset — an empty allowlist means "send to everyone", which is correct once production access (step 4) is granted |

No SSM parameter is needed: the sender address and SES region are non-secret defaults committed in `backend/src/main/resources/application.properties` (`jugueria.notifications.sender-address`, `jugueria.notifications.ses.region`), the same pattern as every other non-secret setting (tech-spec §10.3).

## Cost

SES is billed per email (first 62,000 outbound emails/month effectively free from an EC2/ECS-hosted sender, USD 0.10/1,000 after) — cents at this project's volume, already covered by the "usage-billed services cost cents" line in tech-spec §8.3. No budget change needed.

## Next step

The task role policy applies without a redeploy. A changed `NOTIFICATIONS_RECIPIENT_ALLOWLIST` only reaches the container on a new deployment: in `test`, run `gh workflow run cd-test.yml --ref develop -f redeploy=backend`; in `prod`, run `rollback.yml` to the running commit, which always redeploys. Then send a real set-password link (`POST /api/v1/staff`) and confirm it arrives.
