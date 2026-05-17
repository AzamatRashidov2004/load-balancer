package org.example
import java.net.HttpURLConnection
import java.nio.channels.ServerSocketChannel
import java.net.SocketAddress
import java.net.InetSocketAddress
import java.net.URL
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    val listenPort = 80
    val address: SocketAddress = InetSocketAddress("0.0.0.0",listenPort)
    val channel: ServerSocketChannel = ServerSocketChannel.open()


    try {
        channel.bind(address)
        println("Listening to port ${listenPort}}")
        val backendServers = mutableListOf(
            mutableListOf("good", "http://localhost:8081"),
            mutableListOf("good", "http://localhost:8082"),
            mutableListOf("good", "http://localhost:8083")
        )

        try {
            thread {
                while (true) {
                    Thread.sleep(5000)
                    for (server in backendServers) {
                        val serverUrl = URL(server[1])
                        val connectionHealthCheck = serverUrl.openConnection() as HttpURLConnection
                        var good = false
                        try {
                            connectionHealthCheck.requestMethod = "GET"
                            connectionHealthCheck.connectTimeout = 5000
                            connectionHealthCheck.setRequestProperty("Accept", "text/html")
                            connectionHealthCheck.setRequestProperty("User-Agent", "KotlinClient/1.0")
                            connectionHealthCheck.connect()
                            good = if (connectionHealthCheck.responseCode in 200..299) true else false
                        } catch (e: Exception) {
                            println("Error connecting to ${server[1]}: $e")
                            good = false
                        } finally {
                            connectionHealthCheck.disconnect()
                            if (server[0] == "bad" && good) {
                                server[0] = "good"
                            }
                            if (server[0] == "good" && !good) {
                                server[0] = "bad"
                            }
                        }
                    }
                }
            }
        }
        catch (e: Exception) {
            println("Error starting a background healthcheck: $e")
        }

        var currBackendServerIndex = 0
        while (true) {
            val client = channel.accept()
            try {
                val reader = client.socket().getInputStream().bufferedReader(StandardCharsets.UTF_8);

                val clientMetadata = mutableListOf<String>()
                clientMetadata.add("Received request from ${client.remoteAddress}")

                var line: String? = reader.readLine()
                while (line != null && !line.isBlank() && !line.isEmpty()) {
                    clientMetadata.add(line)
                    line = reader.readLine()
                }

                for (s in clientMetadata) {
                    println(s)
                }

                // Round Robin algorithm
                while (backendServers[currBackendServerIndex][0] == "bad") {
                    currBackendServerIndex = (currBackendServerIndex + 1) % backendServers.size
                }
                val currServer = backendServers[currBackendServerIndex][1]
                currBackendServerIndex = (currBackendServerIndex + 1) % backendServers.size


                println("hitting server: $currServer")

                val currServerUrl = URL(currServer)
                val connection = currServerUrl.openConnection() as HttpURLConnection

                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.setRequestProperty("Accept", "text/html")
                    connection.setRequestProperty("User-Agent", "KotlinClient/1.0")

                    connection.connect()

                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream

                    val bodyResponse = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    println("the body result is: $bodyResponse")

                    val response =
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/html; charset=UTF-8\r\n" +
                                "Content-Length: ${bodyResponse.toByteArray().size}\r\n" +
                                "Connection: close\r\n" +
                                "\r\n" +
                                bodyResponse

                    val output = client.socket().getOutputStream()

                    output.write(response.toByteArray(StandardCharsets.UTF_8))
                    output.flush()

                }
                catch (e: Exception) {
                    println("Error connecting to the server: $currServer . Error message: ${e.message}")
                }
                finally {
                    connection.disconnect()
                }

            }
            catch(e: Exception) {
                println ("Error handling the client: ${e.message}")
            }
            finally {
               client.close()
            }
        }
    }
    catch (e: Exception){
        println("Server failed to start: ${e.message}")
    }
    finally {
        channel.close()
    }

}