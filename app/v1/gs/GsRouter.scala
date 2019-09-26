package v1.gs

import javax.inject.Inject
import csw.params.core.models.ObsId
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

/**
  * Routes and URLs.
  */
class GsRouter @Inject()(controller: GsController) extends SimpleRouter {
  val prefix = "/v1/gs"

  override def routes: Routes = {

    // assembly commands
    // initialize axis
    case POST(p"/init") =>
      controller.process()

    // home axis
    case POST(p"/home") =>
      controller.process()

    // motor off an axis
    case POST(p"/motorOff") =>
      controller.process()

    // position an axis
    case POST(p"/position") =>
      controller.process()

    // HCD commands

    case POST(p"/setBrushlessAxis" ? q_o"obsId=$maybeObsId" & q"axis=$axisStr") =>
      controller.setBrushlessAxis(maybeObsId.map(ObsId(_)), axisStr(0))

    case POST(p"/setAnalogFeedbackSelect" ? q_o"obsId=$maybeObsId" & q"axis=$axisStr" & q"interpCounts=$countStr") =>
      controller.setAnalogFeedbackSelect(maybeObsId.map(ObsId(_)), axisStr(0), countStr.toInt)

    case POST(p"/setBrushlessModulus" ? q_o"obsId=$maybeObsId" & q"axis=$axisStr" & q"brushlessModulus=$brushlessModulus") =>
      controller.setBrushlessModulus(maybeObsId.map(ObsId(_)), axisStr(0), brushlessModulus.toInt)

    case POST(p"/brushlessZero" ? q_o"obsId=$maybeObsId" & q"axis=$axisStr" & q"volts=$volts") =>
      controller.brushlessZero(maybeObsId.map(ObsId(_)), axisStr(0), volts.toDouble)

    case POST(p"/setHomingMode" ? q_o"obsId=$maybeObsId" & q"axis=$axisStr") =>
      controller.setHomingMode(maybeObsId.map(ObsId(_)), axisStr(0))

    case POST(p"/setFindIndexMode" ? q_o"obsId=$maybeObsId" & q"axis=$axisStr") =>
      controller.setFindIndexMode(maybeObsId.map(ObsId(_)), axisStr(0))

    case POST(p"/setJogSpeed" ? q_o"obsId=$maybeObsId" & q"axis=$axisStr" & q"speed=$speed") =>
      controller.setJogSpeed(maybeObsId.map(ObsId(_)), axisStr(0), speed.toInt)

    case POST(p"/beginMotion" ? q_o"obsId=$maybeObsId" & q"axis=$axisStr") =>
      controller.beginMotion(maybeObsId.map(ObsId(_)), axisStr(0))

    case POST(p"/channelMotorOff" ? q_o"obsId=$maybeObsId" & q"axis=$axisStr") =>
      controller.channelMotorOff(maybeObsId.map(ObsId(_)), axisStr(0))

    case POST(p"/setAbsTarget" ? q_o"obsId=$maybeObsId" & q"axis=$axisStr" & q"target=$target") =>
      controller.setAbsTarget(maybeObsId.map(ObsId(_)), axisStr(0), target.toInt)

    case POST(p"/setRelTarget" ? q_o"obsId=$maybeObsId" & q"axis=$axisStr" & q"target=$target") =>
      controller.setRelTarget(maybeObsId.map(ObsId(_)), axisStr(0), target.toInt)


    // get a config
    case GET(p"/getConfig" ? q"filename=$filenameStr") =>
      controller.getConfig(filenameStr)


    // create or update a config
    //case POST(p"/setConfig" ? q"filename=$filenameStr") =>
    //  controller.setConfig(filenameStr)

    case GET (p"/getContainerAssemblyInfo" ? q"filename=$filenameStr") =>
      controller.getContainerAssemblyInfo(filenameStr)

  }
}