#!/usr/bin/env bash
set -Eeuo pipefail
umask 027

# Installs only the dedicated announcement-board Tomcat instance.
[[ ${EUID} -eq 0 ]] || { echo 'Run with sudo.' >&2; exit 1; }
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
tomcat_version=${TOMCAT_VERSION:-11.0.27}
[[ $tomcat_version =~ ^11\.0\.[0-9]+$ ]] || { echo 'TOMCAT_VERSION must be 11.0.x.' >&2; exit 1; }

# shellcheck disable=SC1091
source /etc/os-release
[[ ${ID:-} == centos && ${VERSION_ID%%.*} == 10 ]] || {
  echo 'This installer targets CentOS Stream 10; review the deployment guide for other systems.' >&2
  exit 1
}

opt_root=/opt/announcement-board
data_root=/var/lib/announcement-board
env_dir=/etc/announcement-board
unit_file=/etc/systemd/system/announcement-board.service
marker=.managed-by-announcement-board
for managed_root in "$opt_root" "$data_root"; do
  if [[ -e $managed_root && ! -f $managed_root/$marker ]]; then
    echo "Refusing to modify existing unmanaged directory: $managed_root" >&2
    exit 1
  fi
done
if [[ -e $unit_file ]] && ! grep -Fq '# Managed by announcement-board deployment scripts.' "$unit_file"; then
  echo "Refusing to replace an unmanaged unit: $unit_file" >&2
  exit 1
fi

dnf install -y java-21-openjdk-headless curl tar gzip shadow-utils coreutils unzip util-linux
exec 9>/run/lock/announcement-board-deploy.lock
flock --nonblock 9 || { echo 'Another announcement-board installation or deployment is running.' >&2; exit 1; }
[[ -x /usr/lib/jvm/jre-21-openjdk/bin/java ]] || { echo 'Java 21 runtime path was not found.' >&2; exit 1; }
if ! getent group tomcat >/dev/null; then groupadd --system tomcat; fi
if ! id tomcat >/dev/null 2>&1; then
  useradd --system --gid tomcat --home-dir "$data_root" --no-create-home --shell /sbin/nologin tomcat
fi

install -d -m 0755 -o root -g root "$opt_root"
install -d -m 0750 -o root -g tomcat "$data_root" "$env_dir"
touch "$opt_root/$marker" "$data_root/$marker"
tomcat_home="$opt_root/apache-tomcat-$tomcat_version"
archive_name="apache-tomcat-$tomcat_version.tar.gz"
if [[ -L $tomcat_home || ( -e $tomcat_home && ! -d $tomcat_home ) ]]; then
  echo "Unexpected Tomcat installation path: $tomcat_home" >&2
  exit 1
fi
if [[ -e $opt_root/tomcat-current && ! -L $opt_root/tomcat-current ]]; then
  echo 'tomcat-current must be a managed symbolic link.' >&2
  exit 1
fi

if [[ ! -d $tomcat_home ]]; then
  download_dir=$(mktemp -d /tmp/announcement-tomcat.XXXXXXXX)
  cleanup() {
    rm -f -- "$download_dir/$archive_name" "$download_dir/$archive_name.sha512"
    rmdir -- "$download_dir" 2>/dev/null || true
  }
  trap cleanup EXIT
  primary="https://dlcdn.apache.org/tomcat/tomcat-11/v$tomcat_version/bin"
  fallback="https://archive.apache.org/dist/tomcat/tomcat-11/v$tomcat_version/bin"
  downloaded=false
  for base_url in "$primary" "$fallback"; do
    if curl --fail --location --retry 3 --output "$download_dir/$archive_name" "$base_url/$archive_name" \
      && curl --fail --location --retry 3 --output "$download_dir/$archive_name.sha512" "$base_url/$archive_name.sha512"; then
      downloaded=true
      break
    fi
  done
  [[ $downloaded == true ]] || { echo 'Could not download Tomcat and its checksum.' >&2; exit 1; }
  (cd -- "$download_dir" && sha512sum --check "$archive_name.sha512")
  tar --extract --gzip --file "$download_dir/$archive_name" --directory "$opt_root" --no-same-owner
  [[ -f $tomcat_home/bin/catalina.sh ]] || { echo 'Unexpected Tomcat archive layout.' >&2; exit 1; }
