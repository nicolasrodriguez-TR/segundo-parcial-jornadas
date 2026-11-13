package ar.jornadas.data

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
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

    // Indica si en este proceso ya se hizo la carga automatica.
    // El repositorio se crea una sola vez por proceso (en Servicios), asi que esta marca dura lo mismo que el proceso.
    private var cargaAutomaticaRealizada = false

    // Devuelve true solo la primera vez que se llama en este proceso: ahi corresponde la carga automatica.
    fun tocaCargaAutomatica(): Boolean {
        if (cargaAutomaticaRealizada) return false
        cargaAutomaticaRealizada = true
        return true
    }

    // Pide la edición al servidor y la guarda.
    // Devuelve true si se guardó una edición nueva y false si no se pudo obtener.
    // Si falla, no se toca nada de lo guardado: lista, detalle y favoritas quedan como estaban.
    suspend fun sincronizar(): Boolean {
        val fichasNuevas = try {
            api.consultar().map {
                Charla(it.id, it.title, it.track, it.day, it.time, it.room, it.speaker, it.summary)
            }
        } catch (e: CancellationException) {
            // La corrutina se canceló (por ejemplo, se cerró la pantalla): no es una falla, se deja seguir.
            throw e
        } catch (e: Exception) {
            // Cualquier falla al pedir o al leer la respuesta:
            // sin conexión (IOException), el servidor respondió 4xx/5xx (HttpException)
            // o el JSON no tiene el formato esperado (errores de Gson o campos nulos).
            return false
        }
        reemplazarPrograma(fichasNuevas)
        return true
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
