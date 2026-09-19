module "environment" {
  source = "../../modules/environment"

  environment           = "test"
  backup_retention_days = 1
  deletion_protection   = false
}