fi

# Extraction is affected by umask. Set explicit read/traverse permissions so the
# unprivileged tomcat user can execute this root-owned, otherwise read-only home.
# Real instance settings and secrets live separately under CATALINA_BASE / /etc.
chown -R root:root "$tomcat_home"
find "$tomcat_home" -type d -exec chmod 0755 {} +
find "$tomcat_home" -type f -exec chmod 0644 {} +
find "$tomcat_home/bin" -maxdepth 1 -type f -name '*.sh' -exec chmod 0755 {} +
[[ -x $tomcat_home/bin/catalina.sh ]] || { echo 'Tomcat startup script is not executable.' >&2; exit 1; }

# A version switch takes effect on the next start; stop this dedicated instance first.
if systemctl is-active --quiet announcement-board.service; then
  systemctl stop announcement-board.service
fi
ln -sfn -- "$tomcat_home" "$opt_root/tomcat-current"

catalina_base="$data_root/tomcat"
install -d -m 0750 -o root -g tomcat "$catalina_base" "$catalina_base/conf" "$catalina_base/webapps" "$data_root/deploy"
for writable_dir in logs temp work; do
  install -d -m 0750 -o tomcat -g tomcat "$catalina_base/$writable_dir"
done
install -d -m 0750 -o tomcat -g tomcat "$data_root/uploads"
if [[ ! -f $catalina_base/conf/server.xml ]]; then
  cp -R -- "$tomcat_home/conf/." "$catalina_base/conf/"
  sed -i 's/<Server port="8005"/<Server port="-1"/; s/unpackWARs="true"/unpackWARs="false"/; s/autoDeploy="true"/autoDeploy="false"/' "$catalina_base/conf/server.xml"
  context_file="$catalina_base/conf/context.xml"
  if grep -Eq '<Resources([[:space:]]|/|>)' "$context_file"; then
    echo 'Existing Resources configuration was preserved; review ExtractingRoot in docs/centos-deployment.md before deployment.' >&2
  else
    [[ $(grep -Ec '^[[:space:]]*</Context>[[:space:]]*$' "$context_file") -eq 1 ]] || {
      echo 'Unexpected context.xml layout; configure ExtractingRoot manually before deployment.' >&2
      exit 1
    }
    sed -i '/^[[:space:]]*<\/Context>[[:space:]]*$/i\    <Resources className="org.apache.catalina.webresources.ExtractingRoot" />' "$context_file"
  fi
  chown -R root:tomcat "$catalina_base/conf"
  find "$catalina_base/conf" -type d -exec chmod 0750 {} +
  find "$catalina_base/conf" -type f -exec chmod 0640 {} +
elif ! grep -Fq 'org.apache.catalina.webresources.ExtractingRoot' "$catalina_base/conf/context.xml"; then
  echo 'Existing Tomcat configuration was preserved; review ExtractingRoot in docs/centos-deployment.md to avoid slow packed-WAR startup.' >&2
fi
if [[ ! -f $env_dir/announcement.env ]]; then
  install -m 0640 -o root -g tomcat "$script_dir/announcement.env.example" "$env_dir/announcement.env"
fi
install -m 0644 -o root -g root "$script_dir/announcement-board.service" "$unit_file"
systemctl daemon-reload
systemctl enable announcement-board.service
echo 'Tomcat installed. Configure MySQL and edit /etc/announcement-board/announcement.env before deploying.'
echo 'Service has not been started. Next: sudo bash deploy/deploy-war.sh target/announcements.war'
