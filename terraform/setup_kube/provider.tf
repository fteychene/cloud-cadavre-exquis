# Configure the OpenStack provider hosted by OVHcloud
provider "openstack" {
  auth_url    = "https://auth.cloud.ovh.net/v3/" # Authentication URL
  domain_name = "default" # Domain name - Always at 'default' for OVHcloud
  alias       = "ovh" # An alias
}

provider "ovh" {
  alias              = "ovh"
  endpoint           = "ovh-eu"
  application_key    = var.OS_APPLICATION_KEY
  application_secret = var.OS_APPLICATION_SECRET
  consumer_key       = var.OS_CONSUMER_KEY
}

provider "kubernetes" {
  alias = "kubernetes"
  config_path = "./kubeconfig"
}
