package net.aquadc.linkedlists

import net.aquadc.lychee.http.ExperimentalHttp
import net.aquadc.lychee.http.GET
import net.aquadc.lychee.http.invoke
import net.aquadc.lychee.http.param.Query
import net.aquadc.lychee.http.param.Response
import net.aquadc.persistence.sql.Table
import net.aquadc.persistence.sql.dialect.Dialect
import net.aquadc.persistence.struct.Schema
import net.aquadc.persistence.struct.Struct
import net.aquadc.persistence.type.i32
import net.aquadc.persistence.type.string


object Place : Schema<Place>() {
    val Id = "id" let i32
    val Name = "name" let string
    val ParentId = "parent_id".let(i32, default = -1) // effectively a foreign key; unused by countries
}

@OptIn(ExperimentalHttp::class)
class HttpContract(path: String) {
    val countries = GET("$path?type=getCountries", Response<List<Struct<Place>>>())
    val states = GET("$path?type=getStates", Query("countryId", i32), Response<List<Struct<Place>>>())
    val cities = GET("$path?type=getCities", Query("stateId", i32), Response<List<Struct<Place>>>())
}

const val LOCAL_PATH = "/country_state_city"
//           this slash ^ is crucial for Undertow

///////////////////////////////////////////
// WARNING                               //
// Lychee SQL API is a subject to change //
///////////////////////////////////////////

val Countries: Table<Place, Int> = Table(Place, "countries", Place.Id)
val States: Table<Place, Int> = Table(Place, "states", Place.Id)
val Cities: Table<Place, Int> = Table(Place, "cities", Place.Id)

fun Dialect.createTable(name: String, parentName: String): String = buildString {
    append("CREATE TABLE ").appendName(name).append(" (")
        .appendName("id").append(" INTEGER NOT NULL PRIMARY KEY, ")
        .appendName("name").append(" TEXT NOT NULL, ")
        .appendName("parent_id").append("INTEGER NOT NULL, ")
        .append("FOREIGN KEY(").appendName("parent_id").append(") REFERENCES ")
        .appendName(parentName).append('(').appendName("id").append(')')
    append(')')
}
