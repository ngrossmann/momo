# Overview

Momo is Scala/Akka based application to manage time-series data in 
[Couchbase](http://www.couchbase.com/) and display it 
using [Grafana](http://grafana.org/).

Features:

* Integrates easily with other monitoring tools due to StatsD
compatible interface.
* REST interface to push metrics and query time-series.
* Grafana built-in no need for additional web-server.
* Minimum configuration required to get started.

# Getting Started

## Installing Binary Packages

Running Momo should be easy:

1. Download and install or extract the package (
[tar](https://s3-eu-west-1.amazonaws.com/net.n12n.momo/momo-0.4.1.tgz), 
[deb](https://s3-eu-west-1.amazonaws.com/net.n12n.momo/momo_0.4.1_all.deb),
[rpm](https://s3-eu-west-1.amazonaws.com/net.n12n.momo/momo-0.4.1-0.noarch.rpm)).
2. Add the Couchbase connection information to application.conf.
3. Create Couchbase views.
4. Point your browser to [http://localhost:8080](http://localhost:8080/)

The location of files depends on the package you downloaded.
If you installed the RPM or DEB the `application.conf` can
be found in `/etc/momo`, `momo` the start script in `/usr/bin`
and all other files, e.g. view definition in `/usr/share/momo`.

If you extracted the tarball the respective files are in `etc`,
`bin` and `share`

## Configure Couchbase

By default Momo connects to Couchbase at 127.0.0.1:8092 using bucket
`default` and no password. This can be changed by editing
`application.conf`.

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

DEB/RPM:

```
$ /usr/sbin/momo-create-views -b default -n localhost:8092
```

Tarball: 

```
$ bin/momo-create-views -b default -n localhost:8092 -s share
```

`momo-create-views` Parameters:

* `-b`: bucket name (default default)
* `-s`: directory containing view definition (default /usr/share/momo)
* `-n`: Couchbase node name (default localhost:8092)
* `-p`: Prompt for bucket password

## Other Configuration Settings

All default settings can be found in
[reference.conf](https://github.com/ngrossmann/momo/blob/master/src/main/resources/reference.conf)
and overridden in `etc/application.conf`.

Disable [Kamon](http://kamon.io/) (Akka metrics):

```
akka {
  extensions = [ ]
}
```

Listen addresses for http and the statsd interface:

```
momo {
  http {
    listen-address = "0.0.0.0"
    port = 8080
  }

  statsd {
    listen-address = "localhost"
    port = 8125
  }
  ...
}
```

Metric settings:

```
momo {
  metric-ttl = 1 day
  document-interval = 10 minutes
  ...
}
```

* `metrics-ttl`: Time-to-live for all metrics. As Couchbase keeps all data
in memory this setting depends highly on the number of data-points you collect
and the size of your Couchbase cluster. 
* `document-interval`: Momo does not create a new document per data point,
but a new document for each time-series per interval. The default is
10 minutes, intended to be used with collection intervals of few seconds.
If you collect metrics in minute intervals you may want to increase this
to `60 minutes` or more.

# Using Momo

## Feeding Data

Momo currently supports two protocols it's own HTTP/Json based protocol
and StatsD.

### StatsD

This is probably the easiest way to get data into Momo
```
echo "server.myhost.random:$RANDOM|g" | nc -u -w0 127.0.0.1 8125
```

Unlike StatsD Momo does not aggregate/count events to build time-series, 
it expects to receive values at a regular interval and pushed those 
values directly to Couchbase.

### HTTP/Json

```
 echo "{\"name\": \"server.myhost.random\", \"timestamp\ $(date  -u +'%s')000, \"value\": $RANDOM}" \
  | curl -d@- -H Content-Type:application/json http://localhost:8080/series
```

### Using Diamond

[Diamond](https://github.com/python-diamond/Diamond) is an easy to use agent
to collect system and
[all other kinds](https://github.com/python-diamond/Diamond/wiki/Collectors)
of metrics. To feed data from Diamond to Momo install the python-statsd
package and configure the 
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

## Configuring Dashboards

The dashboard is a vanilla [Grafana 1.9](http://docs.grafana.org/v1.9/),
most things work as described in the documentation, this section 
documents what is Momo specifc.

### Queries

A single time-series can be queried by its name e.g. just enter
`momo.hostname.router.user_db_metric.processing-time_ms` in the metric
name field.

Multiple time-series are queries using scala regular expressions
enclosed in `/.../`. To query the same time-series as above but 
for all hosts use something like
`/momo.[^.]*.router.user_db_metric.processing-time_ms/`

The matching time-series can be aggregated into one time series
by setting `Merge` to true; how the values are merged depends on
the `Aggregator` setting. `Aggregation Time` should be set to
the collection interval or a multiple of it.

### Templating Queries

Grafana supports templates based on DB queries Momo has basic
support for this. To define a `hostname` template variable 
selecting all hosts which send the metric 
`momo.<hostname>.router.user_db_metric.processing-time_ms`
from above use `router.user_db_metric.processing-time_ms` as
variable values query. Currently this query returns all
time series which contain the query as substring.

As regex you could use `/[^.]*\.([^.]*)\..*/` to cut out
the hostname.

# Build From Source

## Prerequisites

* JDK 7 or 8
* [sbt](http://sbt-scala.org/)
* npm/nodejs (on Debian/Ubuntu install packages npm, nodejs, nodejs-legacy)
* configure Couchbase as described in ``Configure Couchbase`` (if you want to run directly from SBT)
* edit etc/application.conf to point to your Couchbase cluster/instance.
 
## Run Inside SBT

After cloning the repository cd into the root directory. 

```
$ sbt
> reStart
```

During the build you will see error messages like 
```
[info] Running "uglify:dest" (uglify) task
[error] WARN: Dropping unused function argument url [dist/app/app.53caa7d6.js:1778,50]
[error] WARN: Dropping unused function argument moduleName [dist/app/app.53caa7d6.js:1778,38]
```
They can be ignored. sbt calls grunt to build the java-script UI, grunt
logs the WARN messages to stderr which is highlighted by sbt as error.

`reStart` compiles the Scala backend and starts it, to stop it type `reStop`.

## Building Packages

Native Linux packages (both tasks require the respective build tools):

* `debian:packageBin`
* `rpm:packageBin`.

Tar.gz:

* `universal:packageZipTarball`
