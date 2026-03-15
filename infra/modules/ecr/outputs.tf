output "repo_urls" {
  value = { for k, v in aws_ecr_repository.repos : k => v.repository_url }
}

output "api_repo_url" {
  value = aws_ecr_repository.repos["api"].repository_url
}

output "ingestion_repo_url" {
  value = aws_ecr_repository.repos["ingestion"].repository_url
}

output "post_confirmation_repo_url" {
  value = aws_ecr_repository.repos["post-confirmation"].repository_url
}
