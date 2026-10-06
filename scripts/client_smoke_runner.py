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

DEFAULT_SUCCESS_PATTERN = (
    r"(Sound engine started|"
    r"Narrator library for .* initialized|"
    r"Done \([0-9\.]+s\)!|"
    r"Dedicated server took [0-9\.]+ seconds)"
)
DEFAULT_CRASH_PATTERN = (
    r"(Crash report saved to:|"
    r"Exception in thread \"main\"|"
    r"Failed to start Minecraft|"
    r"MixinApplyError|MixinTransformerError|"
    r"A potential solution has been determined:|"
    r"failed to load a valid ResourcePackInfo|"
    r"Warning while loading mods|"
    r"Errors detected during load|"
    r"has failed to load correctly|"
    r"ModLoadingException|"
    r"Failed to create mod instance|"
    r"Missing or unsupported mandatory dependencies:|"
    r"ModResolutionException|"
    r"Error during pre-loading phase|"
    r"Missing language |needs language provider |"
    r"\[main/FATAL\]|\[Render thread/FATAL\]|\[Server thread/FATAL\]|"
    r"Encountered an unexpected exception|"
    r"Stopping server|"
    r"Found [0-9]+ dependencies missing|"
    r"Multiple problems were encountered|"
    r"Mod file .* is not valid)"
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

        # 確保清理該實例先前殘留的進程
        self._terminate_process(None, mc_dir, inst)

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

    def _is_mc_java_running(self, mc_dir: Path, inst: dict = None) -> bool:
        """檢查系統中是否正有屬於該實例的 Java 進程在運行 (透過 cwd 與 cmdline 雙重匹配)"""
        if os.name == "nt":
            return False
        mc_dir_str = str(mc_dir.resolve())
        inst_dir_str = str(mc_dir.parent.resolve())
        inst_name = inst.get("name", "") if inst else ""
        try:
            for pid_dir in Path("/proc").glob("[0-9]*"):
                try:
                    # 1. 優先檢查進程工作目錄 (cwd)
                    cwd_link = pid_dir / "cwd"
                    if cwd_link.is_symlink():
                        try:
                            if os.readlink(str(cwd_link)) == mc_dir_str:
                                return True
                        except Exception:
                            pass

                    # 2. 檢查命令列 (cmdline)
                    cmdline_file = pid_dir / "cmdline"
                    if cmdline_file.exists():
                        cmdline = cmdline_file.read_bytes().replace(b"\x00", b" ").decode("utf-8", errors="ignore")
                        if "java" in cmdline:
                            if mc_dir_str in cmdline or inst_dir_str in cmdline or (inst_name and inst_name in cmdline):
                                return True
                except (PermissionError, ProcessLookupError, ValueError):
                    continue
        except Exception:
            pass
        return False

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

        logs_dir = mc_dir / "logs"
        logs_dir.mkdir(parents=True, exist_ok=True)
        log_file = logs_dir / "latest.log"
        stdout_log_file = logs_dir / "smoke_runner_stdout.log"

        # 徹底隔離與重置日誌，防止讀取到先前執行的舊紀錄
        if log_file.exists():
            try:
                backup_log = log_file.with_name("latest.log.prev")
                if backup_log.exists():
                    backup_log.unlink()
                shutil.move(str(log_file), str(backup_log))
            except Exception:
                pass
            # 強制將 latest.log 清空為 0 位元組（若無法 move 則直接 truncate）
            try:
                with open(log_file, "w", encoding="utf-8") as f:
                    f.truncate(0)
            except Exception:
                pass

        if stdout_log_file.exists():
            try:
                stdout_log_file.unlink()
            except Exception:
                pass

        crash_dir = mc_dir / "crash-reports"
        existing_crashes = set(crash_dir.glob("crash-*.txt")) if crash_dir.exists() else set()

        print(f"🎮 執行啟動指令: {launch_cmd}")
        print(f"📁 工作目錄 (CWD): {mc_dir}")
        print(f"⏱️ 逾時限制: {timeout} 秒，開始監聽日誌與進程狀態...")

        start_time = time.time()
        proc = None
        status = "TIMEOUT"
        detail_msg = "超過等待時間未達到就緒狀態"
        stdout_fd = None

        try:
            stdout_fd = open(stdout_log_file, "w", encoding="utf-8", errors="ignore")
            # 建立獨立進程組以利後續完全關閉，並指定 cwd 為 mc_dir (確保 ./run.sh 正常運作)
            if os.name != "nt":
                proc = subprocess.Popen(
                    launch_cmd,
                    shell=True,
                    cwd=str(mc_dir),
                    stdout=stdout_fd,
                    stderr=subprocess.STDOUT,
                    preexec_fn=os.setsid
                )
            else:
                proc = subprocess.Popen(
                    launch_cmd,
                    shell=True,
                    cwd=str(mc_dir),
                    stdout=stdout_fd,
                    stderr=subprocess.STDOUT
                )

            log_offset = 0
            stdout_offset = 0
            compiled_success = re.compile(success_pattern)
            compiled_crash = re.compile(DEFAULT_CRASH_PATTERN)
            has_started = False

            while time.time() - start_time < timeout:
                time.sleep(1.0)
                elapsed = time.time() - start_time

                # 讀取 latest.log 與 smoke_runner_stdout.log 新增內容
                new_logs = []

                if log_file.exists():
                    try:
                        file_size = log_file.stat().st_size
                        if file_size < log_offset:
                            log_offset = 0
                        with open(log_file, "r", encoding="utf-8", errors="ignore") as f:
                            f.seek(log_offset)
                            chunk = f.read()
                            log_offset = f.tell()
                            if chunk:
                                new_logs.append(chunk)
                    except Exception:
                        pass

                if stdout_log_file.exists():
                    try:
                        file_size = stdout_log_file.stat().st_size
                        if file_size < stdout_offset:
                            stdout_offset = 0
                        with open(stdout_log_file, "r", encoding="utf-8", errors="ignore") as f:
                            f.seek(stdout_offset)
                            chunk = f.read()
                            stdout_offset = f.tell()
                            if chunk:
                                new_logs.append(chunk)
                    except Exception:
                        pass

                combined_new_text = "\n".join(new_logs)

                # 1. 致命錯誤與崩潰模式檢測 (CRASH PATTERN - 優先判定)
                if combined_new_text:
                    crash_match = compiled_crash.search(combined_new_text)
                    if crash_match:
                        status = "FAIL"
                        matched_str = crash_match.group(0)
                        detail_msg = f"日誌檢測到致命阻斷錯誤: {matched_str}"
                        print(f"\n💥 {detail_msg}")
                        # 印出匹配行前後脈絡
                        for line in combined_new_text.splitlines():
                            if matched_str in line:
                                print(f"    ↳ {line.strip()}")
                        break

                # 2. 檢查是否有新誕生的 crash-report 檔案
                if crash_dir.exists():
                    current_crashes = set(crash_dir.glob("crash-*.txt"))
                    new_crashes = current_crashes - existing_crashes
                    if new_crashes:
                        newest_crash = max(new_crashes, key=lambda f: f.stat().st_mtime)
                        status = "FAIL"
                        detail_msg = f"產生新的崩潰報告: {newest_crash.name}"
                        print(f"\n💥 {detail_msg}")
                        try:
                            with open(newest_crash, "r", encoding="utf-8", errors="ignore") as cf:
                                lines = [cf.readline().strip() for _ in range(8)]
                                print("\n--- [崩潰報告摘要] ---")
                                print("\n".join(lines))
                                print("----------------------\n")
                        except Exception:
                            pass
                        break

                # 3. 成功模式檢測 (SUCCESS PATTERN - 必須真正到達就緒標誌)
                if combined_new_text:
                    success_match = compiled_success.search(combined_new_text)
                    if success_match:
                        status = "PASS"
                        detail_msg = f"成功達到就緒狀態 (命中: {success_match.group(0)})"
                        print(f"\n🎉 {detail_msg}")
                        break

                # 4. 偵測遊戲/伺服器是否已真正拉起運行
                if not has_started:
                    if (log_file.exists() and log_file.stat().st_size > 0) or self._is_mc_java_running(mc_dir, inst):
                        has_started = True

                # 5. 進程異常退出檢測 (Fail-Fast)
                if has_started:
                    # 遊戲已啟動後，若 Java 進程消失且非正常存活 -> 判定為崩潰退出
                    if not self._is_mc_java_running(mc_dir, inst) and (proc.poll() is not None):
                        time.sleep(1.0)
                        if not self._is_mc_java_running(mc_dir, inst):
                            status = "FAIL"
                            exit_code = proc.poll()
                            detail_msg = f"遊戲進程已異常退出 (Exit code: {exit_code})，未達成就緒狀態"
                            print(f"\n💥 {detail_msg}")
                            self._print_tail_logs(log_file, stdout_log_file)
                            break
                else:
                    # 遊戲尚未確認啟動
                    if proc.poll() is not None:
                        if proc.poll() != 0:
                            # 啟動命令本體回傳非 0 錯誤
                            status = "FAIL"
                            detail_msg = f"啟動指令執行失敗 (Exit code: {proc.poll()})"
                            print(f"\n💥 {detail_msg}")
                            self._print_tail_logs(log_file, stdout_log_file)
                            break
                        elif elapsed > 20:
                            # 啟動器 CLI 雖回傳 0，但超過 20 秒仍無遊戲進程拉起
                            status = "FAIL"
                            detail_msg = "啟動指令已結束，但超過 20 秒仍未偵測到遊戲進程或日誌產生"
                            print(f"\n💥 {detail_msg}")
                            self._print_tail_logs(log_file, stdout_log_file)
                            break

                sys.stdout.write(f"\r⏳ 等待啟動就緒... 已耗時: {int(elapsed)}s / {timeout}s")
                sys.stdout.flush()

        finally:
            if stdout_fd and not stdout_fd.closed:
                try:
                    stdout_fd.close()
                except Exception:
                    pass
            print("\n🛑 結束實例進程...")
            self._terminate_process(proc, mc_dir, inst)

        duration = round(time.time() - start_time, 1)
        self.results.append({
            "id": inst_id,
            "name": name,
            "status": status,
            "time": duration,
            "msg": detail_msg
        })

    def _print_tail_logs(self, log_file: Path, stdout_log_file: Path):
        try:
            tail_lines = []
            if log_file.exists() and log_file.stat().st_size > 0:
                with open(log_file, "r", encoding="utf-8", errors="ignore") as f:
                    tail_lines = [line.strip() for line in f.readlines()[-15:] if line.strip()]
            elif stdout_log_file.exists():
                with open(stdout_log_file, "r", encoding="utf-8", errors="ignore") as f:
                    tail_lines = [line.strip() for line in f.readlines()[-15:] if line.strip()]
            if tail_lines:
                print("\n--- [最後日誌紀錄] ---")
                print("\n".join(tail_lines))
                print("----------------------\n")
        except Exception:
            pass

    def _terminate_process(self, proc, mc_dir: Path = None, inst: dict = None):
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

        # 額外防護：搜尋並終止屬於此 instance 的 Minecraft Java 進程
        if mc_dir and os.name != "nt":
            mc_dir_str = str(mc_dir.resolve())
            inst_dir_str = str(mc_dir.parent.resolve())
            inst_name = inst.get("name", "") if inst else ""
            try:
                for pid_dir in Path("/proc").glob("[0-9]*"):
                    try:
                        pid = int(pid_dir.name)
                        cwd_link = pid_dir / "cwd"
                        is_match = False
                        if cwd_link.is_symlink():
                            try:
                                if os.readlink(str(cwd_link)) == mc_dir_str:
                                    is_match = True
                            except Exception:
                                pass
                        if not is_match:
                            cmdline_file = pid_dir / "cmdline"
                            if cmdline_file.exists():
                                cmdline = cmdline_file.read_bytes().replace(b"\x00", b" ").decode("utf-8", errors="ignore")
                                if "java" in cmdline and (mc_dir_str in cmdline or inst_dir_str in cmdline or (inst_name and inst_name in cmdline)):
                                    is_match = True
                        if is_match:
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
