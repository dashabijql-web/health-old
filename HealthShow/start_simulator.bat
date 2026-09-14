@echo off
REM HealthShow 模拟器自动启动脚本
REM 用法：双击运行，或注册为 Windows 计划任务（开机自启）
REM
REM 注册为计划任务（以管理员身份运行 cmd）：
REM   schtasks /create /tn "HealthSimulator" /tr "<HealthShow>\start_simulator.bat" /sc onlogon /ru "%USERNAME%" /f

title HealthShow Simulator
pushd "%~dp0" >nul || exit /b 1
echo [%date% %time%] 正在启动 HealthShow 模拟器...
python watch_tcp_simulator_1000.py
echo [%date% %time%] 模拟器已退出，10秒后重启...
timeout /t 10
popd >nul
goto :eof
