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
