variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "github_owner" {
  type = string
}

variable "github_repo" {
  type = string
}

variable "github_branch" {
  type = string
}

variable "github_access_token" {
  type      = string
  sensitive = true
}

variable "api_endpoint" {
  type = string
}

variable "cognito_user_pool_id" {
  type = string
}

variable "cognito_client_id" {
  type = string
}
