output "application_id" {
  value = aws_appconfig_application.main.id
}

output "environment_id" {
  value = aws_appconfig_environment.main.environment_id
}

output "configuration_profile_id" {
  value = aws_appconfig_configuration_profile.main.configuration_profile_id
}

output "initial_version_number" {
  value = tostring(aws_appconfig_hosted_configuration_version.initial.version_number)
}
