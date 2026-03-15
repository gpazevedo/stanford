output "app_id" {
  value = aws_amplify_app.frontend.id
}

output "default_domain" {
  value = aws_amplify_app.frontend.default_domain
}

output "frontend_url" {
  value = "https://${var.github_branch}.${aws_amplify_app.frontend.default_domain}"
}
