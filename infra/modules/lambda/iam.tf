data "aws_iam_policy_document" "lambda_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

# ── API Lambda ────────────────────────────────────────────────────────────────

resource "aws_iam_role" "api" {
  name               = "${var.project_name}-${var.environment}-api-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "api_basic" {
  role       = aws_iam_role.api.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "api_dynamodb" {
  name = "dynamodb"
  role = aws_iam_role.api.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:Query",
        "dynamodb:BatchGetItem",
        "dynamodb:Scan"
      ]
      Resource = [
        var.courses_table_arn,
        var.applications_table_arn,
        "${var.applications_table_arn}/index/*",
        var.users_table_arn
      ]
    }]
  })
}

resource "aws_iam_role_policy" "api_bedrock" {
  name = "bedrock"
  role = aws_iam_role.api.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["bedrock:InvokeModel"]
      Resource = "arn:aws:bedrock:${var.aws_region}::foundation-model/*"
    }]
  })
}

resource "aws_iam_role_policy" "api_s3vectors" {
  name = "s3vectors"
  role = aws_iam_role.api.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3vectors:QueryVectors", "s3vectors:GetVectors"]
      Resource = "arn:aws:s3vectors:${var.aws_region}:${var.aws_account_id}:vectorbucket/*"
    }]
  })
}

resource "aws_iam_role_policy" "api_appconfig" {
  name = "appconfig"
  role = aws_iam_role.api.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "appconfig:GetLatestConfiguration",
        "appconfig:StartConfigurationSession"
      ]
      Resource = "arn:aws:appconfig:${var.aws_region}:${var.aws_account_id}:application/${var.appconfig_application_id}/environment/${var.appconfig_environment_id}/configuration/${var.appconfig_profile_id}"
    }]
  })
}

resource "aws_iam_role_policy" "api_xray" {
  name = "xray"
  role = aws_iam_role.api.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "xray:PutTraceSegments",
        "xray:PutTelemetryRecords"
      ]
      Resource = "*"
    }]
  })
}

# ── Ingestion Lambda ──────────────────────────────────────────────────────────

resource "aws_iam_role" "ingestion" {
  name               = "${var.project_name}-${var.environment}-ingestion-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "ingestion_basic" {
  role       = aws_iam_role.ingestion.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "ingestion_dynamodb" {
  name = "dynamodb"
  role = aws_iam_role.ingestion.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["dynamodb:Scan", "dynamodb:PutItem", "dynamodb:UpdateItem", "dynamodb:DeleteItem"]
        Resource = var.courses_table_arn
      },
      {
        Effect   = "Allow"
        Action   = ["dynamodb:Query", "dynamodb:UpdateItem"]
        Resource = [var.applications_table_arn, "${var.applications_table_arn}/index/*"]
      }
    ]
  })
}

resource "aws_iam_role_policy" "ingestion_bedrock" {
  name = "bedrock"
  role = aws_iam_role.ingestion.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["bedrock:InvokeModel"]
      Resource = "arn:aws:bedrock:${var.aws_region}::foundation-model/*"
    }]
  })
}

resource "aws_iam_role_policy" "ingestion_s3vectors" {
  name = "s3vectors"
  role = aws_iam_role.ingestion.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3vectors:PutVectors", "s3vectors:DeleteVectors"]
      Resource = "arn:aws:s3vectors:${var.aws_region}:${var.aws_account_id}:vectorbucket/*"
    }]
  })
}

resource "aws_iam_role_policy" "ingestion_appconfig" {
  name = "appconfig"
  role = aws_iam_role.ingestion.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "appconfig:GetLatestConfiguration",
        "appconfig:StartConfigurationSession"
      ]
      Resource = "arn:aws:appconfig:${var.aws_region}:${var.aws_account_id}:application/${var.appconfig_application_id}/environment/${var.appconfig_environment_id}/configuration/${var.appconfig_profile_id}"
    }]
  })
}

resource "aws_iam_role_policy" "ingestion_xray" {
  name = "xray"
  role = aws_iam_role.ingestion.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "xray:PutTraceSegments",
        "xray:PutTelemetryRecords"
      ]
      Resource = "*"
    }]
  })
}

resource "aws_iam_role_policy" "ingestion_cloudwatch" {
  name = "cloudwatch"
  role = aws_iam_role.ingestion.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["cloudwatch:PutMetricData"]
      Resource = "*"
    }]
  })
}

# ── Post-Confirmation Lambda ──────────────────────────────────────────────────

resource "aws_iam_role" "post_confirmation" {
  name               = "${var.project_name}-${var.environment}-post-confirmation-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "post_confirmation_basic" {
  role       = aws_iam_role.post_confirmation.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "post_confirmation_cognito" {
  name = "cognito"
  role = aws_iam_role.post_confirmation.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["cognito-idp:AdminAddUserToGroup"]
      Resource = var.user_pool_arn
    }]
  })
}

# ── Admin Authorizer Lambda ───────────────────────────────────────────────────

resource "aws_iam_role" "admin_authorizer" {
  name               = "${var.project_name}-${var.environment}-admin-authorizer-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "admin_authorizer_basic" {
  role       = aws_iam_role.admin_authorizer.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}
