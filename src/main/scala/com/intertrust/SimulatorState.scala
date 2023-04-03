package com.intertrust

import com.intertrust.protocol.Movement.{Enter, Exit}
import com.intertrust.protocol._

import java.time.Duration
import scala.collection.mutable

class SimulatorState {
  private val turbines = new mutable.HashMap[String, TurbineState]()
  private val engineers = new mutable.HashMap[String, Engineer]()

  def apply(movementEvent: MovementEvent): Option[MovementAlert] = {
    val engineer = engineers.getOrElseUpdate(movementEvent.engineerId, new Engineer(Option.empty))
    if (movementEvent.movement == Exit && (engineer.location.isEmpty || engineer.location.get != movementEvent.location)) {
      return Option(MovementAlert(movementEvent.timestamp, movementEvent.engineerId, "Engineer exited location that never entered"))
    }
    if (movementEvent.movement == Enter) {
      if (engineer.location.nonEmpty) {
        return Option(MovementAlert(movementEvent.timestamp, movementEvent.engineerId, "Engineer entered location but never left previous"))
      }
      engineer.location = Option(movementEvent.location)
    } else {
      engineer.location = Option.empty
    }
    if (movementEvent.movement == Exit && movementEvent.location.isInstanceOf[Turbine]) {
      val turbine = turbines.get(movementEvent.location.id)
      if (turbine.isEmpty) {
        return Option(MovementAlert(movementEvent.timestamp, movementEvent.engineerId, "Unknown turbine"))
      }
      turbine.get.technicianLeftTime = Option(movementEvent.timestamp)
    }
    Option.empty
  }

  def apply(turbineEvent: TurbineEvent): Option[TurbineAlert] = {
    val lastState = turbines.getOrElseUpdate(turbineEvent.turbineId, TurbineState.initialState)
    turbines.put(turbineEvent.turbineId, new TurbineState(turbineEvent.status, turbineEvent.timestamp, lastState.technicianLeftTime))
    if (turbineEvent.status == TurbineStatus.Working) {
      turbines.put(turbineEvent.turbineId, new TurbineState(turbineEvent.status, turbineEvent.timestamp, Option.empty))
      return Option.empty
    }
    if (lastState.lastStatus == TurbineStatus.Working && turbineEvent.status == TurbineStatus.Broken) {
      return Option(TurbineAlert(turbineEvent.timestamp, turbineEvent.turbineId, "Turbine is broken"))
    }
    if (Duration.between(turbineEvent.timestamp, lastState.lastStatusChangeTime).toSeconds > 4 * 3600 && lastState.technicianLeftTime.isEmpty) {
      return Option(TurbineAlert(turbineEvent.timestamp, turbineEvent.turbineId, "Turbine is not working > 4 hours"))
    }
    if (lastState.technicianLeftTime.nonEmpty && Duration.between(turbineEvent.timestamp, lastState.technicianLeftTime.get).toSeconds > 3 * 60) {
      return Option(TurbineAlert(turbineEvent.timestamp, turbineEvent.turbineId, "Engineer left turbine broken"))
    }
    Option.empty
  }
}
