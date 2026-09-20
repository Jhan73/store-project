output "vpc_id" {
  value = aws_vpc.main.id
}

output "public_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  value = aws_subnet.private[*].id
}

output "ecr_repository_urls" {
  value = { for name, repo in aws_ecr_repository.app : name => repo.repository_url }
}

output "github_oidc_provider_arn" {
  value = aws_iam_openid_connect_provider.github.arn
}

output "ci_plan_role_arn" {
  value = aws_iam_role.ci_plan.arn
}

output "infra_apply_role_arns" {
  value = { for env, role in aws_iam_role.infra_apply : env => role.arn }
}

output "certificate_arn" {
  value = aws_acm_certificate_validation.app.certificate_arn
}

output "email_identity" {
  value = aws_sesv2_email_identity.domain.email_identity
}

output "dev_media_bucket" {
  value = aws_s3_bucket.dev_media.bucket
}

output "deploy_role_arns" {
  value = { for env, role in aws_iam_role.deploy : env => role.arn }
}
