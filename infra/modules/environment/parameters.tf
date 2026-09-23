# Secret values (application database credentials, JWT keys) are created out of band:
# a managed secret would be refreshed into the Terraform state, which lives in a shared bucket.
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
