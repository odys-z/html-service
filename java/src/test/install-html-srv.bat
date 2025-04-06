@echo "Download the Procrun.exe binary at Apache Daemon https://downloads.apache.org/commons/daemon/binaries/windows/"
@echo "see https://commons.apache.org/proper/commons-daemon/binaries.html"
@echo "Run mvn clean compile package"
@echo "Run this file from project dir, e.g. src/test/install-html.bat"

@set jar-ver=0.1.0
@set jarname=html-web-%jar-ver%.jar
@set jar=target\%jarname%
@set workfolder=%cd%
@set classpath=%workfolder%\%jar%
@set servic_name=html-service-test
@set mainclass=HtmlServer
@set serv_class=io.oz.srv.%mainclass%
@set web_res=src\main\webapp
@set prunsrv=src\test\prunsrv.exe

@echo %classpath%

@echo "Finding service main class:"
jar tf %classpath% | findstr "HtmlServer"

%prunsrv% //IS//%servic_name% --Install=%workfolder%\%prunsrv% ^
--ServiceUser LocalSystem ^
--Description="html-service-test" ^
--Jvm=auto ^
--StartPath=%workfolder%\%web_res% ^
--Classpath=%classpath% ^
--Startup=auto ^
--StartMode=jvm ^
--StartClass=%serv_class% ^
--StartMethod=jvmStart ^
--JvmOptions=-Dfile.encoding=UTF-8;-Dstdout.encoding=UTF-8;-Dstderr.encoding=UTF-8 ^
--StopMode=jvm ^
--StopClass=%serv_class% ^
--StopMethod=jvmStop ^
--StopParams=stop ^
--LogPath=%cd%\logs ^
--StdOutput=auto ^
--StdError=auto

@echo "Finding service main class:"
jar tf %classpath% | findstr "%mainclass%"

%prunsrv% //ES//%servic_name%

@echo "Tip for coverting log files' encoding (use VS Code Bash):"
@echo "iconv -f GB2312 -t UTF-8 logs/commons-daemon.yyyy-mm-dd.log > commons-daemon-utf8.log"