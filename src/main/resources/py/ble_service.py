#!/usr/bin/env python3
"""Headless BLE backend for dlqq-mcp-server.

Protocol: newline-delimited JSON over stdin/stdout.
Request:  {"id": 1, "type": "scan", "params": {"timeout": 5}}
Response: {"id": 1, "success": true, ...}
Event:    {"event": "notification", "address": "...", "characteristic": "...", "data": [1,2]}
Logs go to stderr.
"""

import asyncio
import json
import sys
import threading
import traceback
from collections import deque

from bleak import BleakClient, BleakScanner

MAX_LOG_LINES = 2000

stdout_lock = threading.Lock()


def emit(payload):
    with stdout_lock:
        sys.stdout.write(json.dumps(payload, ensure_ascii=False) + "\n")
        sys.stdout.flush()


class Backend:
    def __init__(self):
        self.scanned = {}
        self.clients = {}
        self.logs = deque(maxlen=MAX_LOG_LINES)
        self.display_as_hex = False
        self.max_buffer_bytes = 5 * 1024 * 1024
        self.data_buffer = []
        self.loop = asyncio.new_event_loop()
        self.loop_thread = threading.Thread(target=self._run_loop, daemon=True,
                                            name="ble-event-loop")
        self.loop_thread.start()

    def _run_loop(self):
        asyncio.set_event_loop(self.loop)
        self.loop.run_forever()

    def submit(self, coro, timeout=60):
        future = asyncio.run_coroutine_threadsafe(coro, self.loop)
        return future.result(timeout)

    def log(self, message, direction="SYS"):
        line = "[%s] %s" % (direction, message)
        self.logs.append(line)
        print(line, file=sys.stderr, flush=True)

    # ==================== commands ====================

    def handle(self, request):
        command_type = request.get("type")
        params = request.get("params") or {}
        handler = getattr(self, "cmd_" + command_type, None)
        if handler is None:
            return {"success": False, "error": "unknown command: %s" % command_type}
        try:
            return handler(params)
        except Exception as exc:  # noqa: BLE001 - report to Java side
            self.log("command %s failed: %s" % (command_type, exc), "ERROR")
            traceback.print_exc(file=sys.stderr)
            return {"success": False, "error": str(exc)}

    def cmd_ping(self, params):
        return {"success": True, "message": "pong"}

    def _scan_online(self, timeout):
        discovered = {}

        def detection_callback(device, advertisement_data):
            discovered[device.address] = {
                "address": device.address,
                "name": device.name or advertisement_data.local_name or "",
                "rssi": advertisement_data.rssi,
            }

        async def scan():
            scanner = BleakScanner(detection_callback=detection_callback)
            await scanner.start()
            await asyncio.sleep(max(1, timeout))
            await scanner.stop()

        self.submit(scan(), timeout=max(10, timeout + 10))
        return discovered

    def cmd_scan(self, params):
        timeout = int(params.get("timeout", 5))
        found = self._scan_online(timeout)
        self.scanned.update(found)
        return {"success": True, "count": len(self.scanned)}

    def cmd_get_devices(self, params):
        devices = list(self.scanned.values())
        devices.sort(key=lambda item: item.get("address", ""))
        return {"success": True, "devices": devices}

    def _client(self, address):
        client = self.clients.get(address)
        if client is None or not client.is_connected:
            raise RuntimeError("BLE 设备未连接: %s" % address)
        return client

    def cmd_connect(self, params):
        address = params.get("address")
        if not address:
            return {"success": False, "error": "address is required"}

        async def connect():
            client = BleakClient(address, timeout=20.0)
            await client.connect()
            return client

        client = self.submit(connect(), timeout=40)
        self.clients[address] = client
        self.log("connected: %s" % address)
        return {"success": True, "address": address}

    def cmd_disconnect(self, params):
        address = params.get("address")
        client = self.clients.pop(address, None)
        if client is not None:
            self.submit(client.disconnect(), timeout=20)
        self.log("disconnected: %s" % address)
        return {"success": True, "address": address}

    def cmd_discover(self, params):
        address = params.get("address")
        client = self._client(address)

        async def discover():
            services = []
            for service in client.services:
                characteristics = []
                for characteristic in service.characteristics:
                    characteristics.append({
                        "uuid": characteristic.uuid,
                        "properties": list(characteristic.properties),
                    })
                services.append({"uuid": service.uuid, "characteristics": characteristics})
            return services

        services = self.submit(discover(), timeout=30)
        return {"success": True, "services": services}

    def cmd_read(self, params):
        address = params.get("address")
        char_uuid = params.get("characteristic") or params.get("char_uuid")
        client = self._client(address)

        async def read():
            return await client.read_gatt_char(char_uuid)

        value = self.submit(read(), timeout=30)
        return {"success": True, "data": list(value)}

    def cmd_write(self, params):
        address = params.get("address")
        char_uuid = params.get("characteristic") or params.get("char_uuid")
        data = params.get("data")
        if isinstance(data, str):
            data = list(data.encode("utf-8"))
        client = self._client(address)

        async def write():
            await client.write_gatt_char(char_uuid, bytes(bytearray(data)), response=True)

        self.submit(write(), timeout=30)
        self.log("write %s -> %s" % (char_uuid, data))
        return {"success": True}

    def cmd_subscribe(self, params):
        address = params.get("address")
        char_uuid = params.get("characteristic") or params.get("char_uuid")
        client = self._client(address)

        def callback(characteristic, data):
            emit({
                "event": "notification",
                "address": address,
                "characteristic": characteristic.uuid,
                "data": list(data),
            })

        async def start_notify():
            await client.start_notify(char_uuid, callback)

        self.submit(start_notify(), timeout=30)
        return {"success": True}

    def cmd_unsubscribe(self, params):
        address = params.get("address")
        char_uuid = params.get("characteristic") or params.get("char_uuid")
        client = self._client(address)

        async def stop_notify():
            await client.stop_notify(char_uuid)

        self.submit(stop_notify(), timeout=30)
        return {"success": True}

    def cmd_get_log(self, params):
        lines = int(params.get("lines", 100))
        return {"success": True, "logs": list(self.logs)[-lines:]}

    def cmd_clear(self, params):
        self.logs.clear()
        return {"success": True}

    def cmd_switch_format(self, params):
        self.display_as_hex = params.get("format", "text") == "hex"
        return {"success": True, "format": "hex" if self.display_as_hex else "text"}

    def cmd_exit(self, params):
        return {"success": True}

    def shutdown(self):
        for address in list(self.clients):
            try:
                self.submit(self.clients.pop(address).disconnect(), timeout=5)
            except Exception:  # noqa: BLE001 - best effort cleanup
                pass
        try:
            self.loop.call_soon_threadsafe(self.loop.stop)
        except RuntimeError:
            pass


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except AttributeError:
        pass
    backend = Backend()
    emit({"event": "ready"})
    for raw in sys.stdin:
        raw = raw.strip()
        if not raw:
            continue
        try:
            request = json.loads(raw)
        except json.JSONDecodeError as exc:
            emit({"success": False, "error": "invalid json: %s" % exc})
            continue
        request_id = request.get("id")
        result = backend.handle(request)
        if request_id is not None:
            result = dict(result)
            result["id"] = request_id
        if request.get("type") == "exit":
            backend.shutdown()
            return
        emit(result)
    backend.shutdown()


if __name__ == "__main__":
    main()
