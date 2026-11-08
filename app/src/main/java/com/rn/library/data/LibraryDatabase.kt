package com.rn.library.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

/** Lists remain structured and lossless, including punctuation and multiline user text. */
class WorkConverters {
    @TypeConverter fun stringsToJson(value: List<String>): String = JSONArray(value).toString()
    @TypeConverter fun jsonToStrings(value: String): List<String> = JSONArray(value).let { array ->
        List(array.length()) { array.getString(it) }
    }
    @TypeConverter fun periodsToJson(value: List<ReadingPeriod>): String = JSONArray().apply {
        value.forEach { put(JSONObject().put("start", it.startDate).put("end", it.endDate ?: JSONObject.NULL)) }
    }.toString()
    @TypeConverter fun jsonToPeriods(value: String): List<ReadingPeriod> = JSONArray(value).let { array ->
        List(array.length()) { i -> array.getJSONObject(i).let {
            ReadingPeriod(it.getString("start"), if (it.isNull("end")) null else it.getString("end"))
        } }
    }
    @TypeConverter fun unitsToJson(value: List<UnitProgress>): String = JSONArray().apply {
        value.forEach { put(JSONObject().put("name", it.unitName).put("completed", it.completed)
            .put("total", it.total ?: JSONObject.NULL)) }
    }.toString()
    @TypeConverter fun jsonToUnits(value: String): List<UnitProgress> = JSONArray(value).let { array ->
        List(array.length()) { i -> array.getJSONObject(i).let {
            UnitProgress(it.getString("name"), it.getDouble("completed"),
                if (it.isNull("total")) null else it.getDouble("total"))
        } }
    }
}

@Entity(tableName = "activity_events", primaryKeys = ["date", "workId"], indices = [Index("workId")])
data class StoredActivityEvent(val date: String, val workId: String, val readDelta: Double, val watchDelta: Double)

@Entity(tableName = "storage_state")
data class StorageState(@PrimaryKey val key: String)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM works ORDER BY id") fun getAllWorks(): List<Work>
    @Query("SELECT * FROM works ORDER BY id") fun observeWorks(): Flow<List<Work>>
    @Query("SELECT * FROM works WHERE id = :id") fun getWork(id: String): Work?
    @Upsert fun saveWork(work: Work)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun importLegacyWork(work: Work)
    @Query("DELETE FROM works WHERE id = :id") fun deleteWork(id: String): Int
    @Query("SELECT EXISTS(SELECT 1 FROM storage_state WHERE `key` = :key)") fun hasState(key: String): Boolean
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun markState(state: StorageState)
    @Query("SELECT `key` FROM storage_state WHERE `key` LIKE 'skipped_markdown/%' ORDER BY `key`")
    fun skippedLegacyFiles(): List<String>
    @Query("SELECT * FROM activity_events ORDER BY date") fun getEvents(): List<StoredActivityEvent>
    @Query("SELECT * FROM activity_events WHERE date = :date AND workId = :workId")
    fun getEvent(date: String, workId: String): StoredActivityEvent?
    @Query("SELECT COUNT(*) FROM activity_events") fun eventCount(): Int
    @Delete fun deleteEvent(event: StoredActivityEvent)
    @Query("SELECT * FROM activity_events WHERE workId = :workId OR workId = 'imported_stats' ORDER BY date")
    fun getEventsForWork(workId: String): List<StoredActivityEvent>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun saveEvents(events: List<StoredActivityEvent>)
    @Query("DELETE FROM activity_events") fun clearEvents()
}

@Database(entities = [Work::class, StoredActivityEvent::class, StorageState::class], version = 1, exportSchema = true)
@TypeConverters(WorkConverters::class)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        @Volatile private var instance: LibraryDatabase? = null

        fun get(context: Context): LibraryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, "library.db")
                .build().also { instance = it }
        }
    }
}
