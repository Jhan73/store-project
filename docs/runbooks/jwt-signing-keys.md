# Create or rotate the JWT signing key of an environment

## What this does

The backend signs access tokens with an RSA private key (RS256) and validates them with the matching public key, which it derives from the same private key. Every task of an environment must use the same key: a token signed by one task has to be accepted by the others behind the load balancer.

You end up with two parameters per environment, which the ECS task execution role injects as environment variables:

| SSM parameter | Type | Environment variable |
|---------------|------|----------------------|
| `/jugueria/<env>/identity/jwt/key-id` | `String` | `JWT_KEY_ID` |
| `/jugueria/<env>/identity/jwt/private-key` | `SecureString` | `JWT_PRIVATE_KEY` |

Run it **once per environment before the first deploy that needs it**. Until both parameters exist, the backend task cannot start: ECS fails to resolve a missing secret, and the backend refuses to start with an empty one rather than sign with a throwaway key.

Locally and in tests no key is configured, so the backend generates one in memory at startup and logs a warning. Only the `local` profile and the test fixture allow that (`jugueria.identity.jwt.ephemeral-key-allowed=true`); it never happens in `test` or `prod`.

## Why it is not Terraform

Same reason as the database credentials (`database-bootstrap.md`): a secret managed by Terraform is read back into the shared state file on every refresh.

## Prerequisites

| Requirement | How to check | Expected |
|-------------|--------------|----------|
| Admin SSO session | `aws sts get-caller-identity --profile jugueria-admin` | An ARN, not an error |
| OpenSSL (ships with Git for Windows) | `& "C:\Program Files\Git\usr\bin\openssl.exe" version` | A version number |

## Steps (PowerShell)

Repeat for `test` and `prod`. Each environment gets its **own** key: a token issued by `test` must never be valid in `prod`.

```powershell
$env:AWS_PROFILE = "jugueria-admin"
$EnvName = "test"                     # then "prod"
$KeyId   = (Get-Date -Format "yyyy-MM")

# The key only lives in this variable; it is never written to disk.
$PrivateKey = (& "C:\Program Files\Git\usr\bin\openssl.exe" genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048) -join "`n"

aws ssm put-parameter --name "/jugueria/$EnvName/identity/jwt/key-id"      --type String       --value $KeyId      --overwrite
aws ssm put-parameter --name "/jugueria/$EnvName/identity/jwt/private-key" --type SecureString --value $PrivateKey --overwrite

Remove-Variable PrivateKey
```

`genpkey` writes PKCS#8 (header `BEGIN PRIVATE KEY`), the only format the backend reads. A PKCS#1 key (header `BEGIN RSA PRIVATE KEY`, from `openssl genrsa`) is rejected at startup.

## Verify

Without printing the key:

```powershell
aws ssm get-parameter --name "/jugueria/$EnvName/identity/jwt/key-id" --query Parameter.Value --output text
(aws ssm get-parameter --name "/jugueria/$EnvName/identity/jwt/private-key" --with-decryption --query Parameter.Value --output text).Contains("BEGIN PRIVATE KEY")
```

Expected: the key ID, then `True`. After the next deploy, the backend log has no `No JWT signing key configured` warning.

## Rotate

Run the same steps with a new `$KeyId`, then redeploy the backend (`cd-test.yml` or `cd-prod.yml`, or `rollback.yml` to the running commit).

Nobody is logged out. Access tokens signed with the old key fail with `401` from the moment the new tasks run, and the frontend answers a `401` by refreshing: refresh tokens are opaque rows in the database, not JWTs, so they survive the rotation and the new access token is signed with the new key. Rotate right away if the key may have leaked; there is no need to rotate on a schedule.
