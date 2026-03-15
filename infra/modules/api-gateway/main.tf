resource "aws_apigatewayv2_api" "main" {
  name          = "${var.project_name}-${var.environment}"
  protocol_type = "HTTP"

  cors_configuration {
    allow_origins = ["*"]
    allow_methods = ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
    allow_headers = ["Content-Type", "Authorization"]
    max_age       = 300
  }
}

# Cognito JWT Authorizer (all routes)
resource "aws_apigatewayv2_authorizer" "cognito" {
  api_id           = aws_apigatewayv2_api.main.id
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]
  name             = "cognito-jwt"

  jwt_configuration {
    audience = [var.cognito_client_id]
    issuer   = "https://${var.cognito_user_pool_endpoint}"
  }
}

# Admin Lambda Authorizer
resource "aws_apigatewayv2_authorizer" "admin" {
  api_id                            = aws_apigatewayv2_api.main.id
  authorizer_type                   = "REQUEST"
  authorizer_uri                    = "arn:aws:apigateway:${var.aws_region}:lambda:path/2015-03-31/functions/${var.admin_authorizer_lambda_arn}/invocations"
  identity_sources                  = ["$request.header.Authorization"]
  name                              = "admin-group-authorizer"
  authorizer_payload_format_version = "2.0"
  enable_simple_responses           = true
}

# Permission for API Gateway to invoke the admin authorizer Lambda
resource "aws_lambda_permission" "api_gateway_admin_authorizer" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = var.admin_authorizer_lambda_arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*"
}

# Integration — API Lambda alias
resource "aws_apigatewayv2_integration" "api_lambda" {
  api_id                 = aws_apigatewayv2_api.main.id
  integration_type       = "AWS_PROXY"
  integration_uri        = var.api_lambda_alias_arn
  payload_format_version = "2.0"
}

# Permission for API Gateway to invoke API Lambda alias
resource "aws_lambda_permission" "api_gateway_api_lambda" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = var.api_lambda_alias_arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*"
}

# Routes — merged locals
locals {
  student_routes = [
    "GET /courses/search",
    "GET /courses/{courseId}",
    "GET /applications",
    "POST /applications/{courseId}",
    "DELETE /applications/{courseId}",
    "GET /profile/completed-courses",
    "PUT /profile/completed-courses",
  ]
  admin_routes = [
    "GET /admin/courses",
    "GET /admin/courses/{courseId}/applicants",
  ]
}

resource "aws_apigatewayv2_route" "student" {
  for_each = toset(local.student_routes)

  api_id             = aws_apigatewayv2_api.main.id
  route_key          = each.key
  target             = "integrations/${aws_apigatewayv2_integration.api_lambda.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito.id
}

# Routes — admin endpoints (admin Lambda authorizer)
resource "aws_apigatewayv2_route" "admin" {
  for_each = toset(local.admin_routes)

  api_id             = aws_apigatewayv2_api.main.id
  route_key          = each.key
  target             = "integrations/${aws_apigatewayv2_integration.api_lambda.id}"
  authorization_type = "CUSTOM"
  authorizer_id      = aws_apigatewayv2_authorizer.admin.id
}

# Stage
resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.main.id
  name        = "$default"
  auto_deploy = true

  default_route_settings {
    throttling_burst_limit = 100
    throttling_rate_limit  = 50
  }
}
