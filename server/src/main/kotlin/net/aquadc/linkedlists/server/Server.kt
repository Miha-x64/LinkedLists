@file:JvmName("Server")
package net.aquadc.linkedlists.server

import com.squareup.moshi.JsonWriter
import io.undertow.Undertow
import io.undertow.server.HttpServerExchange
import io.undertow.server.RoutingHandler
import io.undertow.util.HttpString
import net.aquadc.linkedlists.Cities
import net.aquadc.linkedlists.Countries
import net.aquadc.linkedlists.HttpContract
import net.aquadc.linkedlists.LOCAL_PATH
import net.aquadc.linkedlists.Place
import net.aquadc.linkedlists.States
import net.aquadc.lychee.http.param.Param
import net.aquadc.lychee.http.server.undertow2.add
import net.aquadc.persistence.sql.BindBy
import net.aquadc.persistence.sql.ExperimentalSql
import net.aquadc.persistence.sql.blocking.Eagerly.cell
import net.aquadc.persistence.sql.blocking.Eagerly.structs
import net.aquadc.persistence.sql.blocking.JdbcSession
import net.aquadc.persistence.sql.dialect.sqlite.SqliteDialect
import net.aquadc.persistence.sql.query
import net.aquadc.persistence.struct.Struct
import net.aquadc.persistence.type.i32
import okio.Okio
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.ByteBuffer
import java.sql.DriverManager
import java.sql.ResultSet
import kotlin.system.exitProcess


private val contentType = HttpString("Content-Type")

@OptIn(ExperimentalSql::class)
fun main(args: Array<String>) {
    var address: String? = null
    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "--host" -> address = args[++i]
            else -> exit("unrecognized option: $arg", 1)
        }
        i++
    }
    if (address == null)
        exit("Usage: <server> --host 127.0.0.1", 1)
    println("Starting @ $address:8080")

    val contract = HttpContract(LOCAL_PATH)
    val respond: HttpServerExchange.(List<Struct<Place>>) -> Unit = { places ->
        responseHeaders.add(contentType, "application/json; charset=utf-8")
        responseSender.send(ByteBuffer.wrap(ByteArrayOutputStream().also { baos ->
            JsonWriter.of(Okio.buffer(Okio.sink(baos)))
                .inObject {
                    name("status"); value("success")
                    name("tp"); value(1) // dunno wtf is this, just copying existing API
                    name("msg"); value("Everything is good.")
                    name("result"); inObject {
                        places.forEach { place ->
                            name(place[Place.Id].toString())
                            value(place[Place.Name])
                        }
                    }
                }
                .close()
        }.toByteArray()))
    }
    val respondBad: HttpServerExchange.(Param<*>, Throwable) -> Unit = { param, e ->
        val trace = String(ByteArrayOutputStream().also { e.printStackTrace(PrintStream(it)) }.toByteArray())
        statusCode = 400
        responseHeaders.add(contentType, "text/html")
        responseSender.send("<pre>$trace</pre>")
    }
    val session = JdbcSession(DriverManager.getConnection("jdbc:sqlite:geo.db"), SqliteDialect)
    // Note: this country-city-state database is poor, don't use it    ^^^^^^

    val countries = session.query("SELECT id, name, -1 as \"parent_id\" FROM \"countries\"", structs<ResultSet, Place>(Countries, BindBy.Name))

    val country = session.query("SELECT 1 FROM \"countries\" WHERE \"id\" = ?", i32, cell<ResultSet, Int>(i32))
    val states = session.query("SELECT * FROM \"states\" WHERE \"parent_id\" = ?", i32, structs<ResultSet, Place>(States, BindBy.Name))

    val state = session.query("SELECT 1 FROM \"states\" WHERE \"id\" = ?", i32, cell<ResultSet, Int>(i32))
    val cities = session.query("SELECT * FROM \"cities\" WHERE \"parent_id\" = ?", i32, structs<ResultSet, Place>(Cities, BindBy.Name))

    Undertow.builder()
        .addHttpListener(
            8080, address,
            RoutingHandler(false)
                .add("GET", "/") { it.respondText("(ό‿ὸ)ﾉ") }
                .add(contract.countries, respond) { countries() }
                .add(contract.states, respond, respondBad) { countryId -> country(countryId); states(countryId) }
                .add(contract.cities, respond, respondBad) { stateId -> state(stateId); cities(stateId) }
                .setFallbackHandler { it.statusCode = 404; it.respondText("(ノಠ益ಠ)ノ彡┻━┻") }
        )
        .build().start()
}

private fun HttpServerExchange.respondText(text: String) {
    responseHeaders.add(contentType, "text/plain; charset=utf-8")
    responseSender.send(text, Charsets.UTF_8)
}

private fun exit(message: String, code: Int): Nothing {
    println(message)
    exitProcess(code)
}

private inline fun JsonWriter.inObject(block: JsonWriter.() -> Unit): JsonWriter {
    beginObject()
    block()
    endObject()
    return this
}
