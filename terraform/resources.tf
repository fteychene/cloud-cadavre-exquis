###
# Create the ssh key pair in both openstack & ovh api
###
resource "tls_private_key" "private_key" {
  algorithm = "RSA"
  rsa_bits  = 2048
}

# registers private key in ssh-agent
resource null_resource "register_ssh_private_key" {
  triggers = {
    key = base64sha256(tls_private_key.private_key.private_key_pem)
  }

  provisioner "local-exec" {
    command = "ssh-add - <<< '${tls_private_key.private_key.private_key_pem}'"
    environment = {
      KEY = base64encode(tls_private_key.private_key.private_key_pem)
    }
  }
}


# Keypair which will be used on nodes and bastion
resource "openstack_compute_keypair_v2" "keypair" {
  name       = var.name
  public_key = tls_private_key.private_key.public_key_openssh

  depends_on = [null_resource.register_ssh_private_key]
}


###
# Create the VM
###

resource "openstack_compute_instance_v2" "OVH_in_Fire_controller" {
  for_each    = toset(var.server_config.k8s_controller_instances)
  name        = each.key
  provider    = openstack.ovh
  image_name  = var.server_config.image
  flavor_name = var.server_config.controller_server_type
  key_pair    = openstack_compute_keypair_v2.keypair.name
  security_groups = ["default"]
  network {
    name      = "Ext-Net"
  }
  connection {
    type        = "ssh"
    user        = "root"
    private_key = tls_private_key.private_key.private_key_pem
    host        = self.floating_ip
  }

  provisioner "remote-exec" {
    scripts = [
      "./bin/01_install.sh",
      "./bin/02_kubeadm_init.sh"
    ]
  }
}
resource "openstack_compute_instance_v2" "OVH_in_Fire_worker" {
  for_each    = toset(var.server_config.k8s_worker_instances)
  name        = each.key
  provider    = openstack.ovh
  image_name  = var.server_config.image
  flavor_name = var.server_config.worker_server_type
  key_pair    = openstack_compute_keypair_v2.keypair.name
  security_groups = ["default"]
  network {
    name      = "Ext-Net"
  }
}