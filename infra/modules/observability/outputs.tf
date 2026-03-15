output "alarm_topic_arn" {
  value = aws_sns_topic.alarms.arn
}

output "api_error_alarm_arn" {
  value = aws_cloudwatch_metric_alarm.api_error_rate.arn
}

output "dashboard_name" {
  value = aws_cloudwatch_dashboard.main.dashboard_name
}
