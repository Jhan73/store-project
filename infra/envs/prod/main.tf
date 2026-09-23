module "environment" {
  source = "../../modules/environment"

  environment           = "prod"
  backup_retention_days = 14
  deletion_protection   = true
  log_retention_days    = 90
}
