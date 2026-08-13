#!/usr/bin/env bash
# 检查 Research Agent 全栈：Gateway(8080) → Java Agent(8081) → Chat UI(8501)
set -uo pipefail

AGENT_URL="${AGENT_URL:-http://127.0.0.1:8081}"
GATEWAY_URL="${GATEWAY_URL:-http://127.0.0.1:8080}"
CURL="curl --noproxy '*'"

check_port() {
  local port="$1" name="$2"
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "✓ ${name} 端口 ${port} 正在监听"
    return 0
  fi
  echo "✗ ${name} 端口 ${port} 未启动"
  return 1
}

http_code() {
  $CURL -s -m 5 -o /dev/null -w "%{http_code}" "$1" 2>/dev/null || echo "000"
}

echo "========== 1. 端口 =========="
gw_up=0; agent_up=0; ui_up=0
check_port 8080 "Go LLM Gateway" && gw_up=1 || true
check_port 8081 "Java Agent" && agent_up=1 || true
check_port 8501 "Streamlit Chat UI" && ui_up=1 || true

echo ""
echo "========== 2. Gateway (${GATEWAY_URL}) =========="
if [[ "$gw_up" -eq 1 ]]; then
  code="$(http_code "${GATEWAY_URL}/v1/models")"
  echo "GET /v1/models → HTTP ${code}"
  if [[ "$code" == "502" ]]; then
    echo "  ⚠ 502：Gateway 上游 LLM Provider 不可用，或 HTTP/2 配置问题"
  elif [[ "$code" == "000" ]]; then
    echo "  ⚠ 连接失败"
  fi
else
  echo "跳过（Gateway 未启动）"
  echo "  提示：先启动 go-llm-gateway"
fi

echo ""
echo "========== 3. Java Agent (${AGENT_URL}) =========="
if [[ "$agent_up" -eq 1 ]]; then
  health="$(http_code "${AGENT_URL}/actuator/health")"
  echo "GET /actuator/health → HTTP ${health}"
  if [[ "$health" == "200" ]]; then
    $CURL -s -m 5 "${AGENT_URL}/actuator/health" && echo ""
  fi
  echo "POST /api/agent/chat（非流式，超时 30s）…"
  chat_resp="$($CURL -s -m 30 -w "\n__HTTP__%{http_code}" -X POST "${AGENT_URL}/api/agent/chat" \
    -H 'Content-Type: application/json' \
    -d '{"message":"hi","stream":false}' 2>/dev/null || echo "__HTTP__000")"
  chat_code="${chat_resp##*__HTTP__}"
  chat_body="${chat_resp%__HTTP__*}"
  echo "→ HTTP ${chat_code}"
  echo "${chat_body}" | head -c 400
  echo ""
  if [[ "$chat_code" == "502" ]]; then
    echo "  ⚠ Agent 返回 502：通常是 Agent 调 Gateway(8080) 失败"
    echo "    1) 确认 Gateway 已启动且 /v1/models 不是 502"
    echo "    2) 确认 application.yml 里 spring.ai.openai.base-url 指向 ${GATEWAY_URL}"
  fi
else
  echo "跳过（Java Agent 未启动）"
  echo "  启动：source scripts/use-java21.sh && mvn spring-boot:run -Dspring-boot.run.profiles=mcp"
fi

echo ""
echo "========== 4. Streamlit 应连哪个？ =========="
echo "Chat UI 只应连 Java Agent：${AGENT_URL}/api/agent/chat"
echo "不要连 Gateway ${GATEWAY_URL}（没有 /api/agent/chat 接口）"
echo ""
echo "启动 Chat UI：./scripts/run-chat-ui.sh"
