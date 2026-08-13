"""
Research Agent 聊天界面（Streamlit）。
对接 Java Agent：POST {AGENT_API_URL}/api/agent/chat（默认 8081，不是 Gateway 8080）。
"""

from __future__ import annotations

import json
import os
from typing import Generator, Iterator

import httpx
import streamlit as st

DEFAULT_API_BASE = os.environ.get("AGENT_API_URL", "http://127.0.0.1:8081").rstrip("/")


def http_client(**kwargs) -> httpx.Client:
    """访问本机 Agent/Gateway 时不读 HTTP_PROXY，避免代理对 localhost 返回 502。"""
    return httpx.Client(trust_env=False, **kwargs)

TOOL_LABELS = {
    "search_knowledge": "检索知识库",
    "generate_answer": "生成带引用回答",
    "web_search": "上网搜索",
    "createFlashcard": "创建复习卡片",
    "listCards": "查看已有卡片",
}

STEP_STATUS = {
    "search_knowledge": "正在检索知识库",
    "generate_answer": "正在生成回答",
    "web_search": "正在上网搜索",
    "createFlashcard": "正在创建复习卡片",
    "listCards": "正在读取卡片列表",
}


def tool_label(tool: str | None) -> str:
    if not tool:
        return "处理中"
    return TOOL_LABELS.get(tool, tool)


def step_status(tool: str | None) -> str:
    if not tool:
        return "正在思考"
    return STEP_STATUS.get(tool, f"正在{tool_label(tool)}")


def init_session() -> None:
    if "messages" not in st.session_state:
        st.session_state.messages = []
    if "session_id" not in st.session_state:
        st.session_state.session_id = None
    if "stream" not in st.session_state:
        st.session_state.stream = True
    if "api_base" not in st.session_state:
        st.session_state.api_base = DEFAULT_API_BASE
    # 旧会话若误填 Gateway(8080)，自动改回 Java Agent(8081)
    base = st.session_state.api_base.rstrip("/")
    if base.endswith(":8080") or base.endswith(":8080/v1"):
        st.session_state.api_base = DEFAULT_API_BASE


def is_gateway_url(url: str) -> bool:
    normalized = url.rstrip("/")
    return normalized.endswith(":8080") or "/8080/" in normalized


def chat_url() -> str:
    return f"{st.session_state.api_base.rstrip('/')}/api/agent/chat"


def format_http_error(exc: httpx.HTTPError) -> str:
    base = st.session_state.api_base
    if isinstance(exc, httpx.ConnectError):
        return (
            f"**连接被拒绝** — `{base}` 无服务在监听。\n\n"
            "请先启动 Java Agent：\n"
            "`source scripts/use-java21.sh && mvn spring-boot:run -Dspring-boot.run.profiles=mcp`"
        )
    if isinstance(exc, httpx.HTTPStatusError):
        code = exc.response.status_code
        body = (exc.response.text or "").strip()[:600]
        hints = ""
        if code == 502:
            hints = (
                "\n\n**502 常见原因：**\n"
                "1. 系统开了 **HTTP 代理**（Clash/VPN 等），把 localhost 请求转发出去了"
                " — 侧边栏改用 `http://127.0.0.1:8081`，或关闭代理\n"
                "2. 地址填成了 Gateway（8080）— 应填 **Java Agent（8081）**\n"
                "3. Agent 已起但 Gateway(8080) 不可用\n"
                "4. 终端：`curl --noproxy '*' http://127.0.0.1:8081/actuator/health` 对比浏览器"
            )
        elif code in (404, 405):
            hints = (
                "\n\n该地址可能没有 `/api/agent/chat`。"
                "8080 是 Gateway，请改用 **8081（Java Agent）**。"
            )
        return f"**HTTP {code}** — `{chat_url()}`\n\n```\n{body}\n```{hints}"
    return f"`{base}` — {exc}"


def probe_agent() -> None:
    base = st.session_state.api_base.rstrip("/")
    if is_gateway_url(base):
        st.error("当前地址是 Gateway（8080），请改为 Java Agent：`http://127.0.0.1:8081`")
        return
    health_url = f"{base}/actuator/health"
    try:
        with http_client(timeout=8.0) as client:
            health = client.get(health_url)
            st.write(f"健康检查 `{health_url}` → **HTTP {health.status_code}**")
            if health.status_code == 200:
                st.success("Java Agent 可达")
            else:
                st.warning(health.text[:300])
    except httpx.HTTPError as exc:
        st.markdown(format_http_error(exc))


def parse_sse_blocks(buffer: str) -> tuple[list[tuple[str, str]], str]:
    events: list[tuple[str, str]] = []
    parts = buffer.split("\n\n")
    rest = parts.pop() if parts else ""
    for block in parts:
        if not block.strip():
            continue
        event = "message"
        data_lines: list[str] = []
        for line in block.split("\n"):
            if line.startswith("event:"):
                event = line[6:].strip()
            elif line.startswith("data:"):
                data_lines.append(line[5:].lstrip())
        if data_lines or event != "message":
            events.append((event, "\n".join(data_lines)))
    return events, rest


def iter_sse_events(response: httpx.Response) -> Iterator[tuple[str, str]]:
    buffer = ""
    for chunk in response.iter_text():
        if not chunk:
            continue
        buffer += chunk
        events, buffer = parse_sse_blocks(buffer)
        yield from events
    if buffer.strip():
        events, _ = parse_sse_blocks(buffer + "\n\n")
        yield from events


