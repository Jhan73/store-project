module "environment" {
  source = "../../modules/environment"

  environment           = "prod"
  db_identifier         = "jugueria-prod-t3"
  db_instance_class     = "db.t3.micro"
  backup_retention_days = 14
  deletion_protection   = true
  log_retention_days    = 90
  media_versioning      = true
}
