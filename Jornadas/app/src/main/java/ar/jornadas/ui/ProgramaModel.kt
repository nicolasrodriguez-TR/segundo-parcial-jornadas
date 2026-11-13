package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.JornadasApp
import ar.jornadas.data.Charla
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    // Charlas del programa ya filtradas por dia y tema: la pantalla solo las dibuja.
    val charlasFiltradas: StateFlow<List<Charla>> =
        combine(repo.programa, dia, tema) { listaDeCharlas, diaElegido, temaElegido ->
            listaDeCharlas.filter { charla -> coincideConFiltros(charla, diaElegido, temaElegido) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Charlas marcadas por el usuario (pestana "Mi agenda").
    val miAgenda: StateFlow<List<Charla>> =
        repo.programa.map { listaDeCharlas -> listaDeCharlas.filter { charla -> charla.favorita } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    // Cada estado tiene una version privada que solo el ViewModel modifica (_x)
    // y una version publica de solo lectura que observa la pantalla (x).
    private val _aviso = MutableStateFlow("")
    val aviso: StateFlow<String> = _aviso.asStateFlow()

    private val _cargando = MutableStateFlow(false)
    val cargando: StateFlow<Boolean> = _cargando.asStateFlow()

    // True cuando la ultima actualizacion fallo pero hay una edicion guardada:
    // la lista, el detalle y las favoritas siguen visibles y solo se avisa.
    private val _usandoDatosGuardados = MutableStateFlow(false)
    val usandoDatosGuardados: StateFlow<Boolean> = _usandoDatosGuardados.asStateFlow()

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
        if (_cargando.value) return
        _cargando.value = true
        viewModelScope.launch {
            _aviso.value = ""
            _usandoDatosGuardados.value = false
            try {
                val seActualizo = repo.sincronizar()
                if (!seActualizo) {
                    informarFalla()
                }
            } finally {
                _cargando.value = false
            }
        }
    }

    // Si ya hubo una descarga exitosa se conservan los datos y solo se avisa.
    // Si nunca hubo descarga, se muestra el error con el boton Reintentar.
    private suspend fun informarFalla() {
        if (repo.hayEdicionGuardada.first()) {
            _usandoDatosGuardados.value = true
        } else {
            _aviso.value = "No se pudo consultar el programa"
        }
    }

    // dia 0 significa "Ambos dias"; el tema "Todos" no filtra por tema.
    private fun coincideConFiltros(charla: Charla, diaElegido: Int, temaElegido: String): Boolean {
        val coincideDia = diaElegido == 0 || charla.dia == diaElegido
        val coincideTema = temaElegido == "Todos" || charla.tema == temaElegido
        return coincideDia && coincideTema
    }

    fun elegirDia(nuevoDia: Int) { estadoGuardado["dia"] = nuevoDia }

    fun elegirTema(nuevoTema: String) { estadoGuardado["tema"] = nuevoTema }

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }
}
