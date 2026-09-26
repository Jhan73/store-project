# SES is account-level, shared by test and prod (infra/envs/shared/email.tf); the identity ARN is
# built here rather than read back from shared state, which this project's roles cannot access.
locals {
  ses_identity_arn = "arn:aws:ses:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:identity/jugueria.jhanantezana.com"
}

data "aws_iam_policy_document" "send_email" {
  statement {
    actions   = ["ses:SendEmail"]
    resources = [local.ses_identity_arn]
  }
}

resource "aws_iam_role_policy" "backend_task_send_email" {
  name   = "send-email"
  role   = aws_iam_role.backend_task.id
  policy = data.aws_iam_policy_document.send_email.json
}
