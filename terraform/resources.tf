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

  # provisioner "local-exec" {
  #   command = "bash -c 'echo \"${tls_private_key.private_key.private_key_pem}\" > ./ovh.pkey'"
  #   environment = {
  #     KEY = base64encode(tls_private_key.private_key.private_key_pem)
  #   }
  # }
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
  name        = "controller"
  provider    = openstack.ovh
  image_name  = "Fedora 38"
  flavor_name = "c3-4"
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
    uuid = "6011fbc9-4cbf-46a4-8452-6890a340b60b"
    name = "Ext-Net"
  }
  connection {
    type        = "ssh"
    user        = "fedora"
    private_key = tls_private_key.private_key.private_key_pem
    host        = self.floating_ip
  }

  provisioner "local-exec" {
    command =  <<-EOF
      export CONTROLLER_IPS=${openstack_compute_instance_v2.OVH_in_Fire_controller.network.0.fixed_ip_v4}
      echo "[controller]" > /tmp/controller_ips
      echo $CONTROLLER_IPS >> /tmp/controller_ips
      EOF
  }
}

resource "openstack_compute_instance_v2" "OVH_in_Fire_worker" {
  name        = "worker1"
  provider    = openstack.ovh
  image_name  = "Fedora 38"
  flavor_name = "r3-16"
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
    uuid = "6011fbc9-4cbf-46a4-8452-6890a340b60b"
    name = "Ext-Net"
  }

  connection {
    type        = "ssh"
    user        = "fedora"
    private_key = tls_private_key.private_key.private_key_pem
    host        = self.floating_ip
  }
  provisioner "local-exec" {
    command =  <<-EOF
      export WORKER_IPS=${openstack_compute_instance_v2.OVH_in_Fire_worker.network.0.fixed_ip_v4}
      echo "[workers]" > /tmp/worker_ips
      echo $WORKER_IPS >> /tmp/worker_ips
      EOF
  }
}


resource "null_resource" "ansible_provisioning" {
 depends_on = [openstack_compute_instance_v2.OVH_in_Fire_controller, openstack_compute_instance_v2.OVH_in_Fire_worker]

 triggers = {
   controller_id = openstack_compute_instance_v2.OVH_in_Fire_controller.id
   worker_id = openstack_compute_instance_v2.OVH_in_Fire_worker.id
 }

 provisioner "remote-exec" {
   connection {
     host = openstack_compute_instance_v2.OVH_in_Fire_controller.network.0.fixed_ip_v4
     user = "fedora"
   }
   inline = [ "echo 'Connected to Controller !'" ]
 }
 provisioner "remote-exec" {
   connection {
     host = openstack_compute_instance_v2.OVH_in_Fire_worker.network.0.fixed_ip_v4
     user = "fedora"
   }
   inline = [ "echo 'Connected to Worker !'" ]
 }

 provisioner "local-exec" {
   command = "ansible-playbook -u fedora -i /tmp/worker_ips playbook.yml -i /tmp/controller_ips --tags 'install'"
 }

}