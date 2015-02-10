# scalanda

[![Build Status](https://travis-ci.org/msilb/scalanda.svg?branch=master)](https://travis-ci.org/msilb/scalanda)
[![Coverage Status](https://coveralls.io/repos/msilb/scalanda/badge.svg?branch=master)](https://coveralls.io/r/msilb/scalanda?branch=master)
[![Codacy Badge](https://www.codacy.com/project/badge/2bb6da8abeee48b483a7d9ee8d88d65f)](https://www.codacy.com/public/me_7/scalanda)

Scalanda is a light-weight Scala/Akka/Spray-based wrapper for Oanda's REST and Stream APIs, which supports completely asynchronous non-blocking communication with the API. If you are using (or planning to use) Oanda as a broker for your automated trading needs, this library might be of interest.

# Install

If you are using `sbt` just drop this dependency into your `build.sbt`:

```scala
libraryDependencies += "com.msilb" %% "scalanda" % "0.3.0"
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
restConnector ! CreateOrderRequest("EUR_USD", 10000, "buy", "market")
```

Once the request is completed `restConnector` will reply with an instance of `CreateOrderResponse` containing details of the successfully created trade.

For a full list of supported `Request`s and `Response`s please look at the source code file [`RestConnector.scala`](src/main/scala/com/msilb/scalanda/restapi/RestConnector.scala).

## Listening for Account Events Using the Stream API

Create a new connector actor for the Stream API in the same way as you did for the REST API:

```scala
val accountEventListener = system.actorOf(AccountEventListener.props(Practice, Some(authTokenPractice), Map(testAccountd1 -> listenersForTestAccountId1, testAccountId2 -> listenersForTestAccountId2)))
```

where

* `testAccountId`<sub>i</sub> are the IDs of the accounts you want to listen for updates.
* `listenersForTestAccountId`<sub>i</sub> are sequences of `ActorRef`s that should be notified of updates on account `testAccountId`<sub>i</sub>.

For a full list of supported `Transaction` events please look at the source code file [`AccountEventListener.scala`](src/main/scala/com/msilb/scalanda/streamapi/AccountEventListener.scala).
