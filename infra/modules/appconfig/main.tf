resource "aws_appconfig_application" "main" {
  name        = "${var.project_name}-${var.environment}"
  description = "Stanford Course Finder configuration"
}

resource "aws_appconfig_environment" "main" {
  name           = var.environment
  application_id = aws_appconfig_application.main.id
}

resource "aws_appconfig_configuration_profile" "main" {
  application_id = aws_appconfig_application.main.id
  name           = "app-config"
  location_uri   = "hosted"
}

resource "aws_appconfig_hosted_configuration_version" "initial" {
  application_id           = aws_appconfig_application.main.id
  configuration_profile_id = aws_appconfig_configuration_profile.main.configuration_profile_id
  content_type             = "application/json"

  content = jsonencode({
    embeddingModelId        = var.embedding_model_id
    generativeModelId       = var.generative_model_id
    maxSearchResults        = 10
    enableSemanticReranking = false
    newPrereqEnforcement    = true
  })
}
