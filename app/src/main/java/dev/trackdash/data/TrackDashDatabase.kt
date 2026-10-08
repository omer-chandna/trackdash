package dev.trackdash.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks")
data class TrackRow(@PrimaryKey val id: String, val name: String, val json: String)
@Entity(tableName = "vehicles")
data class VehicleRow(@PrimaryKey val id: String, val name: String, val manufacturer: String,
    val model: String, val year: Int, val engine: String, val vin: String? = null, val capabilities: String = "")
@Entity(tableName = "sessions")
data class SessionRow(@PrimaryKey val id: String, val startedMillis: Long, val endedMillis: Long? = null,
    val mode: String, val simulated: Boolean, val trackId: String?, val vehicleId: String?,
    val status: String = "RECORDING", val notes: String = "")
@Entity(tableName = "samples", indices = [Index(value = ["sessionId", "elapsedNanos"])],
    foreignKeys = [ForeignKey(entity = SessionRow::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)])
data class SampleRow(@PrimaryKey(autoGenerate = true) val id: Long = 0, val sessionId: String,
    val elapsedNanos: Long, val wallMillis: Long, val kind: String, val payload: String)

@Dao
interface TrackDashDao {
    @Query("SELECT * FROM tracks ORDER BY name") fun tracks(): Flow<List<TrackRow>>
    @Query("SELECT * FROM tracks WHERE id = :id") suspend fun track(id: String): TrackRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveTrack(track: TrackRow)
    @Query("DELETE FROM tracks WHERE id = :id") suspend fun deleteTrack(id: String)
    @Query("SELECT * FROM vehicles ORDER BY name") fun vehicles(): Flow<List<VehicleRow>>
    @Query("SELECT * FROM vehicles WHERE id = :id") suspend fun vehicle(id: String): VehicleRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveVehicle(vehicle: VehicleRow)
    @Query("SELECT * FROM sessions ORDER BY startedMillis DESC") fun sessions(): Flow<List<SessionRow>>
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun session(id: String): SessionRow?
    @Insert suspend fun insertSession(session: SessionRow)
    @Query("UPDATE sessions SET status = :status, endedMillis = :ended WHERE id = :id") suspend fun endSession(id: String, ended: Long, status: String)
    @Query("UPDATE sessions SET status = 'INTERRUPTED' WHERE status = 'RECORDING'") suspend fun recoverInterrupted()
    @Query("UPDATE sessions SET notes = :notes WHERE id = :id") suspend fun setNotes(id: String, notes: String)
    @Insert suspend fun insertSamples(samples: List<SampleRow>)
    @Query("SELECT * FROM samples WHERE sessionId = :sessionId AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun samplePage(sessionId: String, afterId: Long = 0, limit: Int = 500): List<SampleRow>
    @Query("SELECT * FROM samples WHERE sessionId = :sessionId AND kind = :kind ORDER BY elapsedNanos")
    fun events(sessionId: String, kind: String): Flow<List<SampleRow>>
    @Query("SELECT COUNT(*) FROM samples WHERE sessionId = :sessionId") fun sampleCount(sessionId: String): Flow<Long>
    @Query("DELETE FROM sessions WHERE id = :id") suspend fun deleteSession(id: String)
}

@Database(entities = [TrackRow::class, VehicleRow::class, SessionRow::class, SampleRow::class], version = 1, exportSchema = true)
abstract class TrackDashDatabase : RoomDatabase() { abstract fun dao(): TrackDashDao }
