package net.aquadc.linkedlists

import net.aquadc.properties.MutableProperty
import net.aquadc.properties.Property
import net.aquadc.properties.bind
import net.aquadc.properties.propertyOf


interface SingleChoice<T : Any, ID> {
    val state: Property<ListState>
    val items: Property<List<T>>
    val selectedItemId: MutableProperty<ID>
    val selectedItem: MutableProperty<T?>
    val selectedItemPosition: MutableProperty<Int>
}

class MutableSingleChoice<T : Any, ID : Any>(
        private val getId: T.() -> ID,
        private val noId: ID,
        concurrent: Boolean
) : SingleChoice<T, ID> {

    override val state: MutableProperty<ListState> = propertyOf(ListState.Empty, concurrent)

    override val items: MutableProperty<List<T>> = propertyOf<List<T>>(emptyList(), concurrent).also {
        it.addChangeListener { _, _ ->
            selectedItemId.value = selectedItemId.value // trigger selectedItem and selectedItemPosition changes
        }
    }

    override val selectedItemId: MutableProperty<ID> = propertyOf(noId, concurrent)

    override val selectedItem: MutableProperty<T?> = selectedItemId.bind(
            { id -> if (id == noId) null else items.value.firstOrNull { it.getId() == id } },
            { item -> if (item === null) noId else item.getId() }
    )

    override val selectedItemPosition: MutableProperty<Int> = selectedItemId.bind(
            { id: ID -> if (id == noId) -1 else items.value.indexOfFirst { it.getId() == id } },
            { position: Int -> if (position < 0) noId else items.value[position].getId() }
    )

    fun clear() {
        state.value = ListState.Empty
        selectedItemId.value = noId
    }

    fun deselect() {
        selectedItemId.value = noId
    }

}

enum class ListState {
    Empty, Loading, Ok, Error
}
