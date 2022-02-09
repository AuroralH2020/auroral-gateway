![GitHub tag (latest by date)](https://img.shields.io/github/v/tag/AuroralH2020/auroral-gateway)
![GitHub code size in bytes](https://img.shields.io/github/languages/code-size/AuroralH2020/auroral-gateway)
![GitHub issues](https://img.shields.io/github/issues-raw/AuroralH2020/auroral-gateway)
![GitHub](https://img.shields.io/github/license/AuroralH2020/auroral-gateway)

# AURORAL NODE GATEWAY #

This README documents the agent component of the AURORAL gateway, which is funded by European Union’s Horizon 2020 Framework Programme for Research and Innovation under grant agreement no 101016854 AURORAL.

### Dependencies ###

AURORAL Node Gateway is a part of the AURORAL Node, which is a client to connect IoT infrastructures with AURORAL. It depends on a Redis instance for persistance.

### Deployment ###

Refer to AURORAL Node repository for deployment

### Images ###

Available for AMD64, ARM64 and ARM7 architectures.

### Build binaries ###

mvn clean package

### Fix issues in raspberry pi ###

Not imported certificates:

* Prevent missing cert in keystore error in RaspberryPi *
RUN echo | openssl s_client -showcerts -servername auroral.dev.bavenir.eu -connect auroral.dev.bavenir.eu:443 2>/dev/null | openssl x509 > keystore/cert.pem
RUN openssl x509 -in keystore/cert.pem -out keystore/my-ca.der -outform DER 
RUN keytool -import -trustcacerts -noprompt -alias local-CA -storepass changeit \
    -keystore /usr/lib/jvm/java-8-oracle/jre/lib/security/cacerts \
    -file keystore/my-ca.der

### Who do I talk to? ###

Developed by bAvenir

* jorge.almela@bavenir.eu
* peter.drahovsky@bavenir.eu

### Old documentation ###

# VICINITY Open Gateway API #
The standalone VICINITY Open Gateway API enables your IoT infrastructure to interconnect with other IoT infrastructures and services through VICINITY P2P Network by using HTTP REST requests. Among its features there are devices and services registration,  retrieving and setting a property on remote objects, executing an action, or subscribing to an event channel and receiving asynchronously fired event whenever one is published.

# Getting started with VICINITY Open Gateway API #
This “simple:)” get started guide provides step by step approach to integrate IoT infrastructure in VICINITY.           
https://vicinity-get-started.readthedocs.io/en/latest/getstarted.html

# VICINITY Open Gateway REST API description #
For more information about HTTP REST requests please visit complete REST API description.
https://vicinityh2020.github.io/vicinity-gateway-api/#/
