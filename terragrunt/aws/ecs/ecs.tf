locals {
  container_secrets = [
    {
      name      = "DOCDB_USERNAME"
      valueFrom = var.docdb_username_arn
    },
    {
      name      = "DOCDB_PASSWORD"
      valueFrom = var.docdb_password_arn
    },
    {
      name      = "JWT_SECRET_KEY"
      valueFrom = var.jwt_secret_key_arn
    }
  ]
}

module "feedback_viewer" {
  source = "github.com/cds-snc/terraform-modules//ecs?ref=v10.9.1"

  # Cluster and service
  cluster_name = "${var.product_name}-cluster"
  service_name = "${var.product_name}-app-service"

  # Task/Container definition
  container_image     = "${var.ecr_repository_url}:${var.image_tag}"
  container_name      = var.product_name
  task_cpu            = var.cpu
  task_memory         = var.memory
  container_port      = var.container_port
  container_host_port = 3001
  container_secrets = [
    {
      name      = "SPRING_DATA_MONGODB_USERNAME"
      valueFrom = var.docdb_username_arn
    },
    {
      name      = "SPRING_DATA_MONGODB_PASSWORD"
      valueFrom = var.docdb_password_arn
    },
    {
      name      = "JWT_SECRET_KEY"
      valueFrom = var.jwt_secret_key_arn
    }
  ]
  container_environment = [
    {
      name  = "SPRING_DATA_MONGODB_URI"
      value = "mongodb://$${SPRING_DATA_MONGODB_USERNAME}:$${SPRING_DATA_MONGODB_PASSWORD}@${var.docdb_endpoint}:27017/pagesuccess?ssl=true&retryWrites=false&tlsAllowInvalidHostnames=true"
    },
    {
      name  = "SERVER_FORWARD_HEADERS_STRATEGY"
      value = "FRAMEWORK"
    },
    {
      name  = "MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE"
      value = "health"
    },
    {
      name  = "MANAGEMENT_ENDPOINTS_WEB_BASE_PATH"
      value = "/"
    },
    {
      name  = "MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS"
      value = "always"
    },
    {
      name  = "MANAGEMENT_HEALTH_DEFAULTS_ENABLED"
      value = "true"
    }
  ]
  # Container health check - ECS won't mark the container healthy until /health returns 0.
  # startPeriod gives the JVM/Spring 60s to boot before failures are counted.
  container_health_check = {
    command     = ["CMD-SHELL", "wget -q -O /dev/null http://localhost:3001/health || exit 1"]
    interval    = 15
    timeout     = 5
    retries     = 3
    startPeriod = 60
  }

  container_linux_parameters = {}
  container_ulimits = [
    {
      "hardLimit" : 1000000,
      "name" : "nofile",
      "softLimit" : 1000000
    }
  ]
  container_read_only_root_filesystem = false

  # Task definition
  task_name          = "${var.product_name}-task"
  task_exec_role_arn = var.iam_role_arn
  task_role_arn      = var.iam_role_arn

  # Scaling
  enable_autoscaling = true
  desired_count      = var.desired_count

  # Networking
  lb_target_group_arn = var.lb_target_group_arn
  security_group_ids  = [aws_security_group.ecs_tasks.id]
  subnet_ids          = var.private_subnet_ids

  # Forward logs to Sentinel
  sentinel_forwarder           = true
  sentinel_forwarder_layer_arn = "arn:aws:lambda:ca-central-1:283582579564:layer:aws-sentinel-connector-layer:238"

  billing_tag_value = var.billing_code

  # Enabled to allow connection to DB only in staging
  enable_execute_command = var.env == "staging" ? true : false
}

resource "aws_cloudwatch_log_group" "feedback-viewer_group" {
  name              = "/aws/ecs/${var.product_name}-cluster"
  retention_in_days = 30
}

resource "aws_cloudwatch_log_stream" "feedback-viewer_stream" {
  name           = "${var.product_name}-log-stream"
  log_group_name = aws_cloudwatch_log_group.feedback-viewer_group.name
}
