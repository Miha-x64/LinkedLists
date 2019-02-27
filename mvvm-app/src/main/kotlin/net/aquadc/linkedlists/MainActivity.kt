package net.aquadc.linkedlists

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import android.support.annotation.StringRes
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import net.aquadc.properties.Property
import net.aquadc.properties.android.bindings.bindViewTo
import net.aquadc.properties.android.bindings.view.bindVisibilityHardlyTo
import net.aquadc.properties.android.bindings.view.setWhenClicked
import net.aquadc.properties.android.bindings.widget.bindTextTo
import net.aquadc.properties.android.persistence.parcel.ParcelPropertiesMemento
import net.aquadc.properties.anyValue
import net.aquadc.properties.function.isSameAs
import net.aquadc.properties.map
import net.aquadc.properties.mapValueList
import net.aquadc.properties.set
import org.jetbrains.anko.button
import org.jetbrains.anko.connectivityManager
import org.jetbrains.anko.dip
import org.jetbrains.anko.horizontalMargin
import org.jetbrains.anko.matchParent
import org.jetbrains.anko.space
import org.jetbrains.anko.spinner
import org.jetbrains.anko.textResource
import org.jetbrains.anko.textView
import org.jetbrains.anko.verticalLayout
import org.jetbrains.anko.verticalMargin
import org.jetbrains.anko.verticalPadding
import org.jetbrains.anko.wrapContent
import java.io.IOException


class MainActivity : Activity() {

    private lateinit var vm: LinkedListsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vm = (lastNonConfigurationInstance as LinkedListsViewModel?)
                ?: LinkedListsViewModel(savedInstanceState?.getParcelable("vm"))

        verticalLayout {
            verticalPadding = dip(16)

            spinner {
                layoutParams = spaces()
                bind(vm.countries, Place::name, R.string.hint_country)
            }

            spinner {
                layoutParams = spaces()
                bind(vm.states, Place::name, R.string.hint_state)
            }

            spinner {
                layoutParams = spaces()
                bind(vm.cities, Place::name, R.string.hint_city)
            }

            space().lparams(matchParent, 0, weight = 1f)

            val hasError = listOf(vm.countries.state, vm.states.state, vm.cities.state)
                    .anyValue(isSameAs(ListState.Error))

            textView {
                layoutParams = spaces()
                bindTextTo(vm.problem.map { when (it) {
                    null -> ""
                    is IOException -> resources.getText(R.string.error_io)
                    else -> resources.getText(R.string.error_unexpected)
                } })
                bindVisibilityHardlyTo(hasError)
            }

            button {
                layoutParams = spaces()
                textResource = R.string.retry
                bindVisibilityHardlyTo(hasError)
                setWhenClicked(vm.retryRequested)
            }

            space().lparams(matchParent, 0, weight = 1f)

        }
    }

    private val connectivityReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent?) {
            if (context.connectivityManager.activeNetworkInfo?.isConnected == true)
                vm.retryRequested.set()
        }

    }

    @Suppress("DEPRECATION")
    override fun onStart() {
        super.onStart()
        registerReceiver(connectivityReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }

    override fun onStop() {
        unregisterReceiver(connectivityReceiver)
        super.onStop()
    }

    override fun onRetainNonConfigurationInstance(): Any? =
            vm // don't reload data lists from scratch

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("vm", ParcelPropertiesMemento(vm))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing)
            vm.destroy()
    }

}

fun View.spaces() =
        ViewGroup.MarginLayoutParams(matchParent, wrapContent).apply {
            verticalMargin = dip(4)
            horizontalMargin = dip(16)
        }

private fun <T : Any, ID> AdapterView<*>.bind(
        choice: SingleChoice<T, ID>, display: (T) -> CharSequence, @StringRes hint: Int
) {
    bindViewTo(choice.everything()) { spinner, (state, items, position) ->
        spinner.adapter = spinner.context.arrayAdapter(state, items, display, hint)
        spinner.isEnabled = state === ListState.Ok
        spinner.setSelection(position + 1)
    }
    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {}
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val pos = position - 1
            if (choice.state.value === ListState.Ok && // if out choice is meaningful
                    pos >= 0 && // and not -1 (hint)
                    choice.selectedItemPosition.value != pos) { // and not equal to current (don't recurse)
                choice.selectedItemPosition.value = pos
            }
        }
    }
}

private fun <T> Context.arrayAdapter(
        state: ListState, items: List<T>, display: (T) -> CharSequence, @StringRes hint: Int
): ArrayAdapter<CharSequence> {
    val itemNames = when (state) {
        ListState.Empty -> listOf(resources.getText(R.string.list_empty))
        ListState.Loading -> listOf(resources.getText(R.string.list_loading))
        ListState.Ok -> {
            val size = items.size
            val names = arrayOfNulls<CharSequence>(size + 1)
            names[0] = resources.getText(hint)
            for (i in items.indices) {
                names[i + 1] = display(items[i])
            }
            names.asList()
        }
        ListState.Error -> listOf(resources.getText(R.string.list_error))
    }

    return object : ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_item, itemNames) {

        init {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        override fun isEnabled(position: Int): Boolean =
                position != 0 // 0 is an unselectable hint

    }

}

@Suppress("UNCHECKED_CAST")
fun <T : Any> SingleChoice<T, *>.everything(): Property<Triple<ListState, List<T>, Int>> =
        listOf(state, items, selectedItemPosition)
                .mapValueList { Triple(it[0] as ListState, it[1] as List<T>, it[2] as Int) }
