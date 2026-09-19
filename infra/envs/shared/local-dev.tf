resource "aws_s3_bucket" "dev_media" {
  bucket = "jugueria-dev-media"
}

resource "aws_s3_bucket_ownership_controls" "dev_media" {
  bucket = aws_s3_bucket.dev_media.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "dev_media" {
  bucket                  = aws_s3_bucket.dev_media.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

data "aws_ssoadmin_instances" "main" {}

locals {
  sso_instance_arn  = one(data.aws_ssoadmin_instances.main.arns)
  identity_store_id = one(data.aws_ssoadmin_instances.main.identity_store_ids)
}

resource "aws_ssoadmin_permission_set" "local_dev" {
  name             = "jugueria-dev"
  description      = "Local development: read and write the jugueria-dev-media bucket only"
  instance_arn     = local.sso_instance_arn
  session_duration = "PT8H"
}

data "aws_iam_policy_document" "local_dev" {
  statement {
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.dev_media.arn]
  }

  statement {
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.dev_media.arn}/*"]
  }
}

resource "aws_ssoadmin_permission_set_inline_policy" "local_dev" {
  instance_arn       = local.sso_instance_arn
  permission_set_arn = aws_ssoadmin_permission_set.local_dev.arn
  inline_policy      = data.aws_iam_policy_document.local_dev.json
}

data "aws_identitystore_user" "developer" {
  identity_store_id = local.identity_store_id

  alternate_identifier {
    unique_attribute {
      attribute_path  = "UserName"
      attribute_value = var.developer_username
    }
  }
}

resource "aws_ssoadmin_account_assignment" "local_dev" {
  instance_arn       = local.sso_instance_arn
  permission_set_arn = aws_ssoadmin_permission_set.local_dev.arn
  principal_id       = data.aws_identitystore_user.developer.user_id
  principal_type     = "USER"
  target_id          = var.account_id
  target_type        = "AWS_ACCOUNT"
}
