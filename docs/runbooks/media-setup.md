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

Same procedure as the other environment changes (`terraform plan`, then `apply` per `infra/CLAUDE.md`), for `test` and then `prod`. A new CloudFront distribution takes a few minutes to deploy.

## 2. Set the public base URL

```powershell
terraform -chdir=infra/envs/test output github_environment_variables
gh variable set MEDIA_PUBLIC_BASE_URL --env test --body "https://<distribution>.cloudfront.net"
```

Repeat with `--env prod` using the `prod` root's output. Then redeploy the backend (`gh workflow run cd-test.yml --ref develop -f redeploy=backend`; for `prod`, the next release). An unset variable leaves the profile file's value in force, which is the not-yet-created custom domain.

## 3. Verify

Upload an image through the admin API in `test`, then open the `imageUrl` it returns: it must load over HTTPS with `Cache-Control: public, max-age=31536000, immutable`. A direct S3 URL must answer `403`.

## Follow-up: custom domain

`media.test.jugueria.jhanantezana.com` and `media.jugueria.jhanantezana.com` are covered by the existing certificate's wildcards. Using them needs a CloudFront alias with that certificate and a Route 53 alias record per environment, then dropping the `MEDIA_PUBLIC_BASE_URL` variable so the profile files apply. It changes DNS, so it waits for the owner's decision.
