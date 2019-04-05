# JMXQuery
This is a modified version JMXQuery forked from https://github.com/dgildeh/JMXQuery
This tool provides a command line interface to query a JMX/RMI server to capture metrics.

This version has been designed for high performance monitoring by leverating a NailGun server to "stick" the JMXQuery in JVM memory.
The Nailgun project is located at https://github.com/facebook/nailgun.
Nailgun can hold multiple instances of a class in memory pool, allowing for multiple threads to re-use these commands without starting up a new JVM.
This reduces the execution time by 70% for JMX calls.  A special nailMain function is used to support passing extra info to the JMXQuery application.

The code was also stripped down to only return JSON formatted output.

The last change was modifying the JMX Connection class to support unique requirements for Weblogic and the Tuxedo rmi repositories.

Weblogic domains need to implement an extra provider:

`env.put(JMXConnectorFactory.PROTOCOL_PROVIDER_PACKAGES, "weblogic.management.remote");`

Tuxedo uses a different method to authenticate with the "jmx.remote.credentials" environment variable.


This program is executed through a Nailgun Client.  The client connects to the nailgun server through the **NailgunConnection** Class.  
This class will use an efficient unix socket to connect to the Nailgun server.  The client will then request to have the Nailgun server to run a command, being this program.
The command will follow the same parameters as if ran directly at command line.   However, the Nailgun sever will keep the Class 
in memory after it has completed the request so others can re-use the command/instance.

This method is being used for a Pyhton Monitoring engine to feed metrics to Splunk (internal PeopleSoft monitoring solution), 
as well as a custom PeopleSoft MetricBeat to feed metric data to Elasticsearch/Kibana (public project).  

The Nailgun client is implemented in several programming languages: C, Python, golang.
