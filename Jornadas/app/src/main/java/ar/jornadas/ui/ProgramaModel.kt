package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.JornadasApp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException

// SavedStateHandle guarda los filtros de forma que sobreviven a que Android mate el proceso.
class ProgramaModel(app: Application, private val estadoGuardado: SavedStateHandle) : AndroidViewModel(app) {
    // Se toma el Servicios unico de la Application en lugar de crear uno nuevo.
    private val aplicacion = app as JornadasApp
    private val servicios = aplicacion.servicios
    val mesa = servicios.mesa
    private val repo = servicios.agenda
    val charlas = repo.programa.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    // Filtros: viven en el ViewModel (sobreviven a rotar y a abrir una ficha)
    // y en SavedStateHandle (sobreviven a que maten el proceso).
    val dia = estadoGuardado.getStateFlow("dia", 0)
    val tema = estadoGuardado.getStateFlow("tema", "Todos")
    val aviso = MutableStateFlow("")
    val cargando = MutableStateFlow(false)

    // True cuando la ultima actualizacion fallo pero hay una edicion guardada:
    // la lista, el detalle y las favoritas siguen visibles y solo se avisa.
    val usandoDatosGuardados = MutableStateFlow(false)

    // True cuando la edicion descargada es valida pero no trae charlas (caso v4).
    // combine espera a que ambos flujos emitan, asi no aparece un falso "sin charlas" al abrir.
    val sinCharlasProgramadas = combine(repo.programa, repo.hayEdicionGuardada) { listaDeCharlas, hayEdicion ->
        hayEdicion && listaDeCharlas.isEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Carga automatica: solo ocurre la primera vez que se abre la app en este proceso.
    // Rotar, volver del fondo, abrir una ficha o cambiar de pestana no la repiten.
    fun cargarAlIniciar() {
        if (aplicacion.cargaAutomaticaRealizada) return
        aplicacion.cargaAutomaticaRealizada = true
        recargar()
    }

    // Actualizacion explicita (botones Actualizar y Reintentar).
    fun recargar() {
        // Si ya hay una peticion en curso, se ignora el toque: dos toques seguidos suman un solo intento.
        // Se marca "cargando" antes de lanzar la corrutina para que el segundo toque ya lo vea.
        if (cargando.value) return
        cargando.value = true
        viewModelScope.launch {
            aviso.value = ""
            usandoDatosGuardados.value = false
            try {
                repo.sincronizar()
            } catch (e: IOException) {
                // Si ya hubo una descarga exitosa se conservan los datos y solo se avisa.
                // Si nunca hubo descarga, se muestra el error con el boton Reintentar.
                if (repo.hayEdicionGuardada.first()) {
                    usandoDatosGuardados.value = true
                } else {
                    aviso.value = "No se pudo consultar el programa"
                }
            } finally {
                cargando.value = false
            }
        }
    }

    fun elegirDia(nuevoDia: Int) { estadoGuardado["dia"] = nuevoDia }

    fun elegirTema(nuevoTema: String) { estadoGuardado["tema"] = nuevoTema }

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }
}
