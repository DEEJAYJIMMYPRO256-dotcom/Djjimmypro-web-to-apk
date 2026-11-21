package com.example.dj.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks")
data class TrackEntity(
  @PrimaryKey val id: String,
  val title: String,
  val artist: String,
  val bpm: Float,
  val musicalKey: String,
  val genre: String,
  val durationSeconds: Int,
  val baseFreqHz: Float = 220f,
  val filePath: String? = null,
  val beatGridJson: String = "[]",
  val waveformPeaksJson: String = "[]",
  val addedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "session_history")
data class SessionHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Int = 0,
  val trackTitle: String,
  val artist: String,
  val deckName: String,
  val playedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "eq_presets")
data class EqPresetEntity(
  @PrimaryKey(autoGenerate = true) val id: Int = 0,
  val name: String,
  val low: Float,
  val mid: Float,
  val high: Float,
)

@Dao
interface TrackDao {
  @Query("SELECT * FROM tracks ORDER BY addedAtMs DESC")
  fun getAllTracks(): Flow<List<TrackEntity>>

  @Query("SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' ORDER BY addedAtMs DESC")
  fun searchTracks(query: String): Flow<List<TrackEntity>>

  @Query("SELECT * FROM tracks WHERE beatGridJson = '[]' OR beatGridJson = ''")
  suspend fun getTracksMissingBeatGrid(): List<TrackEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTrack(track: TrackEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAllIgnore(tracks: List<TrackEntity>)

  @Query("DELETE FROM tracks WHERE id = :id")
  suspend fun deleteTrackById(id: String)
}

@Dao
interface SessionHistoryDao {
  @Query("SELECT * FROM session_history ORDER BY playedAtMs DESC")
  fun getAllHistory(): Flow<List<SessionHistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertHistory(entry: SessionHistoryEntity)

  @Query("DELETE FROM session_history")
  suspend fun clearHistory()
}

@Dao
interface EqPresetDao {
  @Query("SELECT * FROM eq_presets ORDER BY id ASC")
  fun getAllPresets(): Flow<List<EqPresetEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPreset(preset: EqPresetEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAllIgnore(presets: List<EqPresetEntity>)

  @Query("SELECT COUNT(*) FROM eq_presets")
  suspend fun countPresets(): Int
}

@Database(
  entities = [TrackEntity::class, SessionHistoryEntity::class, EqPresetEntity::class],
  version = 5,
  exportSchema = false,
)
abstract class TrackLibraryDatabase : RoomDatabase() {
  abstract fun trackDao(): TrackDao

  abstract fun sessionHistoryDao(): SessionHistoryDao

  abstract fun eqPresetDao(): EqPresetDao

  companion object {
    @Volatile private var instance: TrackLibraryDatabase? = null

    fun getInstance(context: Context): TrackLibraryDatabase {
      return instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                TrackLibraryDatabase::class.java,
                "stemdeck_track_library.db",
              )
              .fallbackToDestructiveMigration(true)
              .build()
              .also { instance = it }
        }
    }
  }
}
