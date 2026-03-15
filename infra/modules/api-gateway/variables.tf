variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "cognito_user_pool_endpoint" {
  type = string
}

variable "cognito_client_id" {
  type = string
}

variable "api_lambda_alias_arn" {
  type = string
}

variable "admin_authorizer_lambda_arn" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "aws_account_id" {
  type = string
}
