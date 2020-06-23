package net.aquadc.linkedlists

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import net.aquadc.persistence.sql.Table
import net.aquadc.persistence.sql.blocking.SqliteSession
import net.aquadc.persistence.sql.dialect.sqlite.SqliteDialect

///////////////////////////////////////////
// WARNING                               //
// Lychee SQL API is a subject to change //
///////////////////////////////////////////

val Countries: Table<Place, Int> = Table(Place, "countries", Place.Id)
val States: Table<Place, Int> = Table(Place, "states", Place.Id)
val Cities: Table<Place, Int> = Table(Place, "cities", Place.Id)

fun Context.PlacesDatabase() = SqliteSession(object : SQLiteOpenHelper(this, "places", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SqliteDialect.createTable(Countries))
        db.createTable("states", "countries")
        db.createTable("cities", "states")
    }
    private fun SQLiteDatabase.createTable(name: String, parentName: String) {
        execSQL("""CREATE TABLE "$name" (
                "id" INTEGER NOT NULL PRIMARY KEY,
                "name" TEXT NOT NULL,
                "parent_id" INTEGER NOT NULL,
                FOREIGN KEY("parent_id") REFERENCES "$parentName"("id")
            );""".trimIndent()) // Lychee SqliteDialect is still unaware of FOREIGN KEY
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        throw UnsupportedOperationException()
    }
}.writableDatabase.also {
    it.setForeignKeyConstraintsEnabled(true)
})
