package net.aquadc.linkedlists

import androidx.annotation.WorkerThread
import net.aquadc.persistence.android.json.json
import net.aquadc.persistence.android.json.tokens
import net.aquadc.persistence.extended.tokens.entries
import net.aquadc.persistence.struct.Schema
import net.aquadc.persistence.struct.Struct
import net.aquadc.persistence.tokens.readAs
import net.aquadc.persistence.type.collection
import net.aquadc.persistence.type.i32
import net.aquadc.persistence.type.string
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException


object Place : Schema<Place>() {
    val Id = "id" let i32
    val Name = "name" let string
    val ParentId = "parent_id".let(i32, default = -1) // effectively a foreign key; unused by countries

    val ListOf = collection(Place)
}


private const val URL_START = "http://lab.iamrohit.in/php_ajax_country_state_city_dropdown/apiv1.php?type=get"

@WorkerThread
fun OkHttpClient.fetchCountries(): List<Struct<Place>> =
        fetchPlacesFrom(URL_START + "Countries")

@WorkerThread
fun OkHttpClient.fetchStates(countryId: Int): List<Struct<Place>> =
        fetchPlacesFrom(URL_START + "States&countryId=" + countryId)

@WorkerThread
fun OkHttpClient.fetchCities(stateId: Int): List<Struct<Place>> =
        fetchPlacesFrom(URL_START + "Cities&stateId=" + stateId)


@WorkerThread
private fun OkHttpClient.fetchPlacesFrom(url: String): List<Struct<Place>> {
    val json = newCall(Request.Builder().get().url(url).build())
            .execute()
            .unwrap()
            .string()

    try {
        return json.reader().json().use {
            // skip garbage: {"status":"success","tp":1,"msg":"Countries fetched successfully.","result":
            it.beginObject()
            while (it.nextName() != "result") it.skipValue()

            it.isLenient = true // let's interpret [] as {} (fuck PHP)
            it.tokens()
                    .entries(emptyArray(), "id", "name") // {$id: $name, ...} -> [ {"id": $id, "name": $name}, ... ]
                    .readAs(Place.ListOf)
        }
    } catch (e: Exception) {
        println(json)
        throw e
    }
}


private fun Response.unwrap(): ResponseBody =
        if (isSuccessful) body!!
        else throw IOException("HTTP $code")
