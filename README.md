# html-service

A light-weight java web server, used for registering as Windows Service, based upon Jetty 12.

# Quick Start

* Test the Java application

```
    git clone
    Eclipse -> Open Exist Projects from File System
    Create Debug Instance (Run -> Debug Configuration, create a Java application), set working folder to src/main/webapp
    Change WEB-INF/html-service.json/paths[0].resource to content folder
    Place some html page in the folder
    Update the Maven project
    Run the Debug application
```

Now visit:

```
    http://127.0.0.1:{port}/{path}
```

where the port and the (relative from webapp) path are configured in html-service.json. 

* Or Test Without Eclipse

```
    cd java
    mvn clean compile package
    cd src/main/webapp
    java -jar ../../../target/html-web-0.1.2.jar
    # Ctrl+C for quite
```

# Configure resource paths (v 0.1.7)

WEB-INF/html-service.josn:

```
    "paths": [
        {"path": "/*", "resource": "./pm/home"},
        {"path": "/facts/*", "resource": "./pm/products"},
        {"path": "/album-web/*", "resource": "$ALBUM_WEB/web-dist"}
    ]
```

where *$ALBUM_WEB* is a environment variable. Starting the server with

```
    java -DALBUM_WEB=../../../../../../anclient/examples/example.js/album -jar html-web-0.1.7.jar 
```
the actual path of resources (on Windows 11) are:

```
    root path(/): [abolute-path]\html-service\java\src\main\webapp\.
    /*:           ./pm/home -> [abolute-path]\html-service\java\src\main\webapp\pm\home
    /facts/*:     ./pm/products -> [abolute-path]\html-service\java\src\main\webapp\pm\products
    /album-web/*: $ALBUM_WEB/web-dist -> [abolute-path]\anclient\examples\example.js\album\web-dist
```

# Install as a Windows Service

* Install the service

*For Windows only. Verified on Windows 11, IA64.*

Download [commons-daemon-1.4.1-bin-windows.zip](https://downloads.apache.org/commons/daemon/binaries/windows/commons-daemon-1.4.1-bin-windows.zip).

Check the jdk-17/bin/server/jvm.dll property, unzip the correct version, WIN32 or AMD64
prunmgr.exe into html-service/java.

```
    mvn clean compile package
    src/test/install-html-srv.bat
```

Allow the installation and trust the previlege requires. There should be the
service, *html-service-test*, in the Windows Service Control. Then open a browser
and visit the page.

Alternatively, the release section provides a package that includes everything.
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

Changes can only be applied after re-install the service.

Installing and running logs should be created in the current folder, e.g.

```
    logs/html-service-test-stderr.yyyy-mm-dd.log

    java.io.FileNotFoundException: Unable to find base-resource for [web-dist-0.4]
	at io.oz.srv.HtmlServer.newServer(HtmlServer.java:99)
	at io.oz.srv.HtmlServer._main(HtmlServer.java:69)
	at io.oz.srv.HtmlServer.jvmStart(HtmlServer.java:36)
```

This is the error in json file showing that the folder *web-dist-0.4* hasn't been found.

# How to help

I am struggling to find a simple way of testing / serving static html pages.
See [a quick survey here](https://odys-z.github.io/notes/topics/winsrv.html).

If any better ideas, please leave your comments in the issue section. 
