package net.aquadc.linkedlists

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import net.aquadc.persistence.sql.blocking.SqliteSession
import net.aquadc.persistence.sql.dialect.sqlite.SqliteDialect

///////////////////////////////////////////
// WARNING                               //
// Lychee SQL API is a subject to change //
///////////////////////////////////////////

fun Context.PlacesDatabase() = SqliteSession(object : SQLiteOpenHelper(this, "places", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SqliteDialect.createTable(Countries))
        db.execSQL(SqliteDialect.createTable("states", "countries"))
        db.execSQL(SqliteDialect.createTable("cities", "states"))
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        throw UnsupportedOperationException()
    }
}.writableDatabase.also {
    it.setForeignKeyConstraintsEnabled(true)
})
