#!/bin/sh
# Da eseguire come root dentro un LXC Debian, nella cartella che contiene calsnap_server.py.
set -e
apt-get update && apt-get install -y python3
timedatectl set-timezone Europe/Rome 2>/dev/null || ln -sf /usr/share/zoneinfo/Europe/Rome /etc/localtime

id calsnap >/dev/null 2>&1 || useradd --system --no-create-home calsnap
install -d -o calsnap -m 700 /var/lib/calsnap
install -m 755 calsnap_server.py /usr/local/bin/calsnap_server.py

if [ ! -f /etc/calsnap.env ]; then
  cat > /etc/calsnap.env <<EOF
CALSNAP_UPLOAD_KEY=$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')
CALSNAP_GUEST_TOKEN=$(python3 -c 'import secrets; print(secrets.token_urlsafe(24))')
EOF
  chmod 600 /etc/calsnap.env
fi

cat > /etc/systemd/system/calsnap.service <<EOF
[Unit]
Description=CalSnap
After=network.target

[Service]
User=calsnap
EnvironmentFile=/etc/calsnap.env
ExecStart=/usr/bin/python3 /usr/local/bin/calsnap_server.py
Restart=always

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now calsnap
systemctl restart calsnap

. /etc/calsnap.env
echo
echo "Chiave upload (da inserire nell'app): $CALSNAP_UPLOAD_KEY"
echo "Link ospite: https://<tuo-dominio>/g/$CALSNAP_GUEST_TOKEN/"
