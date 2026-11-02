#!/usr/bin/env bash
set -Eeuo pipefail
umask 027

[[ ${EUID} -eq 0 ]] || { echo 'Run with sudo.' >&2; exit 1; }
[[ $# -eq 1 ]] || { echo 'Usage: sudo bash deploy/deploy-war.sh /absolute/path/announcements.war' >&2; exit 1; }
health_timeout=${HEALTH_TIMEOUT_SECONDS:-180}
[[ $health_timeout =~ ^[1-9][0-9]{0,2}$ && $health_timeout -ge 30 && $health_timeout -le 900 ]] || {
  echo 'HEALTH_TIMEOUT_SECONDS must be an integer between 30 and 900.' >&2
  exit 1
}
exec 9>/run/lock/announcement-board-deploy.lock
flock --nonblock 9 || { echo 'Another announcement-board installation or deployment is running.' >&2; exit 1; }
source_war=$(realpath -- "$1")
[[ -f $source_war && $source_war == *.war ]] || { echo 'Source must be an existing WAR file.' >&2; exit 1; }
data_root=/var/lib/announcement-board
[[ -f $data_root/.managed-by-announcement-board && ! -L $data_root ]] || {
  echo 'Managed Tomcat base is missing or is a symbolic link; run install-tomcat.sh first.' >&2
  exit 1
}
for controlled_dir in "$data_root/deploy" "$data_root/tomcat" "$data_root/tomcat/webapps"; do
  resolved_dir=$(realpath -- "$controlled_dir")
  [[ $resolved_dir == "$controlled_dir" && -d $controlled_dir ]] || {
    echo "Unexpected deployment path: $controlled_dir" >&2
    exit 1
  }
done
environment_file=/etc/announcement-board/announcement.env
[[ -f $environment_file ]] || { echo 'Environment file is missing.' >&2; exit 1; }
if ! grep -Eq '^DB_PASSWORD=.+$' "$environment_file" \
  || grep -Eq '^DB_PASSWORD=("?CHANGE_ME_REQUIRED"?|""|[[:space:]]*)$' "$environment_file"; then
  echo 'Set a real DB_PASSWORD in /etc/announcement-board/announcement.env first.' >&2
  exit 1
fi
unzip -tqq "$source_war"
if ! unzip -Z1 "$source_war" | grep -E '^WEB-INF/classes/.+\.class$' >/dev/null; then
  echo 'This archive does not contain application classes in WEB-INF/classes.' >&2
  exit 1
fi

target_war="$data_root/tomcat/webapps/ROOT.war"
staged_war="$data_root/deploy/ROOT.war.new"
install -m 0640 -o root -g tomcat "$source_war" "$staged_war"
backup_dir=/var/backups/announcement-board
install -d -m 0700 -o root -g root "$backup_dir"
if [[ -f $target_war ]]; then
  backup_path="$backup_dir/$(date -u +%Y%m%dT%H%M%S)-$$-ROOT.war"
  install -m 0600 -o root -g root "$target_war" "$backup_path"
  echo "Previous WAR saved to $backup_path"
fi

systemctl stop announcement-board.service
mv --force -- "$staged_war" "$target_war"
if command -v restorecon >/dev/null; then
  restorecon -R "$data_root/tomcat/webapps" "$data_root/uploads"
fi
systemctl start announcement-board.service
health_deadline=$((SECONDS + health_timeout))
while ((SECONDS < health_deadline)); do
  if health_body=$(curl --fail --silent --max-time 2 http://127.0.0.1:8080/actuator/health) \
    && grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<< "$health_body"; then
    echo 'Deployment healthy: http://SERVER_IP:8080/announcements'
    exit 0
  fi
  sleep 2
done
echo "The health check did not reach UP within approximately $health_timeout seconds. Check: sudo journalctl -u announcement-board -n 100 --no-pager" >&2
echo 'The installed WAR was retained for investigation; no database rollback was attempted.' >&2
exit 1
