resource "aws_lb" "feedback_viewer" {
  name               = "${var.product_name}-lb"
  internal           = false #tfsec:ignore:AWS005
  load_balancer_type = "application"

  idle_timeout               = 300
  enable_deletion_protection = true
  drop_invalid_header_fields = true

  security_groups = [
    aws_security_group.feedback_viewer_load_balancer_sg.id
  ]

  subnets = var.vpc_public_subnet_ids

  tags = merge(var.default_tags, {
    CostCentre   = var.billing_code
    ForceRefresh = "2025-08-14"
  })
}

resource "aws_lb_listener" "feedback_viewer_listener" {
  depends_on = [
    aws_acm_certificate.feedback_viewer,
    aws_route53_record.feedback_viewer_certificate_validation,
    aws_acm_certificate_validation.feedback_viewer,
  ]

  load_balancer_arn = aws_lb.feedback_viewer.arn
  port              = "443"
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-Res-PQ-2025-09"
  certificate_arn   = aws_acm_certificate.feedback_viewer.arn
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.feedback_viewer.arn
  }

  tags = merge(var.default_tags, {
    CostCentre   = var.billing_code
    ForceRefresh = "2025-08-14"
  })
}

resource "aws_lb_target_group" "feedback_viewer" {
  name                 = var.product_name
  port                 = 3001
  protocol             = "HTTP"
  protocol_version     = "HTTP1"
  target_type          = "ip"
  deregistration_delay = 30
  vpc_id               = var.vpc_id

  health_check {
    enabled             = true
    interval            = 60
    path                = "/health"
    timeout             = 30
    healthy_threshold   = 2
    unhealthy_threshold = 2
  }

  tags = merge(var.default_tags, {
    CostCentre   = var.billing_code
    ForceRefresh = "2025-08-14"
  })
}
