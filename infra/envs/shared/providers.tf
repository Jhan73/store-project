provider "aws" {
  region              = var.region
  allowed_account_ids = [var.account_id]

  default_tags {
    tags = {
      Project     = "jugueria"
      Environment = "shared"
      ManagedBy   = "terraform"
      Repository  = "Jhan73/store-project"
    }
  }
}
