package net.aquadc.linkedlists

import android.app.Application
import android.database.Cursor
import android.os.Bundle
import net.aquadc.persistence.sql.Session
import net.aquadc.persistence.sql.blocking.Blocking
import net.aquadc.properties.persistence.memento.PersistableProperties
import okhttp3.OkHttpClient
import java.io.Closeable
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit


class LinkedListsApp : Application() {

    // region MainActivity deps

    private lateinit var db: Session<Blocking<Cursor>>

    private val httpApi = lazy {
        HttpApi(
            OkHttpClient.Builder()
                .callTimeout(10, TimeUnit.SECONDS)
                .build(),
            BuildConfig.SERVER,
            HttpContract(BuildConfig.PATH)
        )
    }

    private val io =
            ThreadPoolExecutor(0, 8, 30, TimeUnit.SECONDS, LinkedBlockingQueue())

    // endregion MainActivity deps

    override fun onCreate() {
        super.onCreate()
        db = PlacesDatabase()
        registerActivityLifecycleCallbacks(object : LifecycleCallbacksInjector() {
            override fun <VM> injectInto(activity: InjectableActivity<VM>, savedInstanceState: Bundle?)
                    where VM : PersistableProperties, VM : Closeable {
                when (activity) {
                    is MainActivity ->
                        activity.vm = LinkedListsViewModel(db, httpApi, io, savedInstanceState?.getParcelable("vm"))
                    else ->
                        throw AssertionError()
                }
            }
        })
    }

}
