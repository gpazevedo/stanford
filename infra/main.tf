terraform {
  required_version = "~> 1.14"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.36"
    }
    archive = {
      source = "hashicorp/archive"
    }
  }

  # bucket supplied at init time via init.sh (or -backend-config flag)
  backend "s3" {
    key          = "prod/terraform.tfstate"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

module "cognito" {
  source       = "./modules/cognito"
  project_name = var.project_name
  environment  = var.environment

  post_confirmation_lambda_arn = module.lambda.post_confirmation_function_arn
}

module "dynamodb" {
  source       = "./modules/dynamodb"
  project_name = var.project_name
  environment  = var.environment
}

module "ecr" {
  source       = "./modules/ecr"
  project_name = var.project_name
  environment  = var.environment
}

module "appconfig" {
  source       = "./modules/appconfig"
  project_name = var.project_name
  environment  = var.environment
}

module "s3vectors" {
  source       = "./modules/s3vectors"
  project_name = var.project_name
  environment  = var.environment
}

data "aws_caller_identity" "current" {}

module "lambda" {
  source         = "./modules/lambda"
  project_name   = var.project_name
  environment    = var.environment
  aws_region     = var.aws_region
  aws_account_id = data.aws_caller_identity.current.account_id

  courses_table_arn      = module.dynamodb.courses_table_arn
  applications_table_arn = module.dynamodb.applications_table_arn
  users_table_arn        = module.dynamodb.users_table_arn
  user_pool_arn          = module.cognito.user_pool_arn
  user_pool_id           = module.cognito.user_pool_id

  appconfig_application_id = module.appconfig.application_id
  appconfig_environment_id = module.appconfig.environment_id
  appconfig_profile_id     = module.appconfig.configuration_profile_id

  api_image_uri               = "${module.ecr.api_repo_url}:placeholder"
  ingestion_image_uri         = "${module.ecr.ingestion_repo_url}:placeholder"
  post_confirmation_image_uri = "${module.ecr.post_confirmation_repo_url}:placeholder"
}

module "api_gateway" {
  source         = "./modules/api-gateway"
  project_name   = var.project_name
  environment    = var.environment
  aws_region     = var.aws_region
  aws_account_id = data.aws_caller_identity.current.account_id

  cognito_user_pool_endpoint  = module.cognito.user_pool_endpoint
  cognito_client_id           = module.cognito.client_id
  api_lambda_alias_arn        = module.lambda.api_alias_arn
  admin_authorizer_lambda_arn = module.lambda.admin_authorizer_arn
}

module "eventbridge" {
  source       = "./modules/eventbridge"
  project_name = var.project_name
  environment  = var.environment

  ingestion_lambda_arn = module.lambda.ingestion_function_arn
}

module "amplify" {
  source       = "./modules/amplify"
  project_name = var.project_name
  environment  = var.environment

  github_owner        = var.github_owner
  github_repo         = var.github_repo
  github_branch       = var.github_branch
  github_access_token = var.github_access_token

  api_endpoint         = module.api_gateway.api_endpoint
  cognito_user_pool_id = module.cognito.user_pool_id
  cognito_client_id    = module.cognito.client_id
}

module "observability" {
  source       = "./modules/observability"
  project_name = var.project_name
  environment  = var.environment
  aws_region   = var.aws_region

  alarm_email           = var.alarm_email
  api_lambda_name       = module.lambda.api_function_name
  ingestion_lambda_name = module.lambda.ingestion_function_name
  api_gateway_id        = module.api_gateway.api_id

  appconfig_application_id = module.appconfig.application_id
  appconfig_environment_id = module.appconfig.environment_id
  appconfig_profile_id     = module.appconfig.configuration_profile_id
  appconfig_version_number = module.appconfig.initial_version_number

  courses_table_name      = module.dynamodb.courses_table_name
  applications_table_name = module.dynamodb.applications_table_name
  users_table_name        = module.dynamodb.users_table_name
}

module "github_oidc" {
  source = "./modules/github-oidc"

  github_org           = var.github_org
  github_repo          = var.github_repo
  create_oidc_provider = var.create_oidc_provider
}
