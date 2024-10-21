package com.velexsoft.places.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "places")
data class Place (
    @PrimaryKey @ColumnInfo(name = "id") val placeId: String,
    val name: String,
    val description: String,
    val country: String,
    val countryCode: String,
    val continent: String,
    val imageUrl: String = ""
)  {
    override fun toString() = name
}

@JvmInline
value class CountryCode ( val code: String )
val NoCountryCode = CountryCode("00")
