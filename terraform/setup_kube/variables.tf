variable "name" {
  description = "Cadavre exquis"
  default     = "cadavre-exquis"
  type = string
}

variable "server_config" {
  default = ({
    controller_server_type = "c3-4"
    worker_server_type = "r3-16"
    image = "Fedora 38"
    k8s_controller_instances = ["controller"]
    k8s_worker_instances = ["worker1"]
  })
}

variable "OS_APPLICATION_KEY" {type = string}
variable "OS_APPLICATION_SECRET" {type = string}
variable "OS_CONSUMER_KEY" {type = string}

variable "ssh_public_keys" {
  description = "List of SSH public keys"
  type        = list(string)
  default     = [""]
}
