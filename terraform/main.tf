module "Install_instance_and_kube" {
  source = "./setup_kube"
  OS_APPLICATION_KEY = var.OS_APPLICATION_KEY
  OS_APPLICATION_SECRET = var.OS_APPLICATION_SECRET
  OS_CONSUMER_KEY = var.OS_CONSUMER_KEY
}