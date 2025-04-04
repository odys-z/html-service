# html-service

A light-weight java web server, used for registering as Windows Service, based upon Jetty 12.

# Quick Start

* Test the Java application

```
    git clone
    Eclipse -> Open Exsist Projects from File System
    Create Debug Instance (Run -> Debug Configuration, create a Java application), set working folder to src/main/webapp
    Chang WEB-INF/html-service.json/paths[0].resource to content folder
    Place some html page in the folder
    Update the Maven project
    Run the Debug application
```

Now visit:

```
    http://127.0.0.1:{port}/{path}
````

where the port and path are configured in html-service.json. 

* Install the service

For Windows only. Verified on Windows 11, IA64.

Download [commons-daemon-1.4.1-bin-windows.zip](https://downloads.apache.org/commons/daemon/binaries/windows/commons-daemon-1.4.1-bin-windows.zip).

Check the jdk-17/bin/server/jvm.dll property, unzip correct version, WIN32 or AMD64
prunmgr.exe into html-service/java.

```
    mvn clean compile package
    src/test/install-html-srv.bat
```

Allow the installation and trust the previllege requires. There should be the
service, *html-service-test*, in the Windows Service Control. Then open a browser
and visit the page.

Alternatively, the release section provides a package includes everthing.
Download and unzip it, then in the folder, run

```
    src/test/install-html-srv.bat
```


* uninstall the service

In html-service/java folder, run

```
    src/test/install-html-srv.bat
```

* Configure html-service.json

The file is in folder WEB-INF. Currently only one url path is allowed in *paths*.

Changes can only be appied after re-install the service.

# How to help

I am struggling to find a simple way of testing / serving static html pages.
See [here]()

If any better ideas, please leave your comments in the issue section. 

