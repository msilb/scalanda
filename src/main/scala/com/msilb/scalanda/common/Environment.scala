package com.msilb.scalanda.common

sealed trait Environment {
  def restApiUrl(): String

  def streamApiUrl(): String

  def authenticationRequired(): Boolean
}

object Environment {

  case object SandBox extends Environment {
    val restApiUrl = "http://api-sandbox.oanda.com"
    val streamApiUrl = "http://stream-sandbox.oanda.com"
    val authenticationRequired = false
  }

  case object Practice extends Environment {
    val restApiUrl = "https://api-fxpractice.oanda.com"
    val streamApiUrl = "https://stream-fxpractice.oanda.com"
    val authenticationRequired = true
  }

  case object Production extends Environment {
    val restApiUrl = "https://api-fxtrade.oanda.com"
    val streamApiUrl = "https://stream-fxtrade.oanda.com"
    val authenticationRequired = true
  }

}
