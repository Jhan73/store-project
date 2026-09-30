# Apply the media buckets and point the backend at them

## What this does

Product images live in a private S3 bucket per environment and are served through CloudFront. Until the buckets exist, an image upload answers `503 catalog.provider-unavailable`.

| Created | Where | Used by |
|---------|-------|---------|
| `jugueria-<env>-media` bucket (private, SSE-S3, TLS only; `prod` versioned) | `infra/envs/<env>` | Image storage |
| CloudFront distribution with Origin Access Control on the default `*.cloudfront.net` domain | `infra/envs/<env>` | Public image URLs |
| `s3:PutObject` on `products/*` of that bucket, on `jugueria-<env>-backend-task` | `infra/envs/<env>` | The backend's S3 adapter |
| `MEDIA_PUBLIC_BASE_URL` GitHub environment variable | GitHub, per environment | Sets `jugueria.catalog.images.public-base-url` |

## 1. Apply

There is no infra apply workflow yet (known gap: `infra/CLAUDE.md` says apply should run only through the protected GitHub environment). Today apply is manual with the admin profile, as at bootstrap, for `test` and then `prod`:

```powershell
$env:AWS_PROFILE = "jugueria-admin"
terraform -chdir=infra/envs/test init -backend-config=backend.hcl
terraform -chdir=infra/envs/test plan -out=test.tfplan
terraform -chdir=infra/envs/test apply test.tfplan
```

Before the first apply, check that the global bucket name is free; `404` means it is available and `403` means someone else owns it:

```powershell
aws s3api head-bucket --bucket jugueria-test-media
aws s3api head-bucket --bucket jugueria-prod-media
```

A new CloudFront distribution takes a few minutes to deploy.

## 2. Set the public base URL

```powershell
terraform -chdir=infra/envs/test output github_environment_variables
gh variable set MEDIA_PUBLIC_BASE_URL --env test --body "https://<distribution>.cloudfront.net"
```

Repeat with `--env prod` using the `prod` root's output. Then redeploy the backend so the new variable reaches the container.

- `test`: `gh workflow run cd-test.yml --ref develop -f redeploy=backend`.
- `prod`: do not wait for a release. `cd-prod.yml` deploys only when the image differs, so a config-only change is not applied otherwise. Run **Rollback** (`rollback.yml`, which forces the deploy) with environment `prod`, application `backend`, and the commit `prod` runs now (first row of the deployments query in `docs/runbooks/rollback.md`). It redeploys that same image with the current configuration. I chose this over a `force` input on `cd-prod.yml` to avoid a second way to bypass the digest comparison. An unset variable leaves the profile file's value in force, which is the not-yet-created custom domain.

## 3. Verify

Upload an image through the admin API in `test`, then open the `imageUrl` it returns: it must load over HTTPS with `Cache-Control: public, max-age=31536000, immutable`. A direct S3 URL must answer `403`.

## Follow-up: custom domain

`media.test.jugueria.jhanantezana.com` and `media.jugueria.jhanantezana.com` are covered by the existing certificate's wildcards. Using them needs a CloudFront alias with that certificate and a Route 53 alias record per environment, then dropping the `MEDIA_PUBLIC_BASE_URL` variable so the profile files apply. It changes DNS, so it waits for the owner's decision.
