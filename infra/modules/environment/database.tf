resource "aws_db_subnet_group" "main" {
  name       = local.name
  subnet_ids = data.aws_subnets.private.ids
}

resource "aws_db_parameter_group" "main" {
  name   = local.name
  family = "postgres18"

  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
}

resource "aws_db_instance" "main" {
  identifier     = local.name
  engine         = "postgres"
  engine_version = "18"
  instance_class = "db.t4g.micro"

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = "jugueria"
  username = "jugueria_admin"
  # RDS keeps and rotates the master password in Secrets Manager; Terraform never sees it.
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  parameter_group_name   = aws_db_parameter_group.main.name
  vpc_security_group_ids = [aws_security_group.database.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period    = var.backup_retention_days
  deletion_protection        = var.deletion_protection
  skip_final_snapshot        = !var.deletion_protection
  final_snapshot_identifier  = var.deletion_protection ? "${local.name}-final" : null
  copy_tags_to_snapshot      = true
  auto_minor_version_upgrade = true
}
