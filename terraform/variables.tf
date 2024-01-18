variable "hcloud_token" {
  sensitive = true
}

variable "server_config" {
  default = ({
    controller_server_type = "cx21"
    worker_server_type = "cpx21"
    image = "debian-11"
    k8s_controller_instances = ["controller"]
    k8s_worker_instances = ["worker1", "worker2"]
  })
}