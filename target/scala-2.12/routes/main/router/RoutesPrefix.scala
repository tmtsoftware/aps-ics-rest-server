
// @GENERATOR:play-routes-compiler
// @SOURCE:/home/smichaels/Desktop/DetectorPrototype/si-detector-rest-server/conf/routes
// @DATE:Thu Jul 11 16:15:23 PDT 2019


package router {
  object RoutesPrefix {
    private var _prefix: String = "/"
    def setPrefix(p: String): Unit = {
      _prefix = p
    }
    def prefix: String = _prefix
    val byNamePrefix: Function0[String] = { () => prefix }
  }
}
