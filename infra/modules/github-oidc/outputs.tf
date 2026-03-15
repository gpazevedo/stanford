# infra/modules/github-oidc/outputs.tf

output "role_arn" {
  description = "IAM role ARN for GitHub Actions OIDC — add as AWS_OIDC_ROLE_ARN secret"
  value       = aws_iam_role.ci.arn
}
