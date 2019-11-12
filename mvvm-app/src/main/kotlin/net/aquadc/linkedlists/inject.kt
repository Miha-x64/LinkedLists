package net.aquadc.linkedlists

import android.app.Activity
import android.app.Application
import android.os.Bundle
import net.aquadc.persistence.android.parcel.ParcelPropertiesMemento
import net.aquadc.properties.persistence.memento.PersistableProperties
import java.io.Closeable


abstract class InjectableActivity<VM> : Activity()
        where VM : PersistableProperties, VM : Closeable {

    lateinit var vm: VM

    final override fun onRetainNonConfigurationInstance(): Any? =
            vm

}

abstract class LifecycleCallbacksInjector : Application.ActivityLifecycleCallbacks {

    @Suppress("UPPER_BOUND_VIOLATED", "UNCHECKED_CAST")
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is InjectableActivity<*>) {
            activity as InjectableActivity<Any>
            val vm = activity.lastNonConfigurationInstance
            if (vm == null) injectInto<Any>(activity, savedInstanceState)
            else (activity).vm = vm
        }
    }

    protected abstract fun <VM> injectInto(
            activity: InjectableActivity<VM>, savedInstanceState: Bundle?
    ) where VM : PersistableProperties, VM : Closeable

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        if (activity is InjectableActivity<*>)
            outState.putParcelable("vm", ParcelPropertiesMemento(activity.vm))
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity.isFinishing && activity is InjectableActivity<*>)
            (activity.vm as Closeable).close()
        //           ^^ workaround for https://youtrack.jetbrains.com/issue/KT-7389
    }

}
