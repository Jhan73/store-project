provider "aws" {
  region              = "us-east-1"
  allowed_account_ids = ["463470979604"]

  default_tags {
    tags = {
      Project     = "jugueria"
      Environment = "test"
      ManagedBy   = "terraform"
      Repository  = "Jhan73/store-project"
    }
  }
}
