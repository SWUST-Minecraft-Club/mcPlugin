#!/usr/bin/env python3
"""用 Python 标准库验证真实 MinecraftMcp；不打印令牌。"""
import argparse
import base64
import json
from pathlib import Path
import sys
import urllib.error
import urllib.request

REPO = Path(__file__).resolve().parent.parent


class Client:
    def __init__(self, url, token):
        self.url = url
        self.token = token
        self.request_id = 0
        self.protocol = "2025-11-25"

    def rpc(self, method, params=None, notification=False):
        self.request_id += 1
        body = {"jsonrpc": "2.0", "method": method}
        if not notification:
            body["id"] = self.request_id
        if params is not None:
            body["params"] = params
        request = urllib.request.Request(
            self.url,
            data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
            headers={
                "Authorization": "Bearer " + self.token,
                "Content-Type": "application/json",
                "Accept": "application/json, text/event-stream",
                "MCP-Protocol-Version": self.protocol,
            },
            method="POST",
        )
        with urllib.request.urlopen(request, timeout=50) as response:
            if notification:
                if response.status != 202:
                    raise RuntimeError("通知未返回 202")
                return None
            result = json.load(response)
        if result.get("id") != self.request_id or result.get("jsonrpc") != "2.0":
            raise RuntimeError("JSON-RPC 响应不匹配")
        if "error" in result:
            raise RuntimeError("JSON-RPC 错误：" + json.dumps(result["error"], ensure_ascii=False))
        return result["result"]

    def tool(self, name, **arguments):
        result = self.rpc("tools/call", {"name": name, "arguments": arguments})
        if result.get("isError"):
            raise RuntimeError(name + " 失败：" + json.dumps(result.get("structuredContent"), ensure_ascii=False))
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, required=True, help="游戏实例的 config/minecraft-mcp.json")
    parser.add_argument("--url", help="port=0 时使用日志中的真实 URL")
    parser.add_argument("--exercise-ui", action="store_true", help="在世界中打开并关闭背包，检查容器和物品")
    args = parser.parse_args()
    config = json.loads(args.config.read_text(encoding="utf-8"))
    if not args.url and not config.get("port", 8765):
        parser.error("随机端口请用 --url 指定日志中的服务地址")
    url = args.url or "http://127.0.0.1:" + str(config.get("port", 8765)) + "/mcp"
    # 此工具只允许本机服务，避免将实例令牌误发给其他主机。
    from urllib.parse import urlparse
    parsed = urlparse(url)
    if parsed.scheme != "http" or parsed.hostname not in ("127.0.0.1", "localhost") or parsed.path != "/mcp":
        parser.error("--url 必须为本机 http://127.0.0.1:<端口>/mcp")
    client = Client(url, config["token"])
    hello = client.rpc("initialize", {
        "protocolVersion": client.protocol, "capabilities": {},
        "clientInfo": {"name": "minecraft-mcp-smoke", "version": "1.0"},
    })
    client.protocol = hello["protocolVersion"]
    client.rpc("notifications/initialized", notification=True)
    tools = client.rpc("tools/list")["tools"]
    required = {"capture.screenshot", "ui.click", "ui.open", "inventory.list", "item.inspect", "test.wait_for"}
    if not required.issubset({tool["name"] for tool in tools}):
        raise RuntimeError("工具目录缺少必要测试能力")
    state = client.tool("client.state")["structuredContent"]
    client.tool("ui.inspect")
    print("MCP 握手成功；工具：", len(tools), "；适配器：", state.get("adapter"))
    opened = False
    try:
        if args.exercise_ui:
            if not state["inWorld"] or state.get("screen") is not None:
                raise RuntimeError("--exercise-ui 需要进入世界并先关闭其他界面")
            client.tool("ui.open", screen="inventory")
            opened = True
            client.tool("test.wait", ticks=2)
            client.tool("test.wait_for", query="ui.inspect", path="/screen/class", operator="exists", timeoutMs=3000)
            inventory = client.tool("inventory.list", scope="container", includeEmpty=True)["structuredContent"]
            slots = inventory["slots"]
            selected = next((slot for slot in slots if not slot["item"]["empty"]), slots[0] if slots else None)
            if selected is not None:
                client.tool("item.inspect", scope="container", slot=selected["slot"])
            print("背包与物品检查成功；槽位：", inventory["size"])
        capture = client.tool("capture.screenshot", maxWidth=1280)
        image = next(item for item in capture["content"] if item["type"] == "image")
        png = base64.b64decode(image["data"], validate=True)
        if image["mimeType"] != "image/png" or not png.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("截图不是有效 PNG")
        output = REPO / "fabric-26_3-mod/MinecraftMcp/build/smoke/screenshot.png"
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_bytes(png)
        print("截图成功：./" + output.relative_to(REPO).as_posix())
    finally:
        if opened:
            client.tool("ui.close")
    print("冒烟验证通过")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, KeyError, StopIteration) as error:
        print("冒烟验证失败：" + str(error), file=sys.stderr)
        sys.exit(1)
