data "aws_region" "current" {}

data "aws_caller_identity" "current" {}

data "aws_vpc" "main" {
  tags = {
    Name = "jugueria-vpc"
  }
}

data "aws_subnets" "private" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.main.id]
  }

  tags = {
    Tier = "private"
  }
}

locals {
  name       = "jugueria-${var.environment}"
  ssm_prefix = "/jugueria/${var.environment}"
}
