# Secret values (application database credentials, JWT keys) are created out of band:
# a managed secret would be refreshed into the Terraform state, which lives in a shared bucket.
resource "aws_ssm_parameter" "db_url" {
  name  = "${local.ssm_prefix}/db/url"
  type  = "String"
  value = "jdbc:postgresql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/jugueria?sslmode=require"
}

# Under power/ because the workflows that start and stop the database already read that path.
resource "aws_ssm_parameter" "power_db_instance" {
  name  = "${local.ssm_prefix}/power/db-instance"
  type  = "String"
  value = aws_db_instance.main.identifier
}

resource "aws_ssm_parameter" "power_mode" {
  name  = "${local.ssm_prefix}/power/mode"
  type  = "String"
  value = "on-demand"

  lifecycle {
    ignore_changes = [value]
  }
}
