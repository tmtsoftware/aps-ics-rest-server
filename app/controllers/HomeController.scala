package controllers



import java.util.concurrent.TimeUnit

import akka.{Done, NotUsed}
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.serialization.Serialization.Settings
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigRenderOptions}
import csw.client.utils.Extensions.FutureExt
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.config.api.scaladsl.ConfigClientService
import csw.config.api.ConfigData
import csw.framework.CswClientWiring
import csw.framework.commons.CoordinatedShutdownReasons.ApplicationFinishedReason
import csw.location.models.{AkkaLocation, ComponentId, ComponentType}
import csw.location.models.ComponentType.{Assembly, HCD}
import csw.location.models.Connection.AkkaConnection
import csw.params.commands.{CommandName, CommandResponse, Setup}
import csw.params.core.generics.{Key, KeyType, Parameter}
import csw.params.core.models.{Id, ObsId, Prefix, Struct}
import javax.inject.Inject
import play.api.data.Form
import play.api.libs.json.{Json, Writes}
import play.api.mvc._

import scala.concurrent.duration.FiniteDuration
import csw.client.utils.Extensions.FutureExt

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import csw.config.client.scaladsl.ConfigClientFactory
import csw.framework.internal.wiring.ActorRuntime
import csw.location.api.scaladsl.LocationService
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.params.core.states.{CurrentState, StateName}

import scala.collection.mutable.HashMap


/**
 * A very small controller that renders a home page.
 */
