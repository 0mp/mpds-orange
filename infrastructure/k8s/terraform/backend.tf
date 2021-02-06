terraform {
  backend "gcs" {
    bucket  = "mpds-task-2"
    prefix  = "terraform/state"
  }
}
