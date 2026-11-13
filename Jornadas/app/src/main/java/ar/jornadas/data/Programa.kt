package ar.jornadas.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import retrofit2.http.GET
import kotlinx.coroutines.flow.Flow

data class FichaRemota(val id: String, val title: String, val track: String, val day: Int,
    val time: String, val room: String, val speaker: String, val summary: String)

interface ProgramaApi {
    @GET("programa") suspend fun consultar(): List<FichaRemota>
}

@Entity(tableName = "charlas")
data class Charla(@PrimaryKey val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String,
    val favorita: Boolean = false)

// Una sola fila (id = 1). Si existe, ya hubo al menos una descarga exitosa,
// aunque esa descarga haya traído cero charlas.
@Entity(tableName = "estado_sync")
data class EstadoSync(@PrimaryKey val id: Int = 1, val sincronizadoEn: Long)

@Dao
interface CarteleraDao {
    @Query("SELECT * FROM charlas ORDER BY dia, hora, sala")
    fun observar(): Flow<List<Charla>>

    @Query("SELECT id FROM charlas WHERE favorita = 1")
    suspend fun idsFavoritas(): List<String>

    @Query("DELETE FROM charlas")
    suspend fun borrarTodas()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(fichas: List<Charla>)

    @Query("UPDATE charlas SET favorita = NOT favorita WHERE id = :id")
    suspend fun alternar(id: String)

    @Query("SELECT * FROM estado_sync WHERE id = 1")
    fun observarEstado(): Flow<EstadoSync?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun marcarSincronizado(estado: EstadoSync)
}

@Database(entities = [Charla::class, EstadoSync::class], version = 2, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao
}

// Conserva las charlas y favoritas de los usuarios que ya tienen la app instalada.
// Si ya tenían charlas guardadas, se las considera una edición descargada.
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `estado_sync` " +
                "(`id` INTEGER NOT NULL, `sincronizadoEn` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("INSERT INTO estado_sync (id, sincronizadoEn) SELECT 1, 0 FROM charlas LIMIT 1")
    }
}
