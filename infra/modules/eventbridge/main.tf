resource "aws_iam_role" "scheduler" {
  name = "${var.project_name}-${var.environment}-eventbridge-scheduler"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "scheduler.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "scheduler_invoke" {
  name = "invoke-ingestion"
  role = aws_iam_role.scheduler.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "lambda:InvokeFunction"
      Resource = var.ingestion_lambda_arn
    }]
  })
}

resource "aws_scheduler_schedule" "ingestion" {
  name       = "${var.project_name}-${var.environment}-ingestion"
  group_name = "default"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression          = var.schedule_expression
  schedule_expression_timezone = "UTC"

  target {
    arn      = var.ingestion_lambda_arn
    role_arn = aws_iam_role.scheduler.arn
  }
}
