variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (prod, dev)"
  type        = string
  default     = "prod"
}

variable "project_name" {
  description = "Project name prefix for all resources"
  type        = string
  default     = "stanford-courses"
}

variable "github_owner" {
  description = "GitHub repository owner (username or org)"
  type        = string
}

variable "github_repo" {
  description = "GitHub repository name"
  type        = string
}

variable "github_branch" {
  description = "GitHub branch to deploy from"
  type        = string
  default     = "main"
}

variable "github_access_token" {
  description = "GitHub personal access token for Amplify"
  type        = string
  sensitive   = true
}

variable "alarm_email" {
  description = "Email address for CloudWatch alarm notifications"
  type        = string
}
