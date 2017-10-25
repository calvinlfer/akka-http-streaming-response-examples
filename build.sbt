name := "akka-http-streaming-example"

version := "1.0"

scalaVersion := "2.12.3"

libraryDependencies ++= {
  val akka = "com.typesafe.akka"
  val akkaV = "2.4.20"
  val akkaHttpV = "10.0.6"
  val scalaTestV = "3.0.0"
  Seq(
    akka                        %% "akka-actor"                           % akkaV,
    akka                        %% "akka-testkit"                         % akkaV % "test",
    akka                        %% "akka-slf4j"                           % akkaV,
    akka                        %% "akka-http-core"                       % akkaHttpV,
    akka                        %% "akka-http-spray-json"                 % akkaHttpV,
    "de.heikoseeberger"         %% "akka-sse"                             % "3.0.0",
    akka                        %% "akka-http-testkit"                    % akkaHttpV,
    "org.scalactic"             %% "scalactic"                            % scalaTestV,
    "org.scalatest"             %% "scalatest"                            % scalaTestV % "test",
    "ch.qos.logback"            % "logback-classic"                       % "1.1.7",
    "org.codehaus.groovy"       % "groovy"                                % "2.4.7"
  )
}
