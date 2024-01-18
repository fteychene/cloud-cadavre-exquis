resource "tls_private_key" "generic-ssh-key" {
  algorithm = "RSA"
  rsa_bits  = 4096
  provisioner "local-exec" {
    command = <<EOF
      cat <<< "${tls_private_key.generic-ssh-key.private_key_openssh}" > .ssh/id_rsa.key
      cat <<<  "${tls_private_key.generic-ssh-key.public_key_openssh}" > .ssh/id_rsa.key
      chmod 400 .ssh/id_rsa.key
      chmod 400 .ssh/id_rsa.key
    EOF
    }
}

resource "hcloud_server" "controller" {
  for_each    = toset(var.server_config.k8s_controller_instances)
  name        = each.key
  server_type = var.cloud_server_meta_config.server_type.controller
  image       = var.cloud_server_meta_config.image
  location    = "nbg1"
  ssh_keys    = [hcloud_ssh_key.primary-ssh-key.name]
  connection {
    type        = "ssh"
    user        = "root"
    private_key = tls_private_key.generic-ssh-key.private_key_openssh
    host        = self.ipv4_address
  }
  provisioner "remote-exec" {
    scripts = [
      "./bin/01_install.sh",
      "./bin/02_kubeadm_init.sh"
    ]
  }
  provisioner "local-exec" {
    command = <<EOF
      rm -rvf ./bin/03_kubeadm_join.sh
      echo "echo 1 > /proc/sys/net/ipv4/ip_forward" > ./bin/03_kubeadm_join.sh
      ssh root@${self.ipv4_address} -o StrictHostKeyChecking=no -i .ssh/id_rsa.key "kubeadm token create --print-join-command" >> ./bin/03_kubeadm_join.sh
    EOF
  }
}

resource "hcloud_server" "worker" {
  for_each    = toset(var.server_config.worker_instances)
  name        = each.key
  server_type = var.server_config.worker_server_type
  image       = var.server_config.image
  location    = "fsn1"
  ssh_keys    = [hcloud_ssh_key.primary-ssh-key.name]
  depends_on = [
      hcloud_server.controller
  ]

  connection {
    type        = "ssh"
    user        = "root"
    private_key = tls_private_key.generic-ssh-key.private_key_openssh
    host        = self.ipv4_address
  }

  provisioner "remote-exec" {
    scripts = [
      "./bin/01_install.sh",
      "./bin/03_kubeadm_join.sh"
    ]
  }
}
