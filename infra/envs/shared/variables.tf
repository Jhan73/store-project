variable "region" {
  type    = string
  default = "us-east-1"
}

variable "account_id" {
  type    = string
  default = "463470979604"
}

variable "vpc_cidr" {
  type    = string
  default = "10.40.0.0/16"
}

variable "budget_limit_usd" {
  description = "Monthly alert threshold: 70 before launch, 120 from launch."
  type        = number
  default     = 70
}

variable "budget_alert_email" {
  description = "Receives budget alerts. Pass it as TF_VAR_budget_alert_email; it is not committed."
  type        = string
}

variable "developer_username" {
  description = "IAM Identity Center user that receives the jugueria-dev permission set."
  type        = string
  default     = "jugueria-dev"
}

variable "custom_domain_environments" {
  description = "Environments whose custom hostnames point at the shared ALB. Add prod only after its listener rules and NG_ALLOWED_HOSTS are wired: DNS reaching the ALB before the rules exist returns 404."
  type        = list(string)
  default     = ["test"]

  validation {
    condition     = alltrue([for env in var.custom_domain_environments : contains(["test", "prod"], env)])
    error_message = "Only test and prod are allowed."
  }
}
