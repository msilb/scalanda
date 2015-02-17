package com.msilb.scalanda.restapi

import java.time.ZonedDateTime

import com.msilb.scalanda.common.model._

sealed trait Request

object Request {

  // RATES

  case class GetInstrumentsRequest(fields: Option[Seq[InstrumentField]] = None,
                                   instruments: Option[Seq[String]] = None) extends Request

  case class GetCurrentPricesRequest(instruments: Seq[String],
                                     since: Option[ZonedDateTime] = None) extends Request

  case class GetCandlesRequest(instrument: String,
                               granularity: Option[Granularity] = None,
                               count: Option[Int] = None,
                               start: Option[ZonedDateTime] = None,
                               end: Option[ZonedDateTime] = None,
                               candleFormat: Option[CandleFormat] = None,
                               includeFirst: Option[Boolean] = None,
                               dailyAlignment: Option[Byte] = None,
                               alignmentTimeZone: Option[AlignmentTimeZone] = None,
                               weeklyAlignment: Option[WeeklyAlignment] = None) extends Request

  // ACCOUNTS

  case class GetAccountsRequest(username: Option[String] = None) extends Request

  case class CreateTestAccountRequest(currency: Option[String] = None) extends Request

  case class GetAccountInformationRequest(accountId: Int) extends Request

  // ORDERS

  case class GetOrdersRequest(maxId: Option[Int] = None,
                              count: Option[Int] = None,
                              instrument: Option[String] = None,
                              ids: Option[List[Int]] = None) extends Request

  case class CreateOrderRequest(instrument: String,
                                units: Int,
                                side: Side,
                                typ: OrderType,
                                expiry: Option[ZonedDateTime] = None,
                                price: Option[Double] = None,
                                lowerBound: Option[Double] = None,
                                upperBound: Option[Double] = None,
                                stopLoss: Option[Double] = None,
                                takeProfit: Option[Double] = None,
                                trailingStop: Option[Double] = None) extends Request

  case class GetOrderInformationRequest(orderId: Int) extends Request

  case class ModifyOrderRequest(id: Int,
                                units: Option[Int] = None,
                                price: Option[Double] = None,
                                expiry: Option[ZonedDateTime] = None,
                                lowerBound: Option[Double] = None,
                                upperBound: Option[Double] = None,
                                stopLoss: Option[Double] = None,
                                takeProfit: Option[Double] = None,
                                trailingStop: Option[Double] = None) extends Request

  case class CloseOrderRequest(orderId: Int) extends Request

  // TRADES

  case class GetOpenTradesRequest(maxId: Option[Int] = None,
                                  count: Option[Int] = None,
                                  instrument: Option[String] = None,
                                  ids: Option[List[Int]] = None) extends Request

  case class GetTradeInformationRequest(tradeId: Int) extends Request

  case class ModifyTradeRequest(id: Int, stopLoss: Option[Double] = None, takeProfit: Option[Double] = None, trailingStop: Option[Double] = None) extends Request

  case class CloseTradeRequest(tradeId: Int) extends Request

  // POSITIONS

  case object GetOpenPositionsRequest extends Request

  case class GetPositionForInstrumentRequest(instrument: String) extends Request

  case class ClosePositionRequest(instrument: String) extends Request

  // TRANSACTIONS

  case class GetTransactionHistoryRequest(maxId: Option[Int] = None,
                                          minId: Option[Int] = None,
                                          count: Option[Int] = None,
                                          instrument: Option[String] = None,
                                          ids: Option[List[Int]] = None) extends Request

  case class GetTransactionInformationRequest(transactionId: Int) extends Request

}
