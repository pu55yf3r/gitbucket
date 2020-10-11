package gitbucket.core

import java.net.InetSocketAddress
import java.nio.file.Files
import java.io.File

import gitbucket.core.util.{FileUtil, HttpClientUtil}
import org.apache.http.client.methods.HttpGet
import org.eclipse.jetty.server.handler.StatisticsHandler
import org.eclipse.jetty.server.{Handler, Server}
import org.eclipse.jetty.webapp.WebAppContext

class TestingGitBucketServer(port: Int = 8080) extends AutoCloseable {
  private var server: Server = null
  private var dir: File = null

  def start(): Unit = {
    System.setProperty("java.awt.headless", "true")

    dir = Files.createTempDirectory("gitbucket-test-").toFile
    System.setProperty("gitbucket.home", dir.getAbsolutePath)

    val address = new InetSocketAddress(port)
    server = new Server(address)

    val context = new WebAppContext
    context.setResourceBase("./target/webapp")
    context.setContextPath("")
    context.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false")
    context.setServer(server)

    val handler = addStatisticsHandler(context)
    server.setHandler(handler)

    server.start()

    HttpClientUtil.withHttpClient(None) { httpClient =>
      var launched = false
      while (!launched) {
        Thread.sleep(500)
        val res = httpClient.execute(new HttpGet("http://localhost:8080/"))
        launched = res.getStatusLine.getStatusCode == 200
      }
    }
  }

  private def addStatisticsHandler(handler: Handler) = { // The graceful shutdown is implemented via the statistics handler.
    // See the following: https://bugs.eclipse.org/bugs/show_bug.cgi?id=420142
    val statisticsHandler = new StatisticsHandler
    statisticsHandler.setHandler(handler)
    statisticsHandler
  }

  def close(): Unit = {
    server.stop()
    FileUtil.deleteIfExists(dir)
  }
}