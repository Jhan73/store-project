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
