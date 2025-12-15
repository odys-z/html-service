@echo install-html-srv.bat: Don't call this directly, use the wrapper, intatllw.bat.
@echo Don't use space in service name.

@REM Usage: src/test/install-html-srv.bat
@REM   [1]prunsrv(e.g. src\test\prunsrv.exe) [2]jar-name [3]service-name
@REM   [4]resource-path(or web-root, e.g. src\main\webapp) [5]main-class(HtmlServer) [6]main-package(io.oz.srv.main-class)
@REM   [7]jvm option, the jre path(optional, e.g. jre17), relative to the workfolder. If not given, use 'auto'.

@set prunsrv=%1
@echo [1 prunsrv]      = %1
@set jar=%2
@echo [2 jar]          = %2
@set servic_name=%3
@echo [3 service-name] = %3
@set res_path=%4
@echo [4 res-path]     = %4
@set mainclass=%5
@echo [5 main-class]   = %5
@set serv_class=%6
@echo [6 jserv-class]  = %6
@set jre_path=%7
@echo [7 jre-path]     = %7

@set workfolder=%cd%

if not "%~7" == "" (
@set jre_path=%workfolder%\%7\bin\server\jvm.dll --JavaHome=%workfolder%\%7 
) else (@set jre_path=auto)

@set classpath=%workfolder%\%jar%

@echo Jar package: %classpath%
@echo mainclass=%mainclass%
@echo Prunsrv.exe: %prunsrv%

@REM echo "Finding service main class:"
@REM jar tf %classpath% | findstr "%mainclass%"
@echo jvm=%jre_path% # jre17/bin/server/jvm.dll

@echo:
@echo ACTION NEEDED!
@echo:
@echo Please confirm permission (in the hidden dialog) to install the service %servic_name%...


@%prunsrv% //IS//%servic_name% --Install=%workfolder%\%prunsrv% ^
--ServiceUser LocalSystem ^
--Description="Synode %servic_name% %jar%" ^
--Jvm=%jre_path% ^
--StartPath=%workfolder%\%res_path% ^
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

@%prunsrv% //ES//%servic_name%
