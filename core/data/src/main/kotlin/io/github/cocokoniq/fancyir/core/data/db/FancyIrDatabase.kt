package io.github.cocokoniq.fancyir.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CatalogCodesetEntity::class,
        CatalogCommandEntity::class,
        VaultDeviceEntity::class,
        VaultCommandEntity::class,
        VaultEmbeddingEntity::class,
        VaultAnchorEntity::class,
        ClimateStateEntity::class,
        SettingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FancyIrDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao
    abstract fun vault(): VaultDao
    abstract fun settings(): SettingsDao

    companion object {
        private const val NAME = "fancy-ir.db"

        fun open(context: Context): FancyIrDatabase =
            Room.databaseBuilder(context.applicationContext, FancyIrDatabase::class.java, NAME).build()

        fun inMemory(context: Context): FancyIrDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, FancyIrDatabase::class.java).build()
    }
}
