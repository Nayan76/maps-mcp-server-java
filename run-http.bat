@echo off
setlocal
set SCRIPT_DIR=%~dp0
set JAR_PATH=%SCRIPT_DIR%target\google-maps-mcp-server.jar

if not exist "%JAR_PATH%" (
    echo Building google-maps-mcp-server.jar...
    pushd "%SCRIPT_DIR%"
    call mvn package -DskipTests -q
    popd
)

set MCP_TRANSPORT=http
if "%MCP_HTTP_PROTOCOL%"=="" set MCP_HTTP_PROTOCOL=STREAMABLE
if "%SERVER_PORT%"=="" set SERVER_PORT=8080

java -Xms256m -Xmx512m -jar "%JAR_PATH%" %*
