# Play REST API for ICS Prototype

This is an example project based on [Making a REST API in Play](http://developer.lightbend.com/guides/play-rest-api/index.html) 
that shows how you could make a REST API for the Galil prototype HCD using the Play Framework.

This project depends on `csw`. Be sure to run `sbt publishLocal stage` in that project first.

### Running

This play server assumes that the csw-prod location service (csw-cluster-seed), 
galil simulator (or local device), galil-hcd and stage assembly running.


Start the csw services: 

```
csw-services.sh start 
```


Start the Galil Prototype HCD (aps-ics-prototype2 branch) (see project for instructions)

Start the aps-ics-stage-assembly (see project for instructions)


First login with AAS system using:
```
csw-config-cli login --consoleLogin
and use the backdoor login if you know it
```
You can run the server with sbt:

    sbt run

or after running `sbt stage`, with:

    ./target/universal/stage/bin/galil-play-rest-api

Play will start up on the HTTP port at http://localhost:9000/.   

### Usage

If you send this URL from the command line, you’ll see the JSON result. 
Using httpie, you can execute the command:

```
http POST 'http://localhost:9000/v1/gs/setRelTarget?axis=A&count=2'
```

and get back something like:

```
"Completed(RunId(673dc403-7184-4b9d-9eca-3e327c5e14ab))"
```

Or:

```
http GET 'http://localhost:9000/v1/gs/getRelTarget?axis=A'
```

returns:

```
"CompletedWithResult(RunId(18d51eeb-8863-48cf-8e37-86716e3d74a5),Result([TEST, test.galil.server](counts((2)NoUnits))))"
```


