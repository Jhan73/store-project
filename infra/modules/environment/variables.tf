variable "environment" {
  type = string

  validation {
    condition     = contains(["test", "prod"], var.environment)
    error_message = "environment must be test or prod."
  }
}

variable "backup_retention_days" {
  type = number
}

variable "deletion_protection" {
  type = bool
}
