package com.intertrust.processing

import com.intertrust.protocol.{MovementAlert, TurbineAlert}
import zio.ZIO
import zio.stream.ZSink

object AlertsSink {
  val console: ZSink[Any, Nothing, Any, Nothing, Unit] = ZSink.foreach {
    case a: TurbineAlert => ZIO.logError(a.toString)
    case a: MovementAlert => ZIO.logError(a.toString)
  }
}
