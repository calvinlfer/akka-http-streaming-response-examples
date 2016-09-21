# Examples using Akka HTTP with Streaming
A list of examples that involve streaming with Akka Streams and used together with Akka HTTP. 
The Akka-HTTP specific details are isolated into the [Routes trait](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/Routes.scala).
The initialization logic to start the Akka HTTP server is kept in [ServerMain](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/ServerMain.scala).

Focusing on the Routes area, there are two HTTP Chunked Streaming endpoints:
* [`streaming-text`](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/Routes.scala#L26) - This uses predefined Sources and throttling in order to give the user a chunked response 
* [`actor-text`](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/Routes.scala#L42) - This is more involved, we define an [Actor that respects Akka Streams backpressure](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/BackpressuredActor.scala) and then create a Source from this. 
We follow up by using the Akka Scheduler to constantly tell the Actor to emit messages into the Stream that the user sees. 
If you use a web browser and stop the streaming response then you will cancel the stream and shut down the actor. 
Feel free to open this up on multiple browsers since we allocate an actor per request

There is also a websocket endpoint:
* [`ws-simple`](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/Routes.scala#L59) - This can be accessed via `ws://localhost:9000/ws-simple`, it uses a stateless Akka Streams Flow in order to echo back the message. 
Websocket clients can be found here: [Online WebSocket Tester](https://www.websocket.org/echo.html), [Dark WebSocket Client](https://chrome.google.com/webstore/detail/dark-websocket-terminal/dmogdjmcpfaibncngoolgljgocdabhke), etc.

