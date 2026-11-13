package ar.jornadas.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AgendaRepository(
    private val api: ProgramaApi,
    private val dao: CarteleraDao,
    private val archivo: Archivo
) {
    val programa = dao.observar()

    // True si alguna vez se descargó una edición completa (aunque no tuviera charlas).
    val hayEdicionGuardada: Flow<Boolean> = dao.observarEstado().map { estado -> estado != null }

    suspend fun sincronizar() {
        val fichasNuevas = api.consultar().map {
            Charla(it.id, it.title, it.track, it.day, it.time, it.room, it.speaker, it.summary)
        }
        reemplazarPrograma(fichasNuevas)
    }

    // Todo ocurre en una transaccion: las favoritas se leen y se escriben juntas,
    // asi una marca cambiada mientras esperaba la respuesta no se pierde.
    private suspend fun reemplazarPrograma(fichasNuevas: List<Charla>) {
        archivo.withTransaction {
            val idsFavoritas = dao.idsFavoritas().toSet()
            val fichasConMarca = fichasNuevas.map { ficha ->
                ficha.copy(favorita = ficha.id in idsFavoritas)
            }
            // Borrar todo elimina las charlas retiradas; si una vuelve, entra sin marca.
            dao.borrarTodas()
            dao.guardar(fichasConMarca)
            dao.marcarSincronizado(EstadoSync(sincronizadoEn = System.currentTimeMillis()))
        }
    }

    suspend fun cambiarFavorita(id: String) = dao.alternar(id)
}
