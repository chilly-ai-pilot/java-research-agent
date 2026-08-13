#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
UI_DIR="$ROOT/ui"
VENV="$UI_DIR/.venv"
PORT="${CHAT_UI_PORT:-8501}"
export AGENT_API_URL="${AGENT_API_URL:-http://127.0.0.1:8081}"  # 用 127.0.0.1 避免代理劫持 localhost
export NO_PROXY="${NO_PROXY:-localhost,127.0.0.1,::1}"

# 默认清华镜像（macOS 官方 Python 常因 SSL 根证书未装而连不上 pypi.org）
PYPI_MIRROR="${PYPI_INDEX:-https://pypi.tuna.tsinghua.edu.cn/simple}"
PYPI_HOST="$(echo "$PYPI_MIRROR" | sed -E 's|https?://([^/]+).*|\1|')"

if ! command -v python3 >/dev/null 2>&1; then
  echo "需要 python3（建议 3.10–3.12）。请先安装 Python。" >&2
  exit 1
fi

PY_VER="$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')"
echo "Python ${PY_VER}"

# macOS python.org 安装包：尝试补全系统根证书（可选，失败不阻断）
try_install_macos_certs() {
  [[ "$(uname -s)" == "Darwin" ]] || return 0
  local cert_script
  for cert_script in \
    "/Applications/Python ${PY_VER}/Install Certificates.command" \
    "/Applications/Python 3.13/Install Certificates.command" \
    "/Applications/Python 3.12/Install Certificates.command"; do
    if [[ -f "$cert_script" ]]; then
      echo "运行证书安装：${cert_script}"
      bash "$cert_script" || true
      return 0
    fi
  done
}
try_install_macos_certs

if [[ ! -d "$VENV" ]]; then
  echo "创建虚拟环境 ${VENV} …"
  python3 -m venv "$VENV"
fi

# shellcheck source=/dev/null
source "$VENV/bin/activate"

pip_install() {
  pip install -i "$PYPI_MIRROR" --trusted-host "$PYPI_HOST" "$@"
}

echo "升级 pip（镜像：${PYPI_MIRROR}）…"
pip_install -q --upgrade pip setuptools wheel certifi

export SSL_CERT_FILE="$(python -c 'import certifi; print(certifi.where())')"
export REQUESTS_CA_BUNDLE="$SSL_CERT_FILE"

echo "安装 Chat UI 依赖…"
pip_install -r "$UI_DIR/requirements.txt"

echo ""
echo "Chat UI  → http://localhost:${PORT}"
echo "Agent API → ${AGENT_API_URL}"
echo ""

exec streamlit run "$UI_DIR/streamlit_app.py" \
  --server.port "$PORT" \
  --server.headless true \
  --browser.gatherUsageStats false
