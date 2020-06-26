package net.aquadc.linkedlists

import net.aquadc.lychee.http.client.okhttp3.blocking
import net.aquadc.lychee.http.client.okhttp3.template
import net.aquadc.lychee.http.param.Resp
import net.aquadc.persistence.android.json.json
import net.aquadc.persistence.android.json.tokens
import net.aquadc.persistence.extended.tokens.entries
import net.aquadc.persistence.struct.Struct
import net.aquadc.persistence.tokens.readListOf
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException

class HttpApi(client: OkHttpClient, baseUrl: String, contract: HttpContract) {
    private val parse: Response.(Resp<List<Struct<Place>>>) -> List<Struct<Place>> =
        { _ ->
            val json = unwrap().string()
            try {
                json.reader().json().use {
                    // skip garbage: {"status":"success","tp":1,"msg":"Countries fetched successfully.","result":
                    it.beginObject()
                    while (it.nextName() != "result") it.skipValue()

                    it.isLenient = true // let's interpret [] as {} (fuck PHP)
                    it.tokens()
                        .entries(emptyArray(), "id", "name") // {$id: $name, ...} -> [ {"id": $id, "name": $name}, ... ]
                        .readListOf(Place)
                }
            } catch (e: Exception) {
                println(json)
                throw e
            }
        }

    val countries: () -> List<Struct<Place>> = client.template(baseUrl, contract.countries, blocking(parse))
    val states: (countryId: Int) -> List<Struct<Place>> = client.template(baseUrl, contract.states, blocking(parse))
    val cities: (stateId: Int) -> List<Struct<Place>> = client.template(baseUrl, contract.cities, blocking(parse))
}

private fun Response.unwrap(): ResponseBody =
    if (isSuccessful) body!!
    else throw IOException("HTTP $code")
