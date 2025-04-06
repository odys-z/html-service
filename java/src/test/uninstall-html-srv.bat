@set prjdir=%cd%
@set classpath=%prjdir%\%rel_jar%
@set html_srv_name=html-service-test
@set prunsrv=src\test\prunsrv.exe

@set PR_JVMOPTIONS=-Dservice.waitHint=1
%prunsrv% //SS//%html_srv_name% --StopTimeout=2
%prunsrv% //DS//%html_srv_name%


















