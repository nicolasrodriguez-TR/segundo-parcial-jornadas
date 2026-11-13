package ar.jornadas.data

import kotlinx.coroutines.flow.Flow

// Contrato del repositorio de la agenda.
// El ViewModel depende de esta interfaz y no de la implementacion:
// asi se puede probar con un repositorio falso, sin base de datos ni red.
interface AgendaRepository {
    // Programa guardado en la base; emite de nuevo cada vez que cambia.
    val programa: Flow<List<Charla>>

    // True si alguna vez se descargó una edición completa (aunque no tuviera charlas).
    val hayEdicionGuardada: Flow<Boolean>

    // Devuelve true solo la primera vez que se llama en este proceso: ahi corresponde la carga automatica.
    fun tocaCargaAutomatica(): Boolean

    // Pide la edición al servidor y la guarda.
    // Devuelve true si se guardó una edición nueva y false si no se pudo obtener.
    suspend fun sincronizar(): Boolean

    // Agrega o quita la charla de "Mi agenda".
    suspend fun cambiarFavorita(id: String)
}
