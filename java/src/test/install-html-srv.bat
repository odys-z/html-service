@echo "Download the Procrun.exe binary at Apache Daemon https://downloads.apache.org/commons/daemon/binaries/windows/"
@echo "see https://commons.apache.org/proper/commons-daemon/binaries.html"
@echo "Run mvn clean compile package"
@echo "Run this file from project dir, e.g. src/test/install-html.bat"

@set jar-ver=0.1.0
@set rel_jar=target\html-web-%jar-ver%.jar
@set prjdir=%cd%
@set classpath=%prjdir%\%rel_jar%
@set servic_name=html-service-test
@set prunsrv=src\test\prunsrv.exe

@echo %classpath%


@echo "Finding service main class:"
jar tf %classpath% | findstr "HtmlServer"

%prunsrv% //IS//%servic_name% --Install=%prjdir%\%prunsrv% ^
--ServiceUser LocalSystem ^
--Description="html-service-test" ^
--Jvm=auto ^
--StartPath=%prjdir%\src\main\webapp ^
--Classpath=%classpath% ^
--Startup=auto ^
--StartMode=jvm ^
--StartClass=io.oz.srv.HtmlServer ^
--StartMethod=jvmStart ^
--JvmOptions=-Dfile.encoding=UTF-8;-Dstdout.encoding=UTF-8;-Dstderr.encoding=UTF-8 ^
--StopMode=java ^
--StopClass=io.oz.syntier.serv.SynotierJettyApp ^
--StopMethod=jvmStop ^
--StopParams=stop ^
--LogPath=%cd%\logs ^
--StdOutput=auto ^
--StdError=auto

%prunsrv% //ES//%servic_name%


@echo "iconv -f GB2312 -t UTF-8 winsrv/logs/commons-daemon.yyyy-mm-dd.log > commons-daemon-utf8.log"