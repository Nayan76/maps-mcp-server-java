@echo off
setlocal
set SCRIPT_DIR=%~dp0
set JAR_PATH=%SCRIPT_DIR%target\google-maps-mcp-server.jar

if not exist "%JAR_PATH%" (
    echo Building google-maps-mcp-server.jar... 1>&2
    pushd "%SCRIPT_DIR%"
    call mvn package -DskipTests -q
    popd
)

set MCP_TRANSPORT=stdio

java -Xms128m -Xmx512m ^
  -Dspring.ai.mcp.server.stdio=true ^
  -Dspring.main.web-application-type=none ^
  -Dspring.main.banner-mode=off ^
  -jar "%JAR_PATH%" %*
