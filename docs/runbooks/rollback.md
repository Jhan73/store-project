# Runbook — roll an application back

Target: back in service in under 30 minutes (PRD §9). One application at a time, one environment at a time.

Use this when a deploy made things worse and the previous image was fine. If the **database** is the problem, this is the wrong runbook: a rollback does not undo a migration.

## 1. Decide what to go back to

Every successful deploy records the image it left running, so the history is already there. Newest first:

```powershell
gh api "repos/Jhan73/store-project/deployments?environment=prod&task=deploy:backend&per_page=10" `
  --jq '.[] | "\(.created_at)  \(.sha)  \(.payload.digest)"'
```

Swap `prod` for `test` and `backend` for `frontend`. The first row is what is running now; the second is normally where you are going.

Confirm the image is still in ECR before you commit to it — the lifecycle policy keeps the last 30:

```powershell
aws ecr describe-images --repository-name jugueria/backend `
  --image-ids imageTag=<sha> --query 'imageDetails[0].imagePushedAt' --output text
```

## 2. Run it

Actions → **Rollback** → *Run workflow*, then: environment, application, and the full 40-character SHA.

`prod` waits for its reviewer before anything happens. Approve it.

The workflow resolves that commit's tag to a digest and deploys the digest, not the tag. If the commit was never published for that application it fails before touching the environment, which is the answer you want at that moment.

## 3. Check

Smoke runs automatically and the run is red if either service does not answer 200. Then confirm what the service actually runs:

```powershell
aws ecs describe-express-gateway-service `
  --service-arn arn:aws:ecs:us-east-1:463470979604:service/jugueria-prod/jugueria-prod-backend `
  --query 'service.activeConfigurations[0].primaryContainer.image' --output text
```

The workflow records the result, so the next release compares against the rolled-back image rather than the one it replaced.

## 4. Afterwards

A rollback buys time; it does not close the incident.

- Open a `hotfix/*` branch from `main` for the real fix, or revert the offending commit on `develop`.
- A rollback leaves `main` pointing at code that is **not** what production runs. Until the fix ships, the next release to `prod` will deploy `main` again — including the change you just rolled back.

## Why a rollback is safe, and when it is not

Migrations are expand/contract (tech-spec §4.10): a new version only adds columns, tables and indexes, and never drops or renames what the previous version reads. So the previous image still understands the schema the new one left behind.

That guarantee breaks the moment a migration destroys something. If the failing deploy dropped or renamed anything, **stop**: restoring the database is a separate procedure and losing 20 minutes reading is cheaper than a rollback that starts writing against a schema it misreads.
