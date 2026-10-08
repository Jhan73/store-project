data "aws_route53_zone" "parent" {
  name         = "jhanantezana.com"
  private_zone = false
}

locals {
  domain = "jugueria.jhanantezana.com"
}

# One certificate for both environments behind the shared ALB: *.test covers api.test.
resource "aws_acm_certificate" "app" {
  domain_name               = local.domain
  subject_alternative_names = ["*.${local.domain}", "*.test.${local.domain}"]
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

# ACM validates *.domain with the same record as the apex, so it is skipped to keep one owner per record.
resource "aws_route53_record" "certificate_validation" {
  for_each = {
    for option in aws_acm_certificate.app.domain_validation_options : option.domain_name => {
      name   = option.resource_record_name
      record = option.resource_record_value
      type   = option.resource_record_type
    } if option.domain_name != "*.${local.domain}"
  }

  zone_id = data.aws_route53_zone.parent.zone_id
  name    = each.value.name
  type    = each.value.type
  ttl     = 300
  records = [each.value.record]
}

resource "aws_acm_certificate_validation" "app" {
  certificate_arn         = aws_acm_certificate.app.arn
  validation_record_fqdns = [for record in aws_route53_record.certificate_validation : record.fqdn]
}

# Owned by ECS Express Mode: looked up only, never managed.
data "aws_lb" "express" {
  tags = {
    AmazonECSManaged = "true"
  }
}

locals {
  environment_hosts = {
    test = {
      frontend = "test.${local.domain}"
      backend  = "api.test.${local.domain}"
    }
    prod = {
      frontend = local.domain
      backend  = "api.${local.domain}"
    }
  }

  custom_domain_hosts = merge([
    for env in var.custom_domain_environments : {
      for app, host in local.environment_hosts[env] : host => { environment = env, app = app }
    }
  ]...)
}

resource "aws_route53_record" "app" {
  for_each = local.custom_domain_hosts

  zone_id = data.aws_route53_zone.parent.zone_id
  name    = each.key
  type    = "A"

  alias {
    name                   = data.aws_lb.express.dns_name
    zone_id                = data.aws_lb.express.zone_id
    evaluate_target_health = false
  }
}