class HomeController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  //  private val logger = Logger(getClass)

  private val prefix = Prefix("aps.ics.engui")

  implicit val resolveTimeout: FiniteDuration = FiniteDuration(30, TimeUnit.SECONDS)
  implicit val timeout: Timeout = Timeout(30.seconds)

  lazy val clientWiring = new CswClientWiring
  import clientWiring._

  // the really long way now to get an adminApi
  lazy val config: Config = ConfigFactory.load()
  lazy val settings       = new Settings(config)
  lazy val actorSystem    = ActorSystem(SpawnProtocol.behavior, "config-cli", config)
  lazy val actorRuntime   = new ActorRuntime(actorSystem)
  import actorRuntime._

  lazy val locationService: LocationService           = HttpLocationServiceFactory.makeLocalClient(actorSystem, actorRuntime.mat)



  // hashmap to place all currentstate axis values
  private var axisInfoMap: HashMap[Char,AxisInfo] = HashMap.empty[Char,AxisInfo]

  private val connection = AkkaConnection(ComponentId("IcsStimulusGalilHcd", HCD))

  def assemblyCommandService(assemblyName: String): CommandService = createCommandService(getAkkaLocation(assemblyName, Assembly))

  def hcdCommandService(hcdName: String): CommandService = createCommandService(getAkkaLocation(hcdName, HCD))

  def shutdown(): Done = wiring.actorRuntime.shutdown(ApplicationFinishedReason).await()

  private def getAkkaLocation(name: String, cType: ComponentType): AkkaLocation = {
    val maybeLocation = locationService.resolve(AkkaConnection(ComponentId(name, cType)), resolveTimeout).await()
    maybeLocation.getOrElse(throw new RuntimeException(s"Location not found for component: name:[$name] type:[${cType.name}]"))
  }

  private def createCommandService: AkkaLocation ⇒ CommandService = CommandServiceFactory.make



  private val galilHcd = hcdCommandService("IcsStimulusGalilHcd")

  private def getAssembly(stageName: String):CommandService = {

    stageName match {
      case "FiberSourceStage" =>
        assemblyCommandService("FiberSourceStageAssembly")
      case "DmOpticStage" =>
        assemblyCommandService("DmOpticStageAssembly")
      case "PupilMaskStage" =>
        assemblyCommandService("PupilMaskStageAssembly")
    }

  }

  var lastHcdState: Option[CurrentState] = Option.empty;

      galilHcd.subscribeCurrentState((x: CurrentState) => {
        lastHcdState = Option(x)
        unpackHcdCurrentState(x)
      })





  private def unpackHcdCurrentState(currentState: CurrentState): Unit = {
    println(s"currentState = ${currentState}")


    val errorCodeKey = KeyType.ByteKey.make("errorCode")
    val errorCode: Byte = currentState.get(errorCodeKey).get.value(0)


    currentState.paramSet.filter((param: Parameter[_]) => "ABCDEFGH".contains(param.keyName)).foreach((param: Parameter[_]) => {

      val channel: Char = param.keyName(0)

      val structParam: Parameter[Struct] = extractStructParam(param)
      val paramSet: Set[Parameter[_]] = structParam.value(0).paramSet

      //println("struct param set = " + paramSet)

      // create a temporary current state to manage the key-type mappings when extracting
      val stateName = StateName("")
      val tempCurState: CurrentState = CurrentState(currentState.prefix, stateName, paramSet)

      //println("tempCurrentState = " + tempCurState)

      val referencePositionKey = KeyType.IntKey.make("referencePosition")
      val motorPositionKey = KeyType.IntKey.make("motorPosition")
      val positionErrorKey = KeyType.IntKey.make("positionError")
      val velocityKey = KeyType.IntKey.make("velocity")
      val torqueKey = KeyType.IntKey.make("torque")


      val referencePosition: Int = tempCurState.get(referencePositionKey).get.value(0)
      val motorPosition: Int = tempCurState.get(motorPositionKey).get.value(0)
      val positionError: Int = tempCurState.get(positionErrorKey).get.value(0)
      val velocity: Int = tempCurState.get(velocityKey).get.value(0)
      val torque: Int = tempCurState.get(torqueKey).get.value(0)


      // build up the part of AxisInfo that we know.  Use old values from axisInfoMap for the rest if it exists.
      // we can do the same process for the current state coming from the HCD, creating a composite AxisInfo

      val currentAxisInfo = if (axisInfoMap.contains(channel)) axisInfoMap(channel) else AxisInfo("" + channel, "",0,0,0,0,0,0,0)

      println("currentAxisInfo " + currentAxisInfo)

      val axisInfo: AxisInfo = AxisInfo(currentAxisInfo.axis, currentAxisInfo.status, currentAxisInfo.position,
        referencePosition, motorPosition, positionError, velocity, torque, errorCode)

      axisInfoMap(channel) = axisInfo

      //println(s"axisInfo $axisInfo")
    })


  }





  private def unpackAssemblyCurrentState(currentState: CurrentState): Unit = {
    //println(s"currentState = ${currentState}")

    currentState.paramSet.foreach((param: Parameter[_]) => {
      val structParam: Parameter[Struct] = extractStructParam(param)
      val paramSet: Set[Parameter[_]] = structParam.value(0).paramSet

      // create a temporary current state to manage the key-type mappings when extracting
      val stateName = StateName("")
      val tempCurState: CurrentState = CurrentState(currentState.prefix, stateName, paramSet)

      val axisNameKey = KeyType.StringKey.make("axisName")
      val channelKey  = KeyType.CharKey.make("channel")
      val positionKey = KeyType.DoubleKey.make("position")
      val statusKey = KeyType.ShortKey.make("status")

      val axisName: String = tempCurState.get(axisNameKey).get.value(0)
      val channel: Char = tempCurState.get(channelKey).get.value(0)
      val position: Double = tempCurState.get(positionKey).get.value(0)
      val status: Short = tempCurState.get(statusKey).get.value(0)

      // convert status to hex string
      val statusHex = f"${status}%08X"
      val statusStr = statusHex.substring(4)

      //println(s"statusStr $statusStr")

      val positionMm: Int = (position * 1000).toInt;

      // build up the part of AxisInfo that we know.  Use old values from axisInfoMap for the rest if it exists.
      // we can do the same process for the current state coming from the HCD, creating a composite AxisInfo

      val currentAxisInfo = if (axisInfoMap.contains(channel)) axisInfoMap(channel) else AxisInfo("" + channel, "",0,0,0,0,0,0,0)


      val axisInfo: AxisInfo = AxisInfo("" + channel, statusStr, positionMm,
        currentAxisInfo.referencePosition, currentAxisInfo.motorPosition, currentAxisInfo.positionError,
        currentAxisInfo.velocity, currentAxisInfo.torque, currentAxisInfo.errorCode)

      axisInfoMap(channel) = axisInfo

      //println(s"axisInfo $axisInfo")
    })


  }

  def extractStructParam(input: Parameter[_]): Parameter[Struct] = {
    input match {
      case x: Parameter[Struct] => x
      case _                    => throw new Exception("unexpected exception")
    }
  }




  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def ws = WebSocket.accept[String, String] { request =>

    // Log events to the console
    val in = Sink.foreach[String](println)

    val out = Source.tick(1.second, 1.second, NotUsed).map(_ => buildMessage())

    Flow.fromSinkAndSource(in, out)

  }

  //case class AxisInfo(axis:String, status:String, position:Int, positionError:Int)

  case class AxisInfo(axis:String, status:String, position:Int, referencePosition: Int,
                      motorPosition: Int, positionError: Int, velocity: Int, torque: Int, errorCode: Int)


  case class AxesInfo(axes: Seq[AxisInfo])

  implicit val axisInfoWrites = new Writes[AxisInfo] {
    def writes(axisInfo: AxisInfo) = Json.obj(
      "axis" -> axisInfo.axis,
      "status" -> axisInfo.status,
      "position" -> axisInfo.position,
      "positionError" -> axisInfo.positionError,
      "referencePosition" -> axisInfo.referencePosition,
      "motorPosition" -> axisInfo.motorPosition,
      "velocity" -> axisInfo.velocity,
      "torque" -> axisInfo.torque,
      "errorCode" -> axisInfo.errorCode
    )
  }

  implicit val axesInfoWrites = new Writes[AxesInfo] {
    def writes(axesInfo: AxesInfo) = Json.obj(
      "axes" -> axesInfo.axes
    )
  }




  def buildMessage(): String = {
    val v = "Hello"


    //val resp4 = Await.result(galilHcdClient.getStatus(Some(ObsId("123"))), 3.seconds)

    //logger.info("resp4 = " + resp4)

    val start = -2000
    val end   = 3000
    val rnd = new scala.util.Random


    val axesInfo = AxesInfo(
      Seq(
        axisInfoMap('A'),
        axisInfoMap('B'),
        axisInfoMap('C'),
        axisInfoMap('D'),
        axisInfoMap('E'),
        axisInfoMap('F'),
        axisInfoMap('G')
      )
    )

    val message = Json.toJson(axesInfo)
    message.toString()
  }





}



