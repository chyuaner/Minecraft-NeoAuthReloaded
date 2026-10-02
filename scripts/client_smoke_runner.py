#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Client Smoke Test Runner for Minecraft Modding (Multi-Loader / Multi-Version)
支援自備啟動器實例（FjordLauncher / PrismLauncher 等）自動化部署、啟動、健康偵測與優雅關閉。
"""

import argparse
import fnmatch
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import time
from pathlib import Path

DEFAULT_SUCCESS_PATTERN = r"(Reloading ResourceManager|Sound engine started|Setting user:|MinecraftForge v.* Initialized|NeoForge Initialized)"
DEFAULT_CRASH_PATTERN = (
    r"(Crash report saved to:|Exception in thread \"main\"|Failed to start Minecraft|"
    r"MixinApplyError|MixinTransformerError|A potential solution has been determined:|"
    r"failed to load a valid ResourcePackInfo|Warning while loading mods)"
)


class SmokeTestRunner:
    def __init__(self, config_path: str, target_instance: str = None, deploy_only: bool = False, headless: bool = False):
        self.project_root = Path(__file__).resolve().parent.parent
        self.config_path = self._resolve_config_path(config_path)
        self.target_instance = target_instance
        self.deploy_only = deploy_only
        self.cli_headless = headless

        self.config = self._load_config()
        self.global_cfg = self.config.get("global", {})
        self.instances = self.config.get("instances", [])
        self.results = []

    def _resolve_config_path(self, user_path: str = None) -> Path:
        if user_path:
            p = Path(user_path)
            if not p.is_absolute():
                p = self.project_root / p
            if p.exists():
                return p
            raise FileNotFoundError(f"找不到指定的設定檔: {p}")

        # 優先尋找 local 設定檔，次之 sample 設定檔
        local_cfg = self.project_root / "client-smoke-test.local.json"
        if local_cfg.exists():
            return local_cfg

        sample_cfg = self.project_root / "client-smoke-test.sample.json"
        if sample_cfg.exists():
            print(f"[提示] 未發現 client-smoke-test.local.json，使用範本 {sample_cfg.name} 載入。")
            return sample_cfg

        raise FileNotFoundError("找不到任何 client-smoke-test 設定檔！")

    def _load_config(self) -> dict:
        print(f"[設定] 載入設定檔: {self.config_path}")
        with open(self.config_path, "r", encoding="utf-8") as f:
            return json.load(f)

    def _resolve_path(self, path_str: str) -> Path:
        p = Path(path_str)
        if not p.is_absolute():
            p = self.project_root / p
        return p

    def run(self) -> int:
        if not self.instances:
            print("[警告] 設定檔中沒有任何可用的 instance 定義。")
            return 0

        filtered_instances = [
            inst for inst in self.instances
            if not self.target_instance or inst.get("id") == self.target_instance
        ]

        if not filtered_instances:
            print(f"[錯誤] 找不到匹配的實例 ID: '{self.target_instance}'")
            return 1

        print(f"\n=======================================================")
        print(f"🚀 開始執行客戶端冒煙測試 (共 {len(filtered_instances)} 個實例)")
        print(f"模式: {'僅部署 (Deploy Only)' if self.deploy_only else '完整部署與啟動驗證'}")
        print(f"=======================================================\n")

        for inst in filtered_instances:
            self._process_instance(inst)

        self._print_summary()

        # 只要有任何一個實例 FAIL 或 TIMEOUT 即返回 1
        has_failure = any(r["status"] in ("FAIL", "TIMEOUT", "ERROR") for r in self.results)
        return 1 if has_failure else 0

    def _process_instance(self, inst: dict):
        inst_id = inst.get("id", "unknown")
        name = inst.get("name", inst_id)
        enabled = inst.get("enabled", True)

        if not enabled:
            print(f"⏩ [略過] {name} (ID: {inst_id}) - 設定為已停用 (enabled: false)")
            self.results.append({"id": inst_id, "name": name, "status": "SKIPPED", "time": 0, "msg": "已停用"})
            return

        print(f"\n-------------------------------------------------------")
        print(f"▶ 正在處理實例: {name} [{inst_id}] (Loader: {inst.get('loader', 'unknown')})")
        print(f"-------------------------------------------------------")

        # 1. 檢查目錄
        mc_dir = self._resolve_path(inst.get("minecraftDir", ""))
        mods_dir = mc_dir / "mods"
        if not mc_dir.exists():
            print(f"❌ [錯誤] 找不到 Minecraft 目錄: {mc_dir}")
            self.results.append({"id": inst_id, "name": name, "status": "ERROR", "time": 0, "msg": "Minecraft 目錄不存在"})
            return

        mods_dir.mkdir(parents=True, exist_ok=True)

        # 2. 部署 JAR
        deployed_jar = self._deploy_jar(inst, mods_dir)
        if not deployed_jar:
            self.results.append({"id": inst_id, "name": name, "status": "ERROR", "time": 0, "msg": "找不到編譯出的 JAR 檔案"})
            return

        if self.deploy_only:
            print(f"✅ 部署完成: {deployed_jar.name}")
            self.results.append({"id": inst_id, "name": name, "status": "DEPLOYED", "time": 0, "msg": f"已部署 {deployed_jar.name}"})
            return

        # 3. 執行啟動與監控
        self._launch_and_monitor(inst, mc_dir)

    def _deploy_jar(self, inst: dict, mods_dir: Path) -> Path:
        source_dir = self._resolve_path(inst.get("jarSourceDir", "build/libs"))
        jar_pattern = inst.get("jarPattern", "*.jar")

        if not source_dir.exists():
            print(f"❌ [錯誤] 找不到 JAR 來源目錄: {source_dir} (請先執行 build 或 collectJars)")
            return None

        # 尋找匹配的 JAR (排除 -sources 與 -dev)
        candidates = []
        for file in source_dir.glob(jar_pattern):
            if file.is_file() and not file.name.endswith("-sources.jar") and not file.name.endswith("-dev.jar"):
                candidates.append(file)

        if not candidates:
            print(f"❌ [錯誤] 在 {source_dir} 找不到符合模式 '{jar_pattern}' 的成品 JAR！")
            return None

        # 取最新修改時間的檔案
        target_jar = max(candidates, key=lambda f: f.stat().st_mtime)

        # 清除目標 mods 目錄中的舊同名/同 pattern 檔案
        if self.global_cfg.get("autoCleanOldJars", True):
            for existing in mods_dir.glob(jar_pattern):
                try:
                    existing.unlink()
                    print(f"🧹 清除舊 JAR: mods/{existing.name}")
                except Exception as e:
                    print(f"⚠️ 無法刪除舊 JAR {existing.name}: {e}")

        # 複製新 JAR
        dest_jar = mods_dir / target_jar.name
        shutil.copy2(target_jar, dest_jar)
        print(f"📦 已複製最新 JAR 至: mods/{dest_jar.name}")
        return dest_jar

    def _launch_and_monitor(self, inst: dict, mc_dir: Path):
        inst_id = inst.get("id")
        name = inst.get("name", inst_id)
        launch_cmd = inst.get("launchCommand", "")
        timeout = inst.get("timeoutSeconds", self.global_cfg.get("timeoutSeconds", 90))
        success_pattern = inst.get("successPattern", DEFAULT_SUCCESS_PATTERN)
        headless = self.cli_headless or self.global_cfg.get("headless", False)

        if not launch_cmd:
            print(f"❌ [錯誤] 實例未指定 launchCommand")
            self.results.append({"id": inst_id, "name": name, "status": "ERROR", "time": 0, "msg": "缺少啟動指令"})
            return

        if headless and os.name != "nt" and shutil.which("xvfb-run"):
            launch_cmd = f"xvfb-run -a {launch_cmd}"
            print("🖥️ 啟用無頭模式 (xvfb-run)")

        # 紀錄日誌起點與 Crash Reports
        log_file = mc_dir / "logs" / "latest.log"
        if log_file.exists():
            try:
                backup_log = log_file.with_name("latest.log.prev")
                if backup_log.exists():
                    backup_log.unlink()
                log_file.rename(backup_log)
            except Exception:
                pass
        initial_log_offset = 0

        crash_dir = mc_dir / "crash-reports"
        existing_crashes = set(crash_dir.glob("crash-*.txt")) if crash_dir.exists() else set()

        print(f"🎮 執行啟動指令: {launch_cmd}")
        print(f"⏱️ 逾時限制: {timeout} 秒，開始監聽日誌...")

        start_time = time.time()
        proc = None
        status = "TIMEOUT"
        detail_msg = "超過等待時間未進入主選單"

        try:
            # 建立獨立進程組以利後續完全關閉
            if os.name != "nt":
                proc = subprocess.Popen(
                    launch_cmd,
                    shell=True,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    preexec_fn=os.setsid
                )
            else:
                proc = subprocess.Popen(
                    launch_cmd,
                    shell=True,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL
                )

            current_offset = initial_log_offset
            compiled_success = re.compile(success_pattern)
            compiled_crash = re.compile(DEFAULT_CRASH_PATTERN)

            while time.time() - start_time < timeout:
                time.sleep(1.0)
                elapsed = time.time() - start_time

                # 檢查進程是否非預期過早退出
                if proc.poll() is not None:
                    # 啟動器本身退出了，有可能是啟動器背景化拉起遊戲，或真正崩潰
                    pass

                # 檢查日誌新內容
                if log_file.exists():
                    try:
                        file_size = log_file.stat().st_size
                        if file_size < current_offset:
                            # 檔案被重新截斷，重設為從頭讀取
                            current_offset = 0

                        with open(log_file, "r", encoding="utf-8", errors="ignore") as f:
                            f.seek(current_offset)
                            new_text = f.read()
                            current_offset = f.tell()

                            if new_text:
                                # 檢測崩潰模式
                                crash_match = compiled_crash.search(new_text)
                                if crash_match:
                                    status = "FAIL"
                                    detail_msg = f"日誌檢測到崩潰字串: {crash_match.group(0)}"
                                    print(f"\n💥 {detail_msg}")
                                    break

                                # 檢測成功模式
                                success_match = compiled_success.search(new_text)
                                if success_match:
                                    status = "PASS"
                                    detail_msg = f"成功進入主畫面 (命中: {success_match.group(0)})"
                                    print(f"\n🎉 {detail_msg}")
                                    break
                    except Exception as e:
                        pass

                # 檢查是否有新誕生的 crash-report 檔案
                if crash_dir.exists():
                    current_crashes = set(crash_dir.glob("crash-*.txt"))
                    new_crashes = current_crashes - existing_crashes
                    if new_crashes:
                        newest_crash = max(new_crashes, key=lambda f: f.stat().st_mtime)
                        status = "FAIL"
                        detail_msg = f"產生新的崩潰報告: {newest_crash.name}"
                        print(f"\n💥 {detail_msg}")
                        # 讀取崩潰報告前 10 行
                        try:
                            with open(newest_crash, "r", encoding="utf-8", errors="ignore") as cf:
                                lines = [cf.readline().strip() for _ in range(8)]
                                print("\n--- [崩潰報告摘要] ---")
                                print("\n".join(lines))
                                print("----------------------\n")
                        except Exception:
                            pass
                        break

                sys.stdout.write(f"\r⏳ 等待啟動就緒... 已耗時: {int(elapsed)}s / {timeout}s")
                sys.stdout.flush()

        finally:
            print("\n🛑 結束實例進程...")
            self._terminate_process(proc, mc_dir)

        duration = round(time.time() - start_time, 1)
        self.results.append({
            "id": inst_id,
            "name": name,
            "status": status,
            "time": duration,
            "msg": detail_msg
        })

    def _terminate_process(self, proc, mc_dir: Path = None):
        if proc:
            try:
                if os.name != "nt":
                    pgid = os.getpgid(proc.pid)
                    os.killpg(pgid, signal.SIGTERM)
                    for _ in range(6):
                        if proc.poll() is not None:
                            break
                        time.sleep(0.5)
                    try:
                        os.killpg(pgid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                else:
                    subprocess.call(["taskkill", "/F", "/T", "/PID", str(proc.pid)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            except Exception:
                pass

        # 額外防護：若啟動器將 Java 獨立開出，搜尋並終止屬於此 instance 的 Minecraft Java 進程
        if mc_dir and os.name != "nt":
            mc_dir_str = str(mc_dir.resolve())
            try:
                for pid_dir in Path("/proc").glob("[0-9]*"):
                    try:
                        cmdline_file = pid_dir / "cmdline"
                        if cmdline_file.exists():
                            cmdline = cmdline_file.read_bytes().replace(b"\x00", b" ").decode("utf-8", errors="ignore")
                            if "java" in cmdline and mc_dir_str in cmdline:
                                pid = int(pid_dir.name)
                                print(f"🛑 終止 Minecraft 客戶端 Java 進程 (PID: {pid})...")
                                os.kill(pid, signal.SIGTERM)
                                time.sleep(0.5)
                                try:
                                    os.kill(pid, signal.SIGKILL)
                                except ProcessLookupError:
                                    pass
                    except (PermissionError, ProcessLookupError, ValueError):
                        continue
            except Exception:
                pass

    def _print_summary(self):
        print("\n" + "=" * 68)
        print("📊 客戶端冒煙測試結果總覽 (Smoke Test Summary)")
        print("=" * 68)
        header = f"{'實例名稱 (Instance)':<22} | {'狀態':<8} | {'耗時':<6} | {'備註 / 結果摘要'}"
        print(header)
        print("-" * 68)

        for r in self.results:
            status_icon = {
                "PASS": "✅ PASS",
                "FAIL": "❌ FAIL",
                "TIMEOUT": "⏱️ TIMEOUT",
                "ERROR": "⚠️ ERROR",
                "DEPLOYED": "📦 DEPLOY",
                "SKIPPED": "⏩ SKIP"
            }.get(r["status"], r["status"])

            duration_str = f"{r['time']}s" if r['time'] > 0 else "-"
            line = f"{r['name']:<20} | {status_icon:<8} | {duration_str:<6} | {r['msg']}"
            print(line)

        print("=" * 68 + "\n")


def main():
    parser = argparse.ArgumentParser(description="Minecraft Mod 自備客戶端冒煙測試執行器")
    parser.add_argument("--config", "-c", type=str, default=None, help="指定設定檔路徑 (JSON)")
    parser.add_argument("--instance", "-i", type=str, default=None, help="僅測試指定 ID 的實例")
    parser.add_argument("--deploy-only", "-d", action="store_true", help="僅部署 JAR 檔案，不啟動遊戲")
    parser.add_argument("--headless", action="store_true", help="使用 xvfb-run 無頭模式執行")

    args = parser.parse_args()
    runner = SmokeTestRunner(
        config_path=args.config,
        target_instance=args.instance,
        deploy_only=args.deploy_only,
        headless=args.headless
    )
    exit_code = runner.run()
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
