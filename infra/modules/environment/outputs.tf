output "database_address" {
  value = aws_db_instance.main.address
}

output "backend_security_group_id" {
  value = aws_security_group.backend.id
}

output "frontend_security_group_id" {
  value = aws_security_group.frontend.id
}

output "task_execution_role_arn" {
  value = aws_iam_role.task_execution.arn
}

output "backend_task_role_arn" {
  value = aws_iam_role.backend_task.arn
}

output "database_master_secret_arn" {
  value = aws_db_instance.main.master_user_secret[0].secret_arn
}

output "ecs_infrastructure_role_arn" {
  value = aws_iam_role.ecs_infrastructure.arn
}
