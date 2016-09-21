name := "akka-http-streaming-example"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= {
  val akka = "com.typesafe.akka"
  val akkaV       = "2.4.10"
  Seq(
    akka                        %% "akka-actor"                           % akkaV,
    akka                        %% "akka-slf4j"                           % akkaV,
    akka                        %% "akka-http-core"                       % akkaV,
    akka                        %% "akka-http-experimental"               % akkaV,
    akka                        %% "akka-http-spray-json-experimental"    % akkaV,
    "ch.qos.logback"            % "logback-classic"                       % "1.1.7",
    "org.codehaus.groovy"       % "groovy"                                % "2.4.7"
  )
}