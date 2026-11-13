package ar.jornadas.ui

import ar.jornadas.data.Charla

// Todo lo que la pantalla "Programa" necesita para dibujarse, en un solo objeto.
data class ProgramaUiState(
    val dia: Int = 0,
    val tema: String = "Todos",
    // Ya filtradas por dia y tema.
    val charlas: List<Charla> = emptyList(),
    val cargando: Boolean = false,
    val resultadoDescarga: ResultadoDescarga = ResultadoDescarga.SinFalla,
    // True cuando la edicion descargada es valida pero no trae charlas (caso v4).
    val sinCharlasProgramadas: Boolean = false,
)

// Como termino la ultima descarga. Es un solo valor: no pueden convivir dos avisos contradictorios.
sealed interface ResultadoDescarga {
    // No hubo falla (o todavia no se pidio nada).
    data object SinFalla : ResultadoDescarga

    // Fallo, pero hay una edicion guardada: la lista sigue visible y se muestra un aviso.
    data object FallaConDatosGuardados : ResultadoDescarga

    // Fallo y nunca hubo descarga: se muestra el error y el boton Reintentar.
    data object FallaSinDatos : ResultadoDescarga
}
