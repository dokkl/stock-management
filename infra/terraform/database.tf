# 관리형 PostgreSQL. 공개 모드를 끄면 같은 리전의 Lightsail 리소스(컨테이너 서비스)만 접속할 수 있다.
resource "random_password" "db" {
  length  = 32
  special = false
}

resource "aws_lightsail_database" "main" {
  relational_database_name     = "${var.name}-db"
  availability_zone            = var.db_availability_zone
  blueprint_id                 = var.db_blueprint_id
  bundle_id                    = var.db_bundle_id
  master_database_name         = "stock"
  master_username              = "stock"
  master_password              = random_password.db.result
  publicly_accessible          = false
  backup_retention_enabled     = true
  preferred_backup_window      = "18:00-18:30"         # KST 03:00
  preferred_maintenance_window = "sun:19:00-sun:19:30" # KST 월 04:00
  final_snapshot_name          = "${var.name}-db-final"
  skip_final_snapshot          = false

  lifecycle {
    prevent_destroy = true
  }
}
