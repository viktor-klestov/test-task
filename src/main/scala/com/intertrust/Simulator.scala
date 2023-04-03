package com.intertrust

import com.intertrust.parsers.{MovementEventStream, TurbineEventStream}
import com.intertrust.processing.AlertsSink
import com.intertrust.protocol.Movement.Enter
import com.intertrust.protocol.{Event, MovementEvent, TurbineEvent}
import zio.stream.ZStream
import zio.{NonEmptyChunk, Schedule, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, durationInt}

import java.time.{Instant, LocalDate, ZoneOffset}
import scala.io.Source

object Simulator extends ZIOAppDefault {
  val simulateSince: Instant = LocalDate.parse("2015-11-23").atStartOfDay(ZoneOffset.UTC).toInstant;
  val speedupFactor = 1 // 1 real second = 1 minute from log files, can not be less than 1

  override def run: ZIO[ZIOAppArgs with Scope, Any, Any] = {
    val movementEvents = MovementEventStream.fromSource(Source.fromURL(getClass.getResource("/movements.csv")))
    val turbineEvents = TurbineEventStream.fromSource(Source.fromURL(getClass.getResource("/turbines.csv")))
    val alertsSink = AlertsSink.console
    // make sure that streams groped by timestamps are of equal size, so we can zip them
    val fill = (g: (Instant, (Instant, NonEmptyChunk[Event]))) => ZStream.iterate(g._1.plusSeconds(60))(last => last.plusSeconds(60)).takeWhile(i => i.isBefore(g._2._1)).map(_ => Option.empty) ++ ZStream(Option(g._2))
    val byDates = (events: ZStream[Scope, Throwable, Event]) => events.filter(t => !t.timestamp.isBefore(simulateSince))
      .groupAdjacentBy(t => t.timestamp)
      .mapAccum(simulateSince)((last, cur) => (cur._1, (last, cur)))
      .flatMap(fill)
    val eventByDates = byDates(turbineEvents).zip(byDates(movementEvents))
    val state = new SimulatorState
    ZStream.fromSchedule(Schedule.fixed(1.seconds))
      .flatMap(_ => ZStream.range(1, speedupFactor + 1))
      .zip(eventByDates)
      .flatMap(g => {
        val events = g._2
        val m = if (events._1.nonEmpty) ZStream.fromChunk(events._1.get._2) else ZStream()
        val t = if (events._2.nonEmpty) ZStream.fromChunk(events._2.get._2) else ZStream()
        m ++ t
      })
      .map {
        case a: MovementEvent => state.apply(a)
        case a: TurbineEvent => state.apply(a)
      }
      .filter(o => o.nonEmpty)
      .map(o => o.get)
      .run(alertsSink)
  }
}
