output "database_address" {
  value = module.environment.database_address
}

output "backend_security_group_id" {
  value = module.environment.backend_security_group_id
}

output "frontend_security_group_id" {
  value = module.environment.frontend_security_group_id
}

output "task_execution_role_arn" {
  value = module.environment.task_execution_role_arn
}

output "backend_task_role_arn" {
  value = module.environment.backend_task_role_arn
}
