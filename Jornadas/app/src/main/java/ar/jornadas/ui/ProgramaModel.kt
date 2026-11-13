package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.JornadasApp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException

class ProgramaModel(app: Application) : AndroidViewModel(app) {
    // Se toma el Servicios unico de la Application en lugar de crear uno nuevo.
    private val servicios = (app as JornadasApp).servicios
    val mesa = servicios.mesa
    private val repo = servicios.agenda
    val charlas = repo.programa.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val dia = MutableStateFlow(0)
    val aviso = MutableStateFlow("")
    val cargando = MutableStateFlow(false)

    fun recargar() {
        viewModelScope.launch {
            cargando.value = true
            aviso.value = ""
            try {
                repo.sincronizar()
            } catch (e: IOException) {
                aviso.value = "No se pudo consultar el programa"
            } finally {
                cargando.value = false
            }
        }
    }

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }
}
