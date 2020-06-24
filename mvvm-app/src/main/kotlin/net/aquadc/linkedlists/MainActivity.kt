package net.aquadc.linkedlists

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Space
import android.widget.Spinner
import androidx.annotation.StringRes
import net.aquadc.properties.Property
import net.aquadc.properties.android.bindings.bindViewTo
import net.aquadc.properties.android.bindings.view.bindVisibilityHardlyTo
import net.aquadc.properties.android.bindings.view.setWhenClicked
import net.aquadc.properties.android.bindings.widget.bindTextTo
import net.aquadc.properties.anyValue
import net.aquadc.properties.function.isSameAs
import net.aquadc.properties.map
import net.aquadc.properties.mapValueList
import net.aquadc.properties.set
import splitties.dimensions.dip
import splitties.systemservices.connectivityManager
import splitties.views.dsl.core.button
import splitties.views.dsl.core.horizontalMargin
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.verticalMargin
import splitties.views.dsl.core.wrapContent
import splitties.views.textResource
import splitties.views.verticalPadding
import java.io.IOException


class MainActivity : InjectableActivity<LinkedListsViewModel>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(verticalLayout {
            verticalPadding = dip(16)

            addView(autoComplete {
                id = 1
                layoutParams = spaces()
                bind(vm.countries, Place.Name, R.string.hint_country)
            })

            addView(autoComplete {
                id = 2
                layoutParams = spaces()
                bind(vm.states, Place.Name, R.string.hint_state)
            })

            addView(autoComplete {
                id = 3
                layoutParams = spaces()
                bind(vm.cities, Place.Name, R.string.hint_city)
            })

            addView(Space(this@MainActivity), lParams(matchParent, 0, weight = 1f))

            val hasError = listOf(vm.countries.state, vm.states.state, vm.cities.state)
                .anyValue(isSameAs(ListState.Error))

            addView(textView {
                layoutParams = spaces()
                bindTextTo(vm.problem.map {
                    when (it) {
                        null -> ""
                        is IOException -> resources.getText(R.string.error_io)
                        else -> resources.getText(R.string.error_unexpected)
                    }
                })
                bindVisibilityHardlyTo(hasError)
            })

            addView(button {
                layoutParams = spaces()
                textResource = R.string.retry
                bindVisibilityHardlyTo(hasError)
                setWhenClicked(vm.retryRequested)
            })

            addView(Space(this@MainActivity), lParams(matchParent, 0, weight = 1f))

        })
    }

    private val connectivityReceiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context, intent: Intent?) {
            if (connectivityManager.activeNetworkInfo?.isConnected == true)
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

}

inline fun Context.autoComplete(init: AutoCompleteTextView.() -> Unit) =
    InstantAutoComplete(this).apply(init)

class InstantAutoComplete(context: Context) : AutoCompleteTextView(context) {
    override fun enoughToFilter(): Boolean = true
    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (focused && adapter != null)
             if (!maybeShowSuggestions())
                 post { maybeShowSuggestions() }
    }
    private fun maybeShowSuggestions(): Boolean =
        if (windowVisibility == View.VISIBLE) {
            performFiltering(text, 0)
            showDropDown()
            true
        } else {
            false
        }
}

fun View.spaces() =
    ViewGroup.MarginLayoutParams(matchParent, wrapContent).apply {
        verticalMargin = dip(4)
        horizontalMargin = dip(16)
    }

private fun <T : Any, ID> Spinner.bind(
    choice: SingleChoice<T, ID>, display: (T) -> CharSequence, @StringRes hint: Int
) {
    bindViewTo(choice.everything()) { spinner, (state, items, position) ->
        spinner.adapter = spinner.context.spinnerAdapter(state, items, display, hint)
        spinner.isEnabled = state === ListState.Ok
        spinner.setSelection(position + 1)
    }
    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {}
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val pos = position - 1
            if (choice.state.value === ListState.Ok && // if our choice is meaningful
                pos >= 0 && // and not -1 (hint)
                choice.selectedItemPosition.value != pos) { // and not equal to current (don't recurse)
                choice.selectedItemPosition.value = pos
            }
        }
    }
}

private fun <T> Context.spinnerAdapter(
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

private fun <T : Any, ID> AutoCompleteTextView.bind(
    choice: SingleChoice<T, ID>, display: (T) -> CharSequence, @StringRes hint: Int
) {
    val adapter = ArrayAdapter<CharSequence>(context, android.R.layout.simple_dropdown_item_1line).also(::setAdapter)
    bindViewTo(choice.everything()) { view, (state, items, position) ->
        val displayItems = items.map(display)
        adapter.clear(); adapter.addAll(displayItems)
        if (state === ListState.Ok) {
            isEnabled = true
        } else {
            isEnabled = false
            setText("")
        }
        view.setHint(when (state) {
            ListState.Empty -> R.string.list_empty
            ListState.Loading -> R.string.list_loading
            ListState.Ok -> hint
            ListState.Error -> R.string.list_error
        })
        listSelection = position
        setOnItemClickListener { _, _, pos, _ ->
            val pos = displayItems.indexOf(adapter.getItem(pos))
            if (choice.state.value === ListState.Ok && // if our choice is meaningful
                choice.selectedItemPosition.value != pos) { // and not equal to current (don't recurse)
                choice.selectedItemPosition.value = pos
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> SingleChoice<T, *>.everything(): Property<Triple<ListState, List<T>, Int>> =
    listOf(state, items, selectedItemPosition)
        .mapValueList { Triple(it[0] as ListState, it[1] as List<T>, it[2] as Int) }
