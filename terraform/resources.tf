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
    command = "bash -c 'echo \"${tls_private_key.private_key.private_key_pem}\" > ./ovh.pkey'"
    environment = {
      KEY = base64encode(tls_private_key.private_key.private_key_pem)
    }
  }
}

# Keypair which will be used on nodes and bastion
resource "openstack_compute_keypair_v2" "keypair" {
  name       = var.name
  public_key = tls_private_key.private_key.public_key_openssh
  provider   = openstack.ovh
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
  user_data = <<-EOF
    #!/bin/bash
    echo "${join("\n", var.ssh_public_keys)}" > /tmp/authorized_keys
    sudo mv /tmp/authorized_keys /home/fedora/.ssh/authorized_keys
    sudo chown fedora:fedora /home/fedora/.ssh/authorized_keys
    sudo chmod 600 /home/fedora/.ssh/authorized_keys
    echo "###" > /tmp/authorized_keys
  EOF
  security_groups = ["default"]
  network {
    name      = "Ext-Net"
  }
  connection {
    type        = "ssh"
    user        = "fedora"
    private_key = tls_private_key.private_key.private_key_pem
    host        = self.floating_ip
  }
}

resource "openstack_compute_instance_v2" "OVH_in_Fire_worker" {
  for_each    = toset(var.server_config.k8s_worker_instances)
  name        = each.key
  provider    = openstack.ovh
  image_name  = var.server_config.image
  flavor_name = var.server_config.worker_server_type
  key_pair    = openstack_compute_keypair_v2.keypair.name
  user_data = <<-EOF
    #!/bin/bash
    echo "${join("\n", var.ssh_public_keys)}" > /tmp/authorized_keys
    sudo mv /tmp/authorized_keys /home/fedora/.ssh/authorized_keys
    sudo chown fedora:fedora /home/fedora/.ssh/authorized_keys
    sudo chmod 600 /home/fedora/.ssh/authorized_keys
    echo "###" > /tmp/authorized_keys
  EOF
  security_groups = ["default"]
  network {
    name      = "Ext-Net"
  }

  connection {
    type        = "ssh"
    user        = "fedora"
    private_key = tls_private_key.private_key.private_key_pem
    host        = self.floating_ip
  }
}

data "openstack_networking_network_v2" "ext_network" {
  name = "public"
}

data "openstack_networking_subnet_ids_v2" "ext_subnets" {
  network_id = data.openstack_networking_network_v2.ext_network.id
}

resource "openstack_networking_floatingip_v2" "myip" {
  pool       = data.openstack_networking_network_v2.ext_network.name
  subnet_ids = data.openstack_networking_subnet_ids_v2.ext_subnets.ids
}
resource "openstack_compute_floatingip_associate_v2" "controler_ip" {
  for_each = openstack_compute_instance_v2.OVH_in_Fire_controller
  instance_id = each.value.id
  floating_ip = openstack_networking_floatingip_v2.myip.address
}

resource "openstack_compute_floatingip_associate_v2" "worker_ip" {
  for_each = openstack_compute_instance_v2.OVH_in_Fire_worker
  instance_id = each.value.id
  floating_ip = openstack_networking_floatingip_v2.myip.address
}

resource "null_resource" "ansible_provisioning" {
 depends_on = [openstack_compute_instance_v2.OVH_in_Fire_controller, openstack_compute_instance_v2.OVH_in_Fire_worker]

 triggers = {
   controller_ids = join(",", [for instance in openstack_compute_instance_v2.OVH_in_Fire_controller: instance.id])
   worker_ids = join(",", [for instance in openstack_compute_instance_v2.OVH_in_Fire_worker: instance.id])
 }

 provisioner "local-exec" {
   command = <<-EOT
     export MASTER_IP="${join(",", openstack_compute_floatingip_associate_v2.controler_ip.*.floating_ip)}"
     export WORKER_IPS="${join(",", openstack_compute_floatingip_associate_v2.worker_ip.*.floating_ip)}"
     ansible-playbook playbook.yml
   EOT
 }
}