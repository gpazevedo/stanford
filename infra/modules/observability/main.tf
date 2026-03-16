# ── SNS Topic for alarm notifications ────────────────────────────────────────

resource "aws_sns_topic" "alarms" {
  name = "${var.project_name}-${var.environment}-alarms"
}

resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

# ── CloudWatch Log Groups (explicit, 30-day retention) ───────────────────────

resource "aws_cloudwatch_log_group" "api" {
  name              = "/aws/lambda/${var.api_lambda_name}"
  retention_in_days = 30
}

resource "aws_cloudwatch_log_group" "ingestion" {
  name              = "/aws/lambda/${var.ingestion_lambda_name}"
  retention_in_days = 30
}

# ── CloudWatch Alarms ─────────────────────────────────────────────────────────

resource "aws_cloudwatch_metric_alarm" "api_error_rate" {
  alarm_name          = "${var.project_name}-${var.environment}-api-error-rate"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  threshold           = 5
  alarm_description   = "API Lambda error rate > 5% over 5 minutes"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
  treat_missing_data  = "notBreaching"

  metric_query {
    id          = "error_rate"
    expression  = "100 * errors / invocations"
    label       = "Error Rate (%)"
    return_data = true
  }

  metric_query {
    id = "errors"
    metric {
      namespace   = "AWS/Lambda"
      metric_name = "Errors"
      dimensions  = { FunctionName = var.api_lambda_name }
      period      = 300
      stat        = "Sum"
    }
  }

  metric_query {
    id = "invocations"
    metric {
      namespace   = "AWS/Lambda"
      metric_name = "Invocations"
      dimensions  = { FunctionName = var.api_lambda_name }
      period      = 300
      stat        = "Sum"
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "api_p99_duration" {
  alarm_name          = "${var.project_name}-${var.environment}-api-p99-duration"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  threshold           = 10000
  alarm_description   = "API Lambda P99 duration > 10s over 5 minutes"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
  treat_missing_data  = "notBreaching"

  namespace          = "AWS/Lambda"
  metric_name        = "Duration"
  dimensions         = { FunctionName = var.api_lambda_name }
  period             = 300
  extended_statistic = "p99"
}

resource "aws_cloudwatch_metric_alarm" "ingestion_errors" {
  alarm_name          = "${var.project_name}-${var.environment}-ingestion-errors"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  threshold           = 1
  alarm_description   = "Ingestion Lambda error detected"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
  treat_missing_data  = "notBreaching"

  namespace   = "AWS/Lambda"
  metric_name = "Errors"
  dimensions  = { FunctionName = var.ingestion_lambda_name }
  period      = 300
  statistic   = "Sum"
}

resource "aws_cloudwatch_metric_alarm" "api_gateway_5xx" {
  alarm_name          = "${var.project_name}-${var.environment}-apigw-5xx"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  threshold           = 1
  alarm_description   = "API Gateway 5xx rate > 1% over 5 minutes"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
  treat_missing_data  = "notBreaching"

  metric_query {
    id          = "error_rate"
    expression  = "100 * errors / requests"
    label       = "5xx Rate (%)"
    return_data = true
  }

  metric_query {
    id = "errors"
    metric {
      namespace   = "AWS/ApiGateway"
      metric_name = "5XXError"
      dimensions  = { ApiId = var.api_gateway_id }
      period      = 300
      stat        = "Sum"
    }
  }

  metric_query {
    id = "requests"
    metric {
      namespace   = "AWS/ApiGateway"
      metric_name = "Count"
      dimensions  = { ApiId = var.api_gateway_id }
      period      = 300
      stat        = "Sum"
    }
  }
}

# ── AppConfig alarm-based rollback ────────────────────────────────────────────

resource "aws_appconfig_deployment_strategy" "alarm_rollback" {
  name                           = "${var.project_name}-with-rollback"
  description                    = "5-minute bake with alarm-based rollback"
  deployment_duration_in_minutes = 5
  final_bake_time_in_minutes     = 5
  growth_factor                  = 100
  growth_type                    = "LINEAR"
  replicate_to                   = "NONE"
}

resource "aws_appconfig_deployment" "with_rollback" {
  application_id           = var.appconfig_application_id
  environment_id           = var.appconfig_environment_id
  configuration_profile_id = var.appconfig_profile_id
  configuration_version    = var.appconfig_version_number
  deployment_strategy_id   = aws_appconfig_deployment_strategy.alarm_rollback.id
  description              = "Production deployment with alarm-based rollback"

  # Note: alarm-based rollback monitors are configured on the AppConfig Environment
  # resource (aws_appconfig_environment), not on the deployment. The appconfig_monitor
  # IAM role below is created here for use when wiring the monitor to the environment.

  # Deployed once by Terraform on initial setup.
  # Subsequent config updates must be deployed manually (console/CLI) or via a
  # dedicated CI step — as described in spec Section 9 "How to change configuration".
  # Prevents re-triggering a deployment on every `terraform apply`.
  lifecycle {
    ignore_changes = all
  }
}

resource "aws_iam_role" "appconfig_monitor" {
  name = "${var.project_name}-${var.environment}-appconfig-monitor"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "appconfig.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "appconfig_monitor_cloudwatch" {
  name = "cloudwatch-read"
  role = aws_iam_role.appconfig_monitor.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["cloudwatch:DescribeAlarms"]
      Resource = aws_cloudwatch_metric_alarm.api_error_rate.arn
    }]
  })
}

