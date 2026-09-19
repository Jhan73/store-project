locals {
  github_repository = "Jhan73/store-project"
  state_bucket_arn  = "arn:aws:s3:::acme-tfstate-dev-463470979604-us-east-1"
}

data "aws_iam_policy_document" "github_pull_request_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${local.github_repository}:pull_request"]
    }
  }
}

resource "aws_iam_role" "ci_plan" {
  name                 = "jugueria-ci-plan"
  description          = "Read-only terraform plan from pull requests"
  assume_role_policy   = data.aws_iam_policy_document.github_pull_request_assume.json
  max_session_duration = 3600
}

resource "aws_iam_role_policy_attachment" "ci_plan_read_only" {
  role       = aws_iam_role.ci_plan.name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

# Plans may read only this project's state (the bucket is shared) and never a secret value.
data "aws_iam_policy_document" "ci_plan_state_scope" {
  statement {
    sid           = "DenyObjectsOutsideJugueriaState"
    effect        = "Deny"
    actions       = ["s3:GetObject", "s3:GetObjectVersion"]
    not_resources = ["${local.state_bucket_arn}/jugueria/*"]
  }

  # ReadOnlyAccess includes ssm:Get*, and the aws/ssm key lets any account principal decrypt via SSM.
  statement {
    sid       = "DenySecretValues"
    effect    = "Deny"
    actions   = ["kms:Decrypt", "secretsmanager:GetSecretValue"]
    resources = ["*"]
  }

  statement {
    sid       = "DenyListingOutsideJugueriaState"
    effect    = "Deny"
    actions   = ["s3:ListBucket", "s3:ListBucketVersions"]
    resources = [local.state_bucket_arn]

    condition {
      test     = "StringNotLike"
      variable = "s3:prefix"
      values   = ["jugueria/*"]
    }
  }
}

resource "aws_iam_role_policy" "ci_plan_state_scope" {
  name   = "state-scope"
  role   = aws_iam_role.ci_plan.id
  policy = data.aws_iam_policy_document.ci_plan_state_scope.json
}

data "aws_iam_policy_document" "github_environment_assume" {
  for_each = toset(["test", "prod"])

  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${local.github_repository}:environment:${each.key}"]
    }
  }
}

resource "aws_iam_role" "infra_apply" {
  for_each = toset(["test", "prod"])

  name                 = "jugueria-infra-apply-${each.key}"
  description          = "terraform apply from the protected ${each.key} GitHub environment"
  assume_role_policy   = data.aws_iam_policy_document.github_environment_assume[each.key].json
  max_session_duration = 3600
}

resource "aws_iam_role_policy_attachment" "infra_apply_admin" {
  for_each = aws_iam_role.infra_apply

  role       = each.value.name
  policy_arn = "arn:aws:iam::aws:policy/AdministratorAccess"
}
