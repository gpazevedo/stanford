# Outputs added as modules are defined

output "cognito_user_pool_id" {
  value = module.cognito.user_pool_id
}

output "cognito_client_id" {
  value = module.cognito.client_id
}

output "ecr_api_repo_url" {
  value = module.ecr.api_repo_url
}

output "ecr_ingestion_repo_url" {
  value = module.ecr.ingestion_repo_url
}
