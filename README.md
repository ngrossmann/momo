Store time-series data in [Couchbase](http://www.couchbase.com/) and display
with [Grafana](http://grafana.org/).

## Configure Couchbase

By default Momo connects to Couchbase at 127.0.0.1:8092 using bucket
``default`` and no password. This can be changed by editing
`etc/application.conf` if you cloned the repository or
`/etc/momo/application.conf` if you installed the package.

Add a section like:

```
couchbase {
  cluster = ["127.0.0.1:8092"]
  bucket = "default"
  password = ...
}
```

Momo requires two view documents which can be created by running
momo-create-view.

In case you cloned the source code:

```
$ src/main/shell/momo-create-views -b default -s src/main/couchbase \
  -n localhost:8092
-b 'default' -s 'src/main/couchbase' -n 'localhost:8092' --
{"ok":true,"id":"_design/dashboards"}
{"ok":true,"id":"_design/targets"}
```

In case you installed the DEB/RPM:

```
$ /usr/sbin/momo-create-views -b default -n localhost:8092
```


## Build from Source

### Prerequisites

* [sbt](http://sbt-scala.org/)
* npm/nodejs (on Debian/Ubuntu install packages npm, nodejs, nodejs-legacy)
* configure Couchbase as described in ``Configure Couchbase`` (if you want to run directly from SBT)
* edit etc/application.conf to point to your Couchbase cluster/instance.
 
### Run Inside SBT

After cloning the repository cd into the root directory. 

```
$ sbt
\[info\] Loading global plugins from ...
\[info\] Loading project definition from ...
\[info\] Set current project to momo (in build file:.../momo/)
> copy
...
\[info\] Done, without errors.
\[info\] Copying Grafana to /home/niklas/Development/scala/momo/target/scala-2.11/classes/grafana
\[success\] Total time: 127 s, completed May 7, 2015 8:06:52 AM
> reStart
```

The `copy` task runs grunt to build grafana, it will output
lots of messages on ``error`` level can be ignored usually (SBT reports
them as error as grunt seems to write to stderr). If you are unsure if
grunt was successful check `target/scala-2.11/classes/grafana/app`, it 
should contain a  file app.<hash>.js.

`reStart` compiles the Scala backend and starts it, to stop it type `reStop`.

### Building Packages

After running the `copy` target run `debian:packageBin` or `rpm:packageBin`.

## Feeding Data

Momo currently supports two protocols it's own HTTP/Json based protocol
and StatsD.

### StatsD

This is probably the easiest way to get data into Momo
```
echo "server.myhost.random:$RANDOM|g" | nc -u -w0 127.0.0.1 8125
```

Unlike StatsD Momo does not aggregate/count values it just pushes the what
it receives to Couchbase.

### HTTP/Json

```
 echo "{\"name\": \"server.myhost.random\", \"timestamp\ $(date  -u +'%s')000, \"value\": $RANDOM}" \
  | curl -d@- -H Content-Type:application/json http://localhost:8080/series
```

### Using Diamond

[Diamond](https://github.com/python-diamond/Diamond) is an easy to use agent
to collect system and
[all other kinds](https://github.com/python-diamond/Diamond/wiki/Collectors)
of metrics. To feed data from Diamond to Momo install python-statsd and 
configure the 
[StatsD handler](https://github.com/python-diamond/Diamond/wiki/handler-StatsdHandler)
in /etc/diamond/diamond.conf:

```
# Handlers for published metrics.
handlers = diamond.handler.stats_d.StatsdHandler
...
[[StatsdHandler]]
host = <momo host>
port = 8125
```
