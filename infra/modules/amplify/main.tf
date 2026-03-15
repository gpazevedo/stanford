resource "aws_amplify_app" "frontend" {
  name         = "${var.project_name}-${var.environment}"
  repository   = "https://github.com/${var.github_owner}/${var.github_repo}"
  access_token = var.github_access_token

  build_spec = <<-EOT
    version: 1
    frontend:
      phases:
        preBuild:
          commands:
            - cd frontend
            - npm ci
        build:
          commands:
            - npm run build
      artifacts:
        baseDirectory: frontend/out
        files:
          - '**/*'
      cache:
        paths:
          - frontend/node_modules/**/*
  EOT

  environment_variables = {
    NEXT_PUBLIC_API_URL           = var.api_endpoint
    NEXT_PUBLIC_COGNITO_USER_POOL = var.cognito_user_pool_id
    NEXT_PUBLIC_COGNITO_CLIENT_ID = var.cognito_client_id
    NEXT_PUBLIC_AWS_REGION        = "us-east-1"
  }
}

resource "aws_amplify_branch" "main" {
  app_id      = aws_amplify_app.frontend.id
  branch_name = var.github_branch

  framework = "Next.js - SSG"
  stage     = "PRODUCTION"

  enable_auto_build = true
}
