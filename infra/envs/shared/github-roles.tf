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

locals {
  ecr_repository_arns = [for repo in aws_ecr_repository.app : repo.arn]
}

data "aws_iam_policy_document" "deploy" {
  for_each = toset(["test", "prod"])

  statement {
    sid       = "EcrLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "EcrPushAndRead"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
      "ecr:DescribeImages",
    ]
    resources = local.ecr_repository_arns
  }

  # Express Mode owns the load balancer and its certificate, so it is driven only through these APIs.
  statement {
    sid = "EcsExpressDeploy"
    actions = [
      "ecs:CreateCluster",
      "ecs:RegisterTaskDefinition",
      "ecs:CreateExpressGatewayService",
      "ecs:UpdateExpressGatewayService",
      "ecs:DescribeExpressGatewayService",
      "ecs:DescribeClusters",
      "ecs:DescribeServices",
      "ecs:UpdateService",
      "ecs:ListServiceDeployments",
      "ecs:DescribeServiceDeployments",
      "ecs:TagResource",
      "ecs:UntagResource",
    ]
    resources = ["*"]
  }

  statement {
    sid       = "PassEnvironmentRoles"
    actions   = ["iam:PassRole"]
    resources = ["arn:aws:iam::${var.account_id}:role/jugueria-${each.key}-*"]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com", "ecs.amazonaws.com"]
    }
  }

  # Power modes: the deploy starts a stopped environment before rolling out (tech-spec 8.1).
  statement {
    sid       = "ReadPowerMode"
    actions   = ["ssm:GetParameter", "ssm:PutParameter"]
    resources = ["arn:aws:ssm:${var.region}:${var.account_id}:parameter/jugueria/${each.key}/power/*"]
  }

  statement {
    sid       = "StartEnvironmentDatabase"
    actions   = ["rds:StartDBInstance", "rds:StopDBInstance"]
    resources = ["arn:aws:rds:${var.region}:${var.account_id}:db:jugueria-${each.key}"]
  }

  statement {
    sid       = "ReadDatabaseState"
    actions   = ["rds:DescribeDBInstances"]
    resources = ["*"]
  }
}

resource "aws_iam_role" "deploy" {
  for_each = toset(["test", "prod"])

  name                 = "jugueria-deploy-${each.key}"
  description          = "Build, push and deploy from the protected ${each.key} GitHub environment"
  assume_role_policy   = data.aws_iam_policy_document.github_environment_assume[each.key].json
  max_session_duration = 3600
}

resource "aws_iam_role_policy" "deploy" {
  for_each = aws_iam_role.deploy

  name   = "deploy"
  role   = each.value.id
  policy = data.aws_iam_policy_document.deploy[each.key].json
}
