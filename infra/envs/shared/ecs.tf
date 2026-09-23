# ECS creates this on first use from the console, which never happened in this account, and the
# deploy role deliberately cannot create it. Express Mode refuses to create a service without it.
resource "aws_iam_service_linked_role" "ecs" {
  aws_service_name = "ecs.amazonaws.com"
}
