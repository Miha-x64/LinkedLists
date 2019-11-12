package net.aquadc.linkedlists

import net.aquadc.persistence.android.parcel.ParcelPropertiesMemento
import net.aquadc.properties.MutableProperty
import net.aquadc.properties.Property
import net.aquadc.properties.clearEachAnd
import net.aquadc.properties.concurrentPropertyOf
import net.aquadc.properties.distinct
import net.aquadc.properties.function.Objectz
import net.aquadc.properties.persistence.PropertyIo
import net.aquadc.properties.persistence.memento.PersistableProperties
import net.aquadc.properties.persistence.memento.restoreTo
import net.aquadc.properties.persistence.x
import net.aquadc.properties.propertyOf
import okhttp3.OkHttpClient
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future


class LinkedListsViewModel(
        private val okHttpClient: OkHttpClient,
        private val io: ExecutorService,
        state: ParcelPropertiesMemento?
) : PersistableProperties, Closeable {

    private val _countries: MutableSingleChoice<Place, Int> = PlaceChoice()
    val countries: SingleChoice<Place, Int> get() = _countries
    private var loadingCountries: Future<*>? = null

    private val _states: MutableSingleChoice<Place, Int> = PlaceChoice()
    val states: SingleChoice<Place, Int> get() = _states
    private var loadingStates: Future<*>? = null

    private val _cities: MutableSingleChoice<Place, Int> = PlaceChoice()
    val cities: SingleChoice<Place, Int> get() = _cities
    private var loadingCities: Future<*>? = null

    private val _problem: MutableProperty<Exception?> = concurrentPropertyOf(null)
    val problem: Property<Exception?> get() = _problem

    val retryRequested: MutableProperty<Boolean> = propertyOf(false).also {
        it.clearEachAnd(::retry)
    }

    override fun saveOrRestore(io: PropertyIo) {
        io x _countries.selectedItemId
        io x _states.selectedItemId
        io x _cities.selectedItemId
    }

    init {
        // catch up with saved state NOW, so we won't start unnecessary network calls on state changes
        if (state !== null) state.restoreTo(this)

        loadCountries()

        loadStates()
        countries.selectedItemId.distinct(Objectz.Equal).addChangeListener { _, _ ->
            _states.clear() // this will clear cities, too; also, we'll crash with IOOBE without it :)
            _problem.value = null
            loadStates()
        }

        loadCities()
        states.selectedItemId.distinct(Objectz.Equal).addChangeListener { _, _ ->
            _cities.clear()
            _problem.value = null
            loadCities()
        }
    }

    private fun loadCountries() {
        loadingCountries = io.submit {
            loadCatching(okHttpClient::fetchCountries, _countries, _problem)
        }
    }

    private fun loadStates() {
        val countryId = _countries.selectedItemId.value
        loadingStates?.cancel(true)
        loadingStates =
                if (countryId == -1) null
                else io.submit {
                    loadCatching({ okHttpClient.fetchStates(countryId = countryId) }, _states, _problem)
                }
    }

    private fun loadCities() {
        val stateId = _states.selectedItemId.value
        loadingCities?.cancel(true)
        loadingCities =
                if (stateId == -1) null
                else io.submit {
                    loadCatching({ okHttpClient.fetchCities(stateId = stateId) }, _cities, _problem)
                }
    }

    private fun retry() {
        retryIfFailed(_countries, ::loadCountries)
        retryIfFailed(_states, ::loadStates)
        retryIfFailed(_cities, ::loadCities)
    }

    private inline fun retryIfFailed(choice: SingleChoice<*, *>, retryAction: () -> Unit) {
        if (choice.state.value === ListState.Error) retryAction()
    }

    override fun close() {
        loadingCountries?.cancel(true)
        loadingStates?.cancel(true)
        loadingCities?.cancel(true)
    }

}

private fun PlaceChoice() =
        MutableSingleChoice(Place::id, -1, true)

private inline fun <T : Any> loadCatching(
        func: () -> List<T>,
        choice: MutableSingleChoice<T, *>,
        problem: MutableProperty<in Exception>
) {
    try {
        choice.state.value = ListState.Loading

        val items = func() // some IO here

        choice.items.value = items
        choice.state.value = if (items.isEmpty()) ListState.Empty else ListState.Ok
    } catch (e: Exception) {
        problem.value = e
        choice.state.value = ListState.Error
        e.printStackTrace()
    }
}
