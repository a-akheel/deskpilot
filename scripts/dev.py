#!/usr/bin/env python3
"""Start the local demo with one command. Ctrl-C stops only owned processes."""

import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]


def main():
    for command in ("java", "temporal", "pnpm"):
        if not shutil.which(command):
            raise SystemExit(
                f"Install {command} and add it to PATH first. See README.md."
            )
    env = dict(os.environ)
    env["LLM_PROVIDER"] = "demo"
    for key in ("OPERATOR_TOKEN", "APPROVER_TOKEN", "IT_API_TOKEN"):
        env.setdefault(key, secrets.token_urlsafe(20))
    # The launcher always runs the free demo. Live planning is an explicit manual setup.
    for key in list(env):
        if key.startswith("PORTKEY_"):
            del env[key]
    runtime = ROOT / ".runtime"
    runtime.mkdir(exist_ok=True)
    env["IT_DB"] = str(runtime / "fixture.db")
    env.setdefault("PORT", "8080")
    env.setdefault("IT_PORT", "8091")
    env.setdefault("IT_API_URL", "http://127.0.0.1:" + env["IT_PORT"])
    subprocess.run(
        [str(ROOT / "gradlew"), "installDist", "--no-daemon"],
        cwd=ROOT,
        env=env,
        check=True,
    )
    subprocess.run(
        ["pnpm", "install", "--frozen-lockfile", "--ignore-scripts"],
        cwd=ROOT / "web",
        env=env,
        check=True,
    )
    processes = []
    handles = []
    try:
        commands = [
            [
                "temporal",
                "server",
                "start-dev",
                "--ip",
                "127.0.0.1",
                "--db-filename",
                str(runtime / "temporal.db"),
            ],
            [sys.executable, "demo/it_api.py"],
            [str(ROOT / "build/install/deskpilot/bin/deskpilot")],
            ["pnpm", "--dir", "web", "dev"],
        ]
        for index, command in enumerate(commands):
            log = open(runtime / f"service-{index}.log", "a")
            handles.append(log)
            processes.append(
                subprocess.Popen(
                    command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT
                )
            )
            if index == 0:
                deadline = time.monotonic() + 30
                while time.monotonic() < deadline:
                    if processes[0].poll() is not None:
                        raise RuntimeError(
                            "Temporal exited; inspect .runtime/service-0.log for a port conflict"
                        )
                    try:
                        with socket.create_connection(("127.0.0.1", 7233), timeout=1):
                            break
                    except OSError:
                        time.sleep(0.2)
                else:
                    raise TimeoutError("Temporal did not start")
        print("DeskPilot: http://127.0.0.1:5173", flush=True)
        print("Operator token:", env["OPERATOR_TOKEN"], flush=True)
        print("Approver token:", env["APPROVER_TOKEN"], flush=True)
        print("Local demo only. Logs: .runtime/ · Stop with Ctrl-C", flush=True)
        while all(process.poll() is None for process in processes):
            time.sleep(0.5)
        raise RuntimeError("A service exited; inspect .runtime logs")
    except KeyboardInterrupt:
        pass
    finally:
        for process in reversed(processes):
            process.terminate()
        for process in processes:
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        for handle in handles:
            handle.close()


if __name__ == "__main__":
    main()
