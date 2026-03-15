# infra/modules/github-oidc/variables.tf

variable "github_org" {
  description = "GitHub organization or username (e.g. 'acme-corp')"
  type        = string
}

variable "github_repo" {
  description = "GitHub repository name without org (e.g. 'stanford-courses')"
  type        = string
}

variable "create_oidc_provider" {
  description = "Set to false if the GitHub OIDC provider already exists in this AWS account"
  type        = bool
  default     = true
}
