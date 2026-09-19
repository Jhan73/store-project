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

resource "aws_route53_record" "certificate_validation" {
  for_each = {
    for option in aws_acm_certificate.app.domain_validation_options : option.domain_name => {
      name   = option.resource_record_name
      record = option.resource_record_value
      type   = option.resource_record_type
    }
  }

  zone_id         = data.aws_route53_zone.parent.zone_id
  name            = each.value.name
  type            = each.value.type
  ttl             = 300
  records         = [each.value.record]
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "app" {
  certificate_arn         = aws_acm_certificate.app.arn
  validation_record_fqdns = [for record in aws_route53_record.certificate_validation : record.fqdn]
}
