
@echo Download the Procrun.exe binary at Apache Daemon https://downloads.apache.org/commons/daemon/binaries/windows/
@echo see https://commons.apache.org/proper/commons-daemon/binaries.html
@echo Run "mvn clean compile package" first
@echo Run this file from project dir, e.g. run: src/installw.bat

if "%~1" == "uninstall" (
@call src\test\uninstall-html-srv.bat src\test\prunsrv.exe "html-service test"
) else (
@call src\test\install-html-srv.bat src\test\prunsrv.exe target\html-web-0.1.1.jar "html-service test" src\main\webapp HtmlServer io.oz.srv.HtmlServer
)

@echo Tip for coverting log files' encoding (use VS Code Bash):
@echo iconv -f GB2312 -t UTF-8 logs/commons-daemon.yyyy-mm-dd.log > commons-daemon-utf8.log