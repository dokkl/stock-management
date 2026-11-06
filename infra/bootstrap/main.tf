# Terraform 원격 상태 저장용 S3 버킷(최초 1회). 이 디렉터리는 로컬 상태를 쓴다.
#   cd infra/bootstrap && terraform init && terraform apply
terraform {
  required_version = ">= 1.10"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.72"
    }
  }
}

variable "aws_account_id" {
  description = "배포 대상 AWS 계정"
  type        = string
  default     = "378040395734"
}

variable "region" {
  type    = string
  default = "ap-northeast-2"
}

provider "aws" {
  region              = var.region
  allowed_account_ids = [var.aws_account_id]
  default_tags {
    tags = { Project = "stock-management", ManagedBy = "terraform" }
  }
}

resource "aws_s3_bucket" "state" {
  bucket = "stock-management-tfstate-${var.aws_account_id}"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

output "state_bucket" {
  value = aws_s3_bucket.state.bucket
}
