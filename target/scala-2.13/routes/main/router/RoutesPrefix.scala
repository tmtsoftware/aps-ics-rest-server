// @GENERATOR:play-routes-compiler
// @SOURCE:/home/smichaels/Desktop/IcsPrototype/aps-ics-rest-server/conf/routes
// @DATE:Wed Sep 25 16:28:19 PDT 2019


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
