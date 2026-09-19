# Application credentials (app and migrator roles) are created out of band: a managed
# secret would be refreshed into the Terraform state, which lives in a shared bucket.
resource "aws_ssm_parameter" "db_master_password" {
  name             = "${local.ssm_prefix}/db/master-password"
  type             = "SecureString"
  value_wo         = ephemeral.random_password.master.result
  value_wo_version = var.master_password_version
}

resource "aws_ssm_parameter" "db_url" {
  name  = "${local.ssm_prefix}/db/url"
  type  = "String"
  value = "jdbc:postgresql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/jugueria?sslmode=require"
}

resource "aws_ssm_parameter" "power_mode" {
  name  = "${local.ssm_prefix}/power/mode"
  type  = "String"
  value = "on-demand"

  lifecycle {
    ignore_changes = [value]
  }
}
