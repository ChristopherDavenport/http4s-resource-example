# Http4s Resource Example

Simple example of resource sharing pattern utilizing fs2 and http4s. 
Resource modified asynchronously are created in server startup. Then passed to services that require them.