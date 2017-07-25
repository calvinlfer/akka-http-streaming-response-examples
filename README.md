# Examples using Akka HTTP with Streaming
A list of examples that involve streaming with Akka Streams and used together with Akka HTTP. 
The Akka-HTTP specific details are isolated into the following traits: 

- [Chunked Streaming Routes](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/chunked/http/ChunkedStreamingRoutes.scala)
- [WebSocket Routes](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/ws/WebSocketRoutes.scala)

The initialization logic to start the Akka HTTP server is kept in [ServerMain](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/ServerMain.scala).

Focusing on HTTP Chunked Streaming Routes:

- [`streaming-text`](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/chunked/http/ChunkedStreamingRoutes.scala#L33) - This uses predefined Sources and throttling in order to give the user a chunked response in a controlled manner
- [`actor-text`](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/chunked/http/ChunkedStreamingRoutes.scala#L50) - This is more involved, we define an [Actor that respects Akka Streams backpressure](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/BackpressuredActor.scala) and then create a Source from this. 
The Akka Scheduler constantly tells the Actor to emit messages into the Stream that the user sees. This logic is placed within the Actor. If you 
use a web browser and stop the streaming response then you will cancel the stream and shut down the actor. Feel free to open this up on multiple
browsers since we allocate an actor per request
- [`alt-actor-text`](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/chunked/http/ChunkedStreamingRoutes.scala#L60) - This is similar to `actor-text` except it uses `mapMaterializedValue` 
to access the materialized ActorRef and schedules messages to be sent constantly (in addition to the scheduler sending messages inside the Actor)
- [`live-actor-text`](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/chunked/http/ChunkedStreamingRoutes.scala#L74) - This is slightly different from the other `actor` endpoints. 
It creates a live actor and places it into a Publisher and creates a Source from this. We publish messages in the same way as the previous examples
- [`streaming-json`](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/chunked/http/ChunkedStreamingRoutes.scala#L100) - This is an example of a JSON streaming route. We define a JSON Marshaller for a case class [here](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/DetailedMessage.scala) and we add a few imports for [streaming support](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/chunked/http/ChunkedStreamingRoutes.scala#L92) in order to have streaming JSON. You can customize the separators as well. 
- [`consuming-streaming-json`](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/chunked/http/ChunkedStreamingRoutes.scala#L112) - This is an example of an endpoint that consumes a Streaming JSON request. This also relies on having JSON Streaming support.
- [`streaming-sse-json`](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/chunked/http/ChunkedStreamingRoutes.scala#L125) - This is an example of Streaming JSON using [Server Sent Events](http://www.html5rocks.com/en/tutorials/eventsource/basics/) with the help of Heiko Seeberger's [Akka-SSE](https://github.com/hseeberger/akka-sse) library. SSEs have better integration with modern day browsers.

Focusing on the WebSocket Routes:

- [`ws-simple`](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/ws/WebSocketRoutes.scala#L23) - This can be accessed via `ws://localhost:9000/ws-simple`, it uses a stateless Akka Streams Flow in order to echo back the message. 
The input to the Flow represents the WebSocket messages coming from the WebSocket Client and the output to the Flow represents the messages that are being sent to the WebSocket Client
- [`ws-chat`](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/ws/WebSocketRoutes.scala#L87) - This is an implementation of an online chat-room, it shows how to integrate Actors with Streams in order to create it. All credits go to [Johan Andrén](https://markatta.com/codemonkey/blog/2016/04/18/chat-with-akka-http-websockets/) 
for his excellent work and explanations on setting this up. This example involves creating a Flow from a Source and a Sink. I have provided my explanation and a [diagram](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/ws/WebSocketRoutes.scala#L76)
to help you understand how this works. Essentially a Flow can be modelled as a Sink and a Source underneath the hood as illustrated by the diagram. If you choose to go 
about thinking in this manner then you have full control over the Flow (as in what the Flow accepts and what the Flow emits). We use Actors underneath the hood to perform
the coordination between the accepting and emitting of Flow controlling messages to and from WebSocket clients. 

## Chat Room Flow construction Overview ##
WebSocket Clients connect to the Chat-Room via `ws://localhost:9000/ws-chat` and a Flow is created. Let's take a look at the inner workings of this Flow:

- First an intermediate Actor is created (per WebSocket Client connection) that is meant to act as the bridge between the WebSocket Actor (more on this below) and the Chat Room Actor
- The Flow is composed from a Sink and a Source. Take a look at the [diagram](https://github.com/calvinlfer/akka-http-streaming-response-examples/blob/master/src/main/scala/com/experiments/calvin/ws/WebSocketRoutes.scala#L76) before coming back here. 
    - The Sink (inside the Flow) represents messages coming in from WebSocket clients, we use Sink.actorRef(intermediate Actor) so that the intermediate Actor will now receive messages whenever the WebSocket Source sends us messages
    - The Source (inside the Flow) represents messages sent to the WebSocket clients, we use Source.actorRef along with `mapMaterializedValue` to get access to the materialized ActorRef that we use to send messages to, in order for messages to be sent to the WebSocket client
- We create a Flow from the Sink and the Source which now represents each WebSocket connection between our server and the WebSocket clients

**Note:** Websocket clients can be found here: [Online WebSocket Tester](https://www.websocket.org/echo.html), [Dark WebSocket Client](https://chrome.google.com/webstore/detail/dark-websocket-terminal/dmogdjmcpfaibncngoolgljgocdabhke), etc.

## Contributions and PRs

Please feel free to send in PRs and contributions and we can review them together and see whether they are small and cohesive enough to fit into the project