# ── CloudWatch Dashboard ──────────────────────────────────────────────────────

resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "${var.project_name}-${var.environment}"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title   = "API Lambda — Invocations & Errors"
          region  = var.aws_region
          period  = 300
          stat    = "Sum"
          metrics = [
            ["AWS/Lambda", "Invocations", "FunctionName", var.api_lambda_name],
            ["AWS/Lambda", "Errors", "FunctionName", var.api_lambda_name]
          ]
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title   = "API Lambda — P50/P99 Duration"
          region  = var.aws_region
          period  = 300
          stat    = "p99"
          metrics = [
            ["AWS/Lambda", "Duration", "FunctionName", var.api_lambda_name, { stat = "p50", label = "P50" }],
            ["AWS/Lambda", "Duration", "FunctionName", var.api_lambda_name, { stat = "p99", label = "P99" }]
          ]
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title   = "API Gateway — Requests & Errors"
          region  = var.aws_region
          period  = 300
          stat    = "Sum"
          metrics = [
            ["AWS/ApiGateway", "Count", "ApiId", var.api_gateway_id],
            ["AWS/ApiGateway", "5XXError", "ApiId", var.api_gateway_id],
            ["AWS/ApiGateway", "4XXError", "ApiId", var.api_gateway_id]
          ]
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title   = "Ingestion Lambda — Invocations & Errors"
          region  = var.aws_region
          period  = 300
          stat    = "Sum"
          metrics = [
            ["AWS/Lambda", "Invocations", "FunctionName", var.ingestion_lambda_name],
            ["AWS/Lambda", "Errors", "FunctionName", var.ingestion_lambda_name],
            ["AWS/Lambda", "Duration", "FunctionName", var.ingestion_lambda_name]
          ]
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title   = "DynamoDB — Consumed Read Capacity Units"
          region  = var.aws_region
          period  = 300
          stat    = "Sum"
          metrics = [
            ["AWS/DynamoDB", "ConsumedReadCapacityUnits", "TableName", var.courses_table_name],
            ["AWS/DynamoDB", "ConsumedReadCapacityUnits", "TableName", var.applications_table_name],
            ["AWS/DynamoDB", "ConsumedReadCapacityUnits", "TableName", var.users_table_name]
          ]
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title   = "DynamoDB — Consumed Write Capacity Units"
          region  = var.aws_region
          period  = 300
          stat    = "Sum"
          metrics = [
            ["AWS/DynamoDB", "ConsumedWriteCapacityUnits", "TableName", var.courses_table_name],
            ["AWS/DynamoDB", "ConsumedWriteCapacityUnits", "TableName", var.applications_table_name],
            ["AWS/DynamoDB", "ConsumedWriteCapacityUnits", "TableName", var.users_table_name]
          ]
        }
      }
    ]
  })
}
