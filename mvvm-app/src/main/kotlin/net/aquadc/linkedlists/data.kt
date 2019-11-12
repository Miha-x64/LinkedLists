package net.aquadc.linkedlists

import android.util.JsonReader
import android.util.JsonToken
import androidx.annotation.WorkerThread
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import java.io.StringReader


class Place( // fixme
        val id: Int,
        val name: String
)


private const val URL_START = "http://lab.iamrohit.in/php_ajax_country_state_city_dropdown/apiv1.php?type=get"

@WorkerThread
fun OkHttpClient.fetchCountries(): List<Place> =
        fetchPlacesFrom(URL_START + "Countries")

@WorkerThread
fun OkHttpClient.fetchStates(countryId: Int): List<Place> =
        fetchPlacesFrom(URL_START + "States&countryId=" + countryId)

@WorkerThread
fun OkHttpClient.fetchCities(stateId: Int): List<Place> =
        fetchPlacesFrom(URL_START + "Cities&stateId=" + stateId)


@WorkerThread
private fun OkHttpClient.fetchPlacesFrom(url: String): List<Place> {
    val json = newCall(Request.Builder().get().url(url).build())
            .execute()
            .unwrap()
            .string()

    try {
        return JsonReader(StringReader(json)).use(JsonReader::readPlacesResponse)
    } catch (e: Exception) {
        println(json)
        throw e
    }
}


private fun Response.unwrap(): ResponseBody =
        if (isSuccessful) body()!!
        else throw IOException("HTTP ${code()}")


private fun JsonReader.readPlacesResponse(): List<Place> {
    beginObject()

    var ret: List<Place>? = null

    while (hasNext()) {
        if (nextName() == "result") {

            when (peek()) {
                JsonToken.BEGIN_ARRAY -> {
                    // fuck PHP
                    beginArray()
                    ret = emptyList()
                    endArray()
                }
                JsonToken.BEGIN_OBJECT -> {
                    beginObject()
                    ret = readPlaces()
                    endObject()
                }
                else -> {
                    throw IllegalArgumentException("JSON contract violation")
                }
            }
        } else {
            skipValue()
        }
    }

    endObject()

    return ret ?: throw IllegalArgumentException("wrong server answer format")
}

private fun JsonReader.readPlaces(): List<Place> =
        if (!hasNext()) emptyList() else {
            val first = readPlace()

            if (!hasNext()) listOf(first) else {
                val list = ArrayList<Place>()
                list.add(first)

                do list.add(readPlace())
                while (hasNext())

                list
            }
        }

private fun JsonReader.readPlace(): Place =
        Place(nextName().toInt(), nextString())
