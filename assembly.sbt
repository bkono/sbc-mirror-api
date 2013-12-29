import sbtassembly.Plugin._
import AssemblyKeys._ // put this at the top of the file

assemblySettings

// your assembly settings here

mainClass in assembly := Some("com.example.Boot")

jarName in assembly := "example-service.jar"
