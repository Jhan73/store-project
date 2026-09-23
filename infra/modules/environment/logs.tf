# Express Mode does not create the log group, and the default never-expire retention leaks cost.
resource "aws_cloudwatch_log_group" "app" {
  for_each = toset(["backend", "frontend"])

  name              = "/ecs/${local.name}-${each.key}"
  retention_in_days = var.log_retention_days
}
