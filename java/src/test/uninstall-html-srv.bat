@REM @set prjdir=%cd%
@REM @set classpath=%prjdir%\%rel_jar%
@REM @set html_srv_name=html-service-test
@set prunsrv=%1
@set html_srv_name=%2

%prunsrv% //SS//%html_srv_name% --StopTimeout=20
%prunsrv% //DS//%html_srv_name%
