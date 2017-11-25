# Http4s Resource Example

Simple example of resource sharing pattern utilizing fs2 and http4s. 
Resources modified asynchronously are created in server startup. Then passed to services that require them.

Server Automatically Terminates After 30 seconds.

Endpoints are 

```
0.0.0.0:8080/counter
0.0.0.0:8080/timer
```
