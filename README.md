# scalanda

[![Build Status](https://travis-ci.org/msilb/scalanda.svg?branch=master)](https://travis-ci.org/msilb/scalanda)
[![Coverage Status](https://coveralls.io/repos/msilb/scalanda/badge.svg?branch=master)](https://coveralls.io/r/msilb/scalanda?branch=master)
[![Codacy Badge](https://www.codacy.com/project/badge/2bb6da8abeee48b483a7d9ee8d88d65f)](https://www.codacy.com/public/me_7/scalanda)

Scalanda is a light-weight Scala/Akka/Spray-based wrapper for Oanda's REST and Stream APIs, which supports completely asynchronous non-blocking communication with the API. If you are using (or planning to use) Oanda as a broker for your automated trading needs, this library might be of interest.

# Install

If you are using `sbt` just drop this dependency into your `build.sbt`:

```scala
libraryDependencies += "com.msilb" %% "scalanda" % "0.3.7"
```

# Usage

For the full description of Oanda's REST and Stream APIs please consult their great [documentation](http://developer.oanda.com/rest-live/introduction).

## Creating an Instance of the REST Connector

From within your Akka system instantiate the REST connector actor like this:

```scala
val restConnector = system.actorOf(RestConnector.props(Practice, Some(authTokenPractice), practiceAccountId))
```

where:

* `Practice` indicates you want to connect to Oanda's fxTrade Practice environment. Other possible values are `SandBox` and `Production`.
* `authTokenPractice` is your Personal Access Token. For details how to obtain one, please see [here](http://developer.oanda.com/rest-live/authentication). *Note: `SandBox` environment does not require authentication, hence just use `None`*
* `practiceAccountId` is your fxTrade Practice account ID.

## Sending Requests to the REST Connector

After creating an instance of the REST connector you can use it from within your trading actors to send requests to the API, e.g. to create a market order to buy 10,000 units of EUR/USD send this message to your `restConnector`:

```scala
restConnector ! CreateOrderRequest("EUR_USD", 10000, Buy, Market)
```

Once the request is completed `restConnector` will reply with an instance of `CreateOrderResponse` containing details of the successfully created trade. Check out the full list of possible [request](https://github.com/msilb/scalanda/blob/master/src/main/scala/com/msilb/scalanda/restapi/Request.scala) and [response](https://github.com/msilb/scalanda/blob/master/src/main/scala/com/msilb/scalanda/restapi/Response.scala) types.

## Subscribing for Price and Event Streams Using the Stream API

Create a new connector actor for the Stream API and send `Connect` message to it:

```scala
val streamingConnector = system.actorOf(StreamingConnector.props)
streamingConnector ! Connect(Practice, Some(authTokenPractice))
```

then wait until `streamingConnector` responds with the `ConnectionEstablished` message. Register listeners that should be notified of price tick or event updates by sending

```scala
streamingConnector ! AddListeners(Set(listener1, listener2, listener3))
```

to the `streamingConnector`.

Finally, subscribe for the price stream with

```scala
streamingConnector ! StartRatesStreaming(accountId, instruments, sessionIdOpt)
```

or for the events stream with

```scala
streamingConnector ! StartEventsStreaming(accountIdsOpt)
```

For further information on request / response parameters see [Oanda Stream API specification](http://developer.oanda.com/rest-live/streaming).

# Contributing

1. Fork it!
2. Create your feature branch: git checkout -b my-new-feature
3. Commit your changes: git commit -am 'Add some feature'
4. Push to the branch: git push origin my-new-feature
5. Submit a pull request :D

# License

[MIT License](https://github.com/msilb/scalanda/blob/master/LICENSE)
