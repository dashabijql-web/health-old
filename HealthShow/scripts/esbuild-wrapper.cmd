@echo off
set "SCRIPT_DIR=%~dp0"
set "ESBUILD_EXE=%SCRIPT_DIR%..\node_modules\@esbuild\win32-x64\esbuild.exe"
if not exist "%ESBUILD_EXE%" (
  echo esbuild executable not found: "%ESBUILD_EXE%" 1>&2
  exit /b 1
)
"%ESBUILD_EXE%" %*
