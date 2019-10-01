package v1.gs

import java.nio.file.{Path, Paths}
import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.serialization.Serialization.Settings
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
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.{Id, ObsId, Prefix}
import javax.inject.Inject
import play.api.data.Form
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import csw.config.client.scaladsl.ConfigClientFactory
import csw.framework.internal.wiring.ActorRuntime
import csw.location.api.scaladsl.LocationService
import csw.location.client.scaladsl.HttpLocationServiceFactory

case class AssemblyApiFormInput(axesListString: String, stageName: String, positionsString: String, positionMethod: String, positionCoords: String)

/**
  * Takes HTTP requests and produces JSON.
  */
class GsController @Inject()(cc: GsControllerComponents)
  extends GsBaseController(cc) {

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
  //lazy val authStore                                  = new FileAuthStore(settings.authStorePath)
  //lazy val nativeAuthAdapter: InstalledAppAuthAdapter = InstalledAppAuthAdapterFactory.make(config, locationService, authStore)
  //lazy val tokenFactory: TokenFactory = new RestServerTokenFactory(nativeAuthAdapter)
  //val adminApi: ConfigService = ConfigClientFactory.adminApi(actorSystem, locationService, tokenFactory)
  val clientApi: ConfigClientService = ConfigClientFactory.clientApi(actorSystem, locationService)




  def assemblyCommandService(assemblyName: String): CommandService = createCommandService(getAkkaLocation(assemblyName, Assembly))

  def hcdCommandService(hcdName: String): CommandService = createCommandService(getAkkaLocation(hcdName, HCD))

  def shutdown(): Done = wiring.actorRuntime.shutdown(ApplicationFinishedReason).await()

  private def getAkkaLocation(name: String, cType: ComponentType): AkkaLocation = {
    val maybeLocation = locationService.resolve(AkkaConnection(ComponentId(name, cType)), resolveTimeout).await()
    maybeLocation.getOrElse(throw new RuntimeException(s"Location not found for component: name:[$name] type:[${cType.name}]"))
  }

  private def createCommandService: AkkaLocation ⇒ CommandService = CommandServiceFactory.make






  private val source:Prefix = Prefix("aps.ics.rest.server")
  private val maybeObsId = None


  println("ABOUT TO GET HCD COMMAND")



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


  private val axesKey: Key[String]           = KeyType.StringKey.make("axes")
  private val positionKey: Key[Double]       = KeyType.DoubleKey.make("position")
  private val positionMethodKey: Key[String] = KeyType.StringKey.make("positionMethod")
  val positionCoordKey: Key[String]          = KeyType.StringKey.make("positionCoord")


  //LoggingSystemFactory.start("GalilHcdClientApp", "0.1", host, system)


  private val form: Form[AssemblyApiFormInput] = {
    import play.api.data.Forms._
    import play.api.data.format.Formats._

    Form(
      mapping(
        "axesListString" -> nonEmptyText,
        "stageName" -> nonEmptyText,
        "positionsString" -> nonEmptyText,
        "positionMethod" -> nonEmptyText,
        "positionCoords" -> nonEmptyText
      )(AssemblyApiFormInput.apply)(AssemblyApiFormInput.unapply)
    )
  }

  def process: Action[AnyContent] = {
    GsAction.async { implicit request =>
      processJsonPost()
    }
  }

  // TODO: axisType (servo or stepper) is not yet used.  Current code only supports servo.

  private def processJsonPost[A]()(implicit request: GsRequest[A]):  Future[Result] = {
    def failure(badForm: Form[AssemblyApiFormInput]) = {
      Future.successful(BadRequest(badForm.errorsAsJson))
    }

    def success(input: AssemblyApiFormInput) = {

      implicit val ec: ExecutionContext = wiring.actorRuntime.ec

      // convert axesList and positions into Arrays
      val axesList = input.axesListString.split(",")
      val positions = input.positionsString.split(",").map(_.toDouble)

      request.target.path match  {

        case "/v1/gs/position/" => doPosition(Some(ObsId("123")), input.stageName, axesList, positions, input.positionMethod, input.positionCoords).map { response => {
          Ok(Json.toJson(response.toString())) }
        }

        case "/v1/gs/motorOff/" => doMotorOff(Some(ObsId("123")), input.stageName, axesList).map { response => Ok(Json.toJson(response.toString())) }

        case "/v1/gs/init/" => doInit(Some(ObsId("123")), input.stageName, axesList).map { response => {

          Ok(Json.toJson(response.toString())) }
        }

        case "/v1/gs/home/" => doHome(Some(ObsId("123")), input.stageName, axesList).map { response => Ok(Json.toJson(response.toString())) }


      }

    }

    form.bindFromRequest().fold(failure, success)
  }



  /**
   * Sends an init message to the assembly and returns the response
   */
  def doInit(obsId: Option[ObsId],stageName: String, axesList: Array[String]): Future[CommandResponse] = {

    val assembly:CommandService = getAssembly(stageName)
    val setup = Setup(prefix, CommandName("init"), obsId).add(axesKey.set(axesList))
    assembly.submitAndWait(setup)
  }

  /**
   * Sends a home message to the assembly and returns the response
   */
  def doHome(obsId: Option[ObsId],stageName: String, axesList: Array[String]): Future[CommandResponse] = {
    val assembly:CommandService = getAssembly(stageName)
    val setup = Setup(prefix, CommandName("home"), obsId).add(axesKey.set(axesList))
    assembly.submitAndWait(setup)

  }

  /**
   * Sends a home message to the assembly and returns the response
   */
  def doMotorOff(obsId: Option[ObsId],stageName: String, axesList: Array[String]): Future[CommandResponse] = {
    val assembly:CommandService = getAssembly(stageName)
    val setup = Setup(prefix, CommandName("motorOff"), obsId).add(axesKey.set(axesList))
    assembly.submitAndWait(setup)

  }

  /**
   * Sends a home message to the assembly and returns the response
   */
  def doPosition(obsId: Option[ObsId],stageName: String, axesList: Array[String], positions: Array[Double], positionMethod: String, positionCoords: String): Future[CommandResponse] = {
    val assembly = getAssembly(stageName)

    // axesList and positions need to be made into arrays
    val setup = Setup(prefix, CommandName("position"), obsId)
      .add(axesKey.set(axesList))
      .add(positionKey.set(positions))
      .add(positionMethodKey.set(positionMethod))
      .add(positionCoordKey.set(positionCoords))

    assembly.submitAndWait(setup)


  }

  // HCD Commands

  private val axisKey: Key[Char] = KeyType.CharKey.make("axis")
  private val countsKey: Key[Int] = KeyType.IntKey.make("counts")
  private val interpCountsKey: Key[Int] = KeyType.IntKey.make("interpCounts")
  private val brushlessModulusKey: Key[Int] = KeyType.IntKey.make("brushlessModulus")
  private val voltsKey: Key[Double] = KeyType.DoubleKey.make("volts")
  private val speedKey: Key[Int] = KeyType.IntKey.make("speed")



  def setBrushlessAxis(obsId: Option[ObsId], axis: Char): Action[AnyContent] = GsAction.async { implicit request =>
    val setup = Setup(source, CommandName("setBrushlessAxis"), obsId).add(axisKey.set(axis))
    galilHcd.submitAndWait(setup).map {response =>
      Ok(Json.toJson(response.toString))
    }
  }

  def setAnalogFeedbackSelect(obsId: Option[ObsId], axis: Char, count: Int): Action[AnyContent] = GsAction.async { implicit request =>
    val setup = Setup(source, CommandName("setAnalogFeedbackSelect"), obsId)
      .add(axisKey.set(axis))
      .add(interpCountsKey.set(count))

    galilHcd.submitAndWait(setup).map { response =>
      Ok(Json.toJson(response.toString))
    }
  }

  def setBrushlessModulus(obsId: Option[ObsId], axis: Char, brushlessModulus: Int): Action[AnyContent] = GsAction.async { implicit request =>
    val setup = Setup(source, CommandName("setBrushlessModulus"), obsId)
      .add(axisKey.set(axis))
      .add(brushlessModulusKey.set(brushlessModulus))
    galilHcd.submitAndWait(setup).map { response =>
      Ok(Json.toJson(response.toString))
    }
  }

  def brushlessZero(obsId: Option[ObsId], axis: Char, volts: Double): Action[AnyContent] = GsAction.async { implicit request =>
    val setup = Setup(source, CommandName("brushlessZero"), obsId)
      .add(axisKey.set(axis))
      .add(voltsKey.set(volts))
    galilHcd.submitAndWait(setup).map { response =>
      Ok(Json.toJson(response.toString))
    }
  }

  def setHomingMode(obsId: Option[ObsId], axis: Char): Action[AnyContent] = GsAction.async { implicit request =>
    val setup = Setup(source, CommandName("setHomingMode"), obsId)
      .add(axisKey.set(axis))
    galilHcd.submitAndWait(setup).map { response =>
      Ok(Json.toJson(response.toString))
    }
  }

  def setFindIndexMode(obsId: Option[ObsId], axis: Char): Action[AnyContent] = GsAction.async { implicit request =>
    val setup = Setup(source, CommandName("setFindIndexMode"), obsId)
      .add(axisKey.set(axis))
    galilHcd.submitAndWait(setup).map { response =>
      Ok(Json.toJson(response.toString))
    }
  }

  def setJogSpeed(obsId: Option[ObsId], axis: Char, speed: Int): Action[AnyContent] = GsAction.async { implicit request =>
    val setup = Setup(source, CommandName("setJogSpeed"), obsId)
      .add(axisKey.set(axis))
      .add(speedKey.set(speed))
    galilHcd.submitAndWait(setup).map { response =>
      Ok(Json.toJson(response.toString))
    }
  }

  def beginMotion(obsId: Option[ObsId], axis: Char): Action[AnyContent] = GsAction.async { implicit request =>
    val setup = Setup(source, CommandName("beginMotion"), obsId)
      .add(axisKey.set(axis))
    galilHcd.submitAndWait(setup).map { response =>
      Ok(Json.toJson(response.toString))
    }
  }

  def channelMotorOff(obsId: Option[ObsId], axis: Char): Action[AnyContent] = GsAction.async { implicit request =>
    val setup = Setup(source, CommandName("motorOff"), obsId)
      .add(axisKey.set(axis))
    galilHcd.submitAndWait(setup).map { response =>
      Ok(Json.toJson(response.toString))
    }
  }

  def setAbsTarget(obsId: Option[ObsId], axis: Char, target:Int): Action[AnyContent] = GsAction.async { implicit request =>
    val setup = Setup(source, CommandName("setAbsTarget"), obsId)
      .add(axisKey.set(axis))
      .add(countsKey.set(target))
    galilHcd.submitAndWait(setup).map { response =>
      Ok(Json.toJson(response.toString))
    }
  }

  def setRelTarget(obsId: Option[ObsId], axis: Char, target:Int): Action[AnyContent] = GsAction.async { implicit request =>
    val setup = Setup(source, CommandName("setRelTarget"), obsId)
      .add(axisKey.set(axis))
      .add(countsKey.set(target))
    galilHcd.submitAndWait(setup).map { response =>
      Ok(Json.toJson(response.toString))
    }
  }


  // Configuration Service API

  // Configuration Service API
  // (Note: This could also be done using the config service command line or HTTP APIs)
  private def getConfigPath(filename: String): Path = {
    Paths.get("/config/org/tmt/aps/ics/" + filename)
  }

  def getConfig(filename: String): Action[AnyContent] = GsAction.async { implicit request =>
    val filePath = getConfigPath(filename)
    (for {
      activeFile <- clientApi.getActive(filePath)
      config <- activeFile.get.toConfigObject
    } yield {
      Ok(Json.parse(config.root().render(ConfigRenderOptions.concise())))
    }).recover {
      case ex => InternalServerError(ex.getMessage)
    }
  }

  /*
  def setConfig(filename: String): Action[AnyContent] = GsAction.async { implicit request =>
    val data = request.body.asJson.get.toString()
    doUpdateConfig(filename, data).map { response =>
      Ok(Json.toJson(response))
    }.recover {
      case ex => BadRequest(ex.getMessage)
    }
  }

   */
/*
  private def doUpdateConfig(filename: String, input: String): Future[String] = async {

    // strip off {}
    val data = input.substring(1, input.length()-1)

    val filePath = getConfigPath(filename)
    val exists = await(adminApi.exists(filePath))
    if (exists) {
      // Note: Using for comp to work around compiler warning
      // "a pure expression does nothing in statement position; multiline expressions might require enclosing parentheses"
      await(for {
        _ <- adminApi.update(filePath, ConfigData.fromString(data), comment = "updated")
        _ <- adminApi.resetActiveVersion(filePath, "latest active")
      } yield "updated")
    } else {
      await(for {
        _ <- adminApi.create(filePath, ConfigData.fromString(data), annex = false, "First commit")
      } yield "created")
    }
  }
*/

  def getContainerAssemblyInfo(filename: String): Action[AnyContent] = GsAction.async { implicit request =>

    constructAssemblyList(filename).map { result =>
      Ok(Json.parse(result.root().render(ConfigRenderOptions.concise())))
    }
  }

  private def constructAssemblyList(filename: String): Future[Config] = {

    import scala.collection.JavaConverters._

    Future {
      var containerConfig = getConfigLocal(filename)

      val assemblyNames = containerConfig.getObjectList("components").asScala.map((p: ConfigObject) => p.toConfig().getString("name"))

      assemblyNames.map((assemblyName: String) => {
        val assemblyConfig = getConfigLocal(s"$assemblyName.conf")

        // get the configValue at "stageConfig" key
        val assemblyConfigValue = assemblyConfig.getValue("stageConfig")
        containerConfig = containerConfig.withValue(s"components.$assemblyName", assemblyConfigValue)


      })

      containerConfig


    }
  }

  private def getConfigLocal(filename: String): Config = {

    val configData: ConfigData = Await.result(getConfigData(filename), 10.seconds)

    Await.result(configData.toConfigObject, 3.seconds)

  }

  private def getConfigData(filename:String): Future[ConfigData] = {

    val filePath = getConfigPath(filename)
    clientApi.getActive(filePath).flatMap {
      case Some(config) ⇒ Future.successful(config) // do work
      case None         ⇒
        // required configuration could not be found in the configuration service. Component can choose to stop until the configuration is made available in the
        // configuration service and started again
        throw new Exception("No configuration found")
    }
  }


}
