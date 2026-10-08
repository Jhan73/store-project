variable "environment" {
  type = string

  validation {
    condition     = contains(["test", "prod"], var.environment)
    error_message = "environment must be test or prod."
  }
}

variable "db_identifier" {
  description = "RDS instance identifier. Null keeps jugueria-<environment>."
  type        = string
  default     = null
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "backup_retention_days" {
  type = number
}

variable "deletion_protection" {
  type = bool
}

variable "log_retention_days" {
  type = number
}

variable "media_versioning" {
  type = bool
}
