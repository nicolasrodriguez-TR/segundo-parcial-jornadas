package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.JornadasApp
import ar.jornadas.data.Charla
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Claves con las que se guardan los filtros en SavedStateHandle.
private const val CLAVE_DIA = "dia"
private const val CLAVE_TEMA = "tema"

// SavedStateHandle guarda los filtros de forma que sobreviven a que Android mate el proceso.
class ProgramaModel(app: Application, private val estadoGuardado: SavedStateHandle) : AndroidViewModel(app) {
    // Filtros elegidos por el usuario, juntos en un objeto.
    private data class Filtros(val dia: Int, val tema: String)

    // Se toma el Servicios unico de la Application en lugar de crear uno nuevo.
    private val aplicacion = app as JornadasApp
    private val servicios = aplicacion.servicios
    val mesa = servicios.mesa
    private val repo = servicios.agenda

    // Programa completo: lo usa la ficha de detalle para buscar una charla por id.
    val charlas = repo.programa.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Charlas marcadas por el usuario (pestana "Mi agenda").
    val miAgenda: StateFlow<List<Charla>> =
        repo.programa.map { listaDeCharlas -> listaDeCharlas.filter { charla -> charla.favorita } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Filtros: viven en el ViewModel (sobreviven a rotar y a abrir una ficha)
    // y en SavedStateHandle (sobreviven a que maten el proceso).
    private val filtros: Flow<Filtros> = combine(
        estadoGuardado.getStateFlow(CLAVE_DIA, 0),
        estadoGuardado.getStateFlow(CLAVE_TEMA, "Todos"),
    ) { diaElegido, temaElegido -> Filtros(diaElegido, temaElegido) }

    // Estado de la descarga: solo el ViewModel lo modifica.
    private val cargando = MutableStateFlow(false)
    private val resultadoDescarga = MutableStateFlow<ResultadoDescarga>(ResultadoDescarga.SinFalla)

    // Estado unico de la pantalla "Programa", de solo lectura.
    // combine espera a que la base emita, asi no aparece un falso "sin charlas" al abrir.
    val uiState: StateFlow<ProgramaUiState> = combine(
        repo.programa,
        repo.hayEdicionGuardada,
        filtros,
        cargando,
        resultadoDescarga,
    ) { todasLasCharlas, hayEdicionGuardada, filtrosElegidos, estaCargando, resultado ->
        ProgramaUiState(
            dia = filtrosElegidos.dia,
            tema = filtrosElegidos.tema,
            charlas = todasLasCharlas.filter { charla -> coincideConFiltros(charla, filtrosElegidos) },
            cargando = estaCargando,
            resultadoDescarga = resultado,
            sinCharlasProgramadas = hayEdicionGuardada && todasLasCharlas.isEmpty(),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        // Mientras la base no emite, los filtros ya muestran lo guardado.
        ProgramaUiState(
            dia = estadoGuardado.get<Int>(CLAVE_DIA) ?: 0,
            tema = estadoGuardado.get<String>(CLAVE_TEMA) ?: "Todos",
        ),
    )

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
            resultadoDescarga.value = ResultadoDescarga.SinFalla
            try {
                val seActualizo = repo.sincronizar()
                if (!seActualizo) {
                    informarFalla()
                }
            } finally {
                cargando.value = false
            }
        }
    }

    // Si ya hubo una descarga exitosa se conservan los datos y solo se avisa.
    // Si nunca hubo descarga, se muestra el error con el boton Reintentar.
    private suspend fun informarFalla() {
        if (repo.hayEdicionGuardada.first()) {
            resultadoDescarga.value = ResultadoDescarga.FallaConDatosGuardados
        } else {
            resultadoDescarga.value = ResultadoDescarga.FallaSinDatos
        }
    }

    // dia 0 significa "Ambos dias"; el tema "Todos" no filtra por tema.
    private fun coincideConFiltros(charla: Charla, filtrosElegidos: Filtros): Boolean {
        val coincideDia = filtrosElegidos.dia == 0 || charla.dia == filtrosElegidos.dia
        val coincideTema = filtrosElegidos.tema == "Todos" || charla.tema == filtrosElegidos.tema
        return coincideDia && coincideTema
    }

    fun elegirDia(nuevoDia: Int) { estadoGuardado[CLAVE_DIA] = nuevoDia }

    fun elegirTema(nuevoTema: String) { estadoGuardado[CLAVE_TEMA] = nuevoTema }

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }
}