def chat_sync(message: str, session_id: str | None) -> dict:
    payload = {"message": message, "sessionId": session_id, "stream": False}
    with http_client(timeout=120.0) as client:
        resp = client.post(chat_url(), json=payload)
        resp.raise_for_status()
        return resp.json()


def chat_stream(message: str, session_id: str | None) -> Generator[tuple[str, str], None, None]:
    payload = {"message": message, "sessionId": session_id, "stream": True}
    with http_client(timeout=120.0) as client:
        with client.stream(
            "POST",
            chat_url(),
            json=payload,
            headers={"Accept": "text/event-stream"},
        ) as resp:
            resp.raise_for_status()
            yield from iter_sse_events(resp)


def render_sidebar() -> None:
    with st.sidebar:
        st.header("设置")
        st.session_state.stream = st.toggle("流式回复", value=st.session_state.stream)
        st.session_state.api_base = st.text_input(
            "Java Agent 地址",
            value=st.session_state.api_base,
            help="聊天 API：/api/agent/chat。建议用 127.0.0.1:8081，不要填 Gateway 8080。",
        ).strip().rstrip("/")
        if is_gateway_url(st.session_state.api_base):
            st.warning("8080 是 LLM Gateway，不能聊天。请改为 **127.0.0.1:8081**。")
        st.caption(f"请求 → `{chat_url()}`")
        if st.button("测试连接", use_container_width=True):
            probe_agent()
        if st.session_state.session_id:
            st.caption("Session")
            st.code(st.session_state.session_id, language=None)
        if st.button("新对话", use_container_width=True):
            st.session_state.messages = []
            st.session_state.session_id = None
            st.rerun()
        st.divider()
        st.markdown(
            "**示例**\n\n"
            "1. 帮我上网搜一下注意力机制\n\n"
            "2. 刚才提到的再详细讲一下"
        )


def render_history() -> None:
    for msg in st.session_state.messages:
        with st.chat_message(msg["role"]):
            if msg.get("steps"):
                with st.expander("工具调用", expanded=False):
                    for step in msg["steps"]:
                        label = tool_label(step.get("tool"))
                        params = step.get("params") or {}
                        st.markdown(f"- **{label}** `{json.dumps(params, ensure_ascii=False)}`")
            st.markdown(msg["content"])
            if msg.get("terminated_reason"):
                st.caption(msg["terminated_reason"])


def run_assistant_turn(message: str) -> None:
    steps: list[dict] = []
    answer = ""
    terminated = ""

    if is_gateway_url(st.session_state.api_base):
        st.error("Java Agent 地址不能填 Gateway（8080），请改为 `http://127.0.0.1:8081`")
        return

    if st.session_state.stream:
        status = st.empty()
        status.caption("正在思考…")

        def stream_tokens():
            nonlocal terminated
            for event, data in chat_stream(message, st.session_state.session_id):
                if event == "step":
                    step = json.loads(data)
                    steps.append(step)
                    status.caption(step_status(step.get("tool")) + "…")
                elif event == "token":
                    yield data
                elif event == "done":
                    payload = json.loads(data)
                    st.session_state.session_id = payload.get("sessionId") or st.session_state.session_id
                    terminated = payload.get("terminatedReason") or ""

        try:
            answer = st.write_stream(stream_tokens()) or ""
        except httpx.HTTPError as exc:
            status.empty()
            st.markdown(format_http_error(exc))
            return
        except Exception as exc:
            status.empty()
            st.error(f"处理失败：{exc}")
            return
        finally:
            status.empty()

        if steps:
            with st.expander("工具调用", expanded=False):
                for step in steps:
                    label = tool_label(step.get("tool"))
                    params = step.get("params") or {}
                    st.markdown(f"- **{label}** `{json.dumps(params, ensure_ascii=False)}`")
        if not answer:
            st.markdown("（空回复）")
    else:
        try:
            data = chat_sync(message, st.session_state.session_id)
        except httpx.HTTPError as exc:
            st.markdown(format_http_error(exc))
            return
        st.session_state.session_id = data.get("sessionId") or st.session_state.session_id
        answer = data.get("answer") or "（空回复）"
        terminated = data.get("terminatedReason") or ""
        steps = [s for s in (data.get("steps") or []) if s.get("action") == "call_tool"]
        with st.expander("工具调用", expanded=False) if steps else st.container():
            if steps:
                for step in steps:
                    label = tool_label(step.get("tool"))
                    params = step.get("params") or {}
                    st.markdown(f"- **{label}** `{json.dumps(params, ensure_ascii=False)}`")
        st.markdown(answer)
        if terminated:
            st.caption(terminated)

    st.session_state.messages.append(
        {
            "role": "assistant",
            "content": answer or "（空回复）",
            "steps": steps,
            "terminated_reason": terminated,
        }
    )


def main() -> None:
    st.set_page_config(
        page_title="Research Agent",
        page_icon="🔬",
        layout="wide",
        initial_sidebar_state="expanded",
    )
    init_session()
    render_sidebar()

    st.title("Research Agent")
    st.caption("知识库检索 · 上网搜索 · 多轮对话 · Markdown 渲染")

    render_history()

    if prompt := st.chat_input("输入消息…"):
        st.session_state.messages.append({"role": "user", "content": prompt})
        with st.chat_message("user"):
            st.markdown(prompt)
        with st.chat_message("assistant"):
            run_assistant_turn(prompt)


if __name__ == "__main__":
    main()
