package net.aquadc.linkedlists

import android.database.Cursor
import net.aquadc.persistence.android.parcel.ParcelPropertiesMemento
import net.aquadc.persistence.sql.BindBy
import net.aquadc.persistence.sql.Session
import net.aquadc.persistence.sql.Table
import net.aquadc.persistence.sql.blocking.Blocking
import net.aquadc.persistence.sql.blocking.Eagerly
import net.aquadc.persistence.sql.withTransaction
import net.aquadc.persistence.struct.Struct
import net.aquadc.persistence.struct.copy
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
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future


class LinkedListsViewModel(
    private val database: Session<Blocking<Cursor>>,
    private val httpApi: Lazy<HttpApi>,
    private val io: ExecutorService,
    state: ParcelPropertiesMemento?
) : PersistableProperties, Closeable {

    private val _countries: MutableSingleChoice<Struct<Place>, Int> = PlaceChoice()
    val countries: SingleChoice<Struct<Place>, Int> get() = _countries
    private var loadingCountries: Future<*>? = null

    private val _states: MutableSingleChoice<Struct<Place>, Int> = PlaceChoice()
    val states: SingleChoice<Struct<Place>, Int> get() = _states
    private var loadingStates: Future<*>? = null

    private val _cities: MutableSingleChoice<Struct<Place>, Int> = PlaceChoice()
    val cities: SingleChoice<Struct<Place>, Int> get() = _cities
    private var loadingCities: Future<*>? = null

    private val _problem: MutableProperty<Exception?> = concurrentPropertyOf(null)
    val problem: Property<Exception?> get() = _problem

    val retryRequested: MutableProperty<Boolean> = propertyOf(false)
            .clearEachAnd(::retry)

    override fun saveOrRestore(io: PropertyIo) {
        io x _countries.selectedItemId
        io x _states.selectedItemId
        io x _cities.selectedItemId
    }

    init {
        // catch up with saved state NOW,
        // thus we won't trigger state changes and start unnecessary network calls or DB queries
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
            loadCatching(Countries, -1, { httpApi.value.countries() }, _countries, _problem)
        }
    }

    private fun loadStates() {
        val countryId = _countries.selectedItemId.value
        loadingStates?.cancel(true)
        loadingStates =
                if (countryId == -1) null
                else io.submit {
                    loadCatching(States, countryId, { httpApi.value.states(it) }, _states, _problem)
                }
    }

    private fun loadCities() {
        val stateId = _states.selectedItemId.value
        loadingCities?.cancel(true)
        loadingCities =
                if (stateId == -1) null
                else io.submit {
                    loadCatching(Cities, stateId, { httpApi.value.cities(it) }, _cities, _problem)
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

    private inline fun loadCatching(
        table: Table<Place, Int>, parentId: Int,
        download: (id: Int) -> List<Struct<Place>>,
        choice: MutableSingleChoice<Struct<Place>, *>,
        problem: MutableProperty<in Exception>
    ) {
        try {
            choice.state.value = ListState.Loading

            var items: List<Struct<Place>> = database.rawQuery(
                "SELECT * FROM ${table.name} WHERE ${Place.ParentId.name(Place)} = ? ORDER BY ${Place.Name.name(Place)} ASC",
                arrayOf(Place.ParentId.type(Place)),
                arrayOf(parentId),
                Eagerly.structs(table, BindBy.Name)
            )
            if (items.isEmpty()) {
                items = download(parentId)
                database.withTransaction {
                    items.forEach { insert(table, it.copy { it[ParentId] = parentId }) }
                }
            }

            choice.items.value = items
            choice.state.value = if (items.isEmpty()) ListState.Empty else ListState.Ok
        } catch (e: Exception) {
            problem.value = e
            choice.state.value = ListState.Error
            e.printStackTrace()
        }
    }

    private fun PlaceChoice() =
        MutableSingleChoice(Place.Id, -1, true)

}
