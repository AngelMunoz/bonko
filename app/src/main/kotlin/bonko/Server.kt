package bonko

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*

fun createServer(bun: Bun, host: String = "localhost", port: Int = 7331): NettyApplicationEngine {
    val server =
            embeddedServer(Netty, host = host, port = port) {
                routing {
                    get("/") {
                        call.respondText("Hello, world!")
                    }
                    post("/transpile") {
                        val loader = call.request.queryParameters["loader"] ?: "js"
                        val content = call.receiveText()
                        val (result, error) = bun.transpile(loader, content)

                        val isError = result.isEmpty() || result.isBlank()

                        val headerValue = if (isError) "error" else "success"
                        val responseValue = if (isError) error else result
                        val contentType = if (isError) ContentType.Text.Plain else ContentType.Text.JavaScript
                        val statusCode = if (isError) HttpStatusCode.BadRequest else HttpStatusCode.OK

                        call.response.headers.append("x-transpile-result", headerValue)

                        call.respondText(responseValue, contentType, statusCode)
                    }
                }
            }
    return server
}