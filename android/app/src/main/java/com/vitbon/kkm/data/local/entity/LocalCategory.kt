package com.vitbon.kkm.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class LocalCategory(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String?
)
