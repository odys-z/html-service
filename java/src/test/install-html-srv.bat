@echo install-html-srv.bat: Don't call this directly, use the wrapper, intatllw.bat.
@echo Don't use space in service name.

@echo Usage: src/test/install-html-srv.bat
@echo   [1]prunsrv(e.g. src\test\prunsrv.exe) [2]jar-name [3]service-name
@echo   [4]resource-path(or web-root, e.g. src\main\webapp) [5]main-class(HtmlServer) [6]main-package(io.oz.srv.main-class)


@set prunsrv=%1
@set jar=%2
@set servic_name=%3
@set res_path=%4
@set mainclass=%5
@set serv_class=%6

@set workfolder=%cd%
@set classpath=%workfolder%\%jar%

@echo Jar package: %classpath%
@echo mainclass=%mainclass%
@echo Prunsrv.exe: %prunsrv%

@echo "Finding service main class:"
jar tf %classpath% | findstr "%mainclass%"

@echo
@echo Confirm permission (in the hidden dialog) to install the service %servic_name%...

@%prunsrv% //IS//%servic_name% --Install=%workfolder%\%prunsrv% ^
--ServiceUser LocalSystem ^
--Description="Synode %servic_name% %jar%" ^
--Jvm=auto ^
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
