package org.ibm

import cats.effect.*
import cats.syntax.all.*
import org.ibm.shared.{HelloResponse, WatsonxAIIFM}
import org.http4s.{HttpApp, HttpRoutes, StaticFile}
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.server.staticcontent.*
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.http4s.*
import com.comcast.ip4s.*
import fs2.io.file.Path
import sttp.tapir.server.ServerEndpoint
import org.ibm.watsonxaiifm.Client
import java.nio.file.Paths
import scala.concurrent.ExecutionContext
import scala.util.Try

object Main extends IOApp {
  // Toggle dev vs prod via -Ddev=true
  private val isDev: Boolean = sys.props.get("dev").contains("true")
  private val watsonxAIIFMEndpoint: ServerEndpoint[Any, IO] =
    endpoint.get
      .in("api" / "watsonxaiifm")
      .out(jsonBody[WatsonxAIIFM])
      .serverLogicSuccess(_ => {
        val fm = Client.getWatsonxAIIFM
        IO.pure(WatsonxAIIFM(fm.toString))
      }   )

  private val watsonxAIIFMRoutes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(watsonxAIIFMEndpoint)
  // 2a. Production: serve from classpath /web
  private val prodStaticRoutes: HttpRoutes[IO] =
    resourceServiceBuilder[IO]("/web")
      .withClassLoader(Try{getClass.getClassLoader}.toOption)
      .toRoutes



  private val devStaticRoutes = HttpRoutes.of[IO] {
    case request@ GET -> Root / fileName =>

      val baseDir = java.nio.file.Paths.get("./client/target/scala-3.7.1")
      val resolved = baseDir.resolve(fileName).toString
      println(resolved)
        StaticFile.fromPath(Path(resolved), Some(request)).getOrElseF(NotFound())
  }

  // Choose which static routes to mount
  private val staticRoutes: HttpRoutes[IO] =
    if (isDev) devStaticRoutes else prodStaticRoutes

  // 3. Fallback for GET "/" to serve index.html
  private val indexRoute: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root =>
      StaticFile
        .fromResource("/web/index.html", Some(req))
        .getOrElseF(NotFound())
  }

  // 4. Compose everything
  private val allRoutes: HttpRoutes[IO] =
      staticRoutes <+>
      indexRoute <+> watsonxAIIFMRoutes

  private val httpApp: HttpApp[IO] = Router[IO](
    "/"    -> allRoutes
  ).orNotFound

  // 5. Launch Ember server
  override def run(args: List[String]): IO[ExitCode] =
    EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(httpApp)
      .build
      .use(_ => IO.never)
      .as(ExitCode.Success)
}
