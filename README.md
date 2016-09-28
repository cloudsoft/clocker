# Clocker 2.0

## Components

### [Docker](./common/src/main/resources/docker/)

### [Swarm](./swarm/src/main/resources/swarm/)

### [Kubernetes](./kubernetes/src/main/resources/kubernetes/)

## Getting Started

### Add Clocker to Brooklyn (Karaf Edition)

Add the YAML below (note this assumes `clocker-swarm` and `clocker-kubernetes` JARs hosted)

```YAML
brooklyn.catalog:
  brooklyn.libraries:
    - "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.etcd&a=brooklyn-etcd&v=2.3.0-SNAPSHOT"
    # TODO Replace below URLs with link to release JARS
    - "http://localhost:8000/io/brooklyn/clocker/clocker-common/2.0.0-SNAPSHOT/clocker-common-2.0.0-SNAPSHOT.jar"
    - "http://localhost:8000/io/brooklyn/clocker/clocker-swarm/2.0.0-SNAPSHOT/clocker-swarm-2.0.0-SNAPSHOT.jar"
    - "http://localhost:8000/io/brooklyn/clocker/clocker-kubernetes/2.0.0-SNAPSHOT/clocker-kubernetes-2.0.0-SNAPSHOT.jar"
  items:
    - classpath://io.brooklyn.etcd.brooklyn-etcd:brooklyn-etcd/catalog.bom
    - classpath://io.brooklyn.clocker.swarm:swarm/catalog.bom
    - classpath://io.brooklyn.clocker.kubernetes:kubernetes/catalog.bom
```

### Add Clocker to Brooklyn (Standard Edition)

You must add the following JARs to the `lib/dropins` folder:

*   [brooklyn-etcd](https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn.etcd&a=brooklyn-etcd&v=2.3.0-SNAPSHOT)
*   [common](./common)
*   [swarm](./swarm)
*   [kubernetes](./kubernetes)

```YAML
brooklyn.catalog:
  items:
    - classpath://brooklyn-etcd/catalog.bom
    - classpath://swarm/catalog.bom
    - classpath://kubernetes/catalog.bom
```
