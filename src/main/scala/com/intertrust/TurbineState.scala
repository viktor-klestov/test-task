package com.intertrust

import com.intertrust.protocol.TurbineStatus

import java.time.Instant

class TurbineState(
                    var lastStatus: TurbineStatus,
                    var lastStatusChangeTime: Instant,
                    var technicianLeftTime: Option[Instant]
                  ) {
}

object TurbineState {
  val initialState = new TurbineState(TurbineStatus.Working, Simulator.simulateSince, Option.empty)
}