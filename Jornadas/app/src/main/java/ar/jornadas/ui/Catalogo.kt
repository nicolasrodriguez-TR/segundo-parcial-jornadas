package ar.jornadas.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.jornadas.data.Charla

@Composable
fun Catalogo(model: ProgramaModel, abrir: (String) -> Unit) {
    // Se quito la recarga automatica de aca: se repetia al cambiar de pestana o volver de una ficha.
    // Todo el estado de la pantalla llega en un solo objeto desde el ViewModel.
    // Los filtros y la lista ya filtrada vienen de ahi (antes el tema estaba en remember y se perdia).
    val estado by model.uiState.collectAsStateWithLifecycle()
    Selector(listOf("Ambos días", "Día 1", "Día 2"), estado.dia) { model.elegirDia(it) }
    val temas = listOf("Todos", "Android", "Datos", "Calidad")
    Selector(temas, temas.indexOf(estado.tema)) { model.elegirTema(temas[it]) }
    TextButton(onClick = model::recargar) { Text("Actualizar") }
    if (estado.cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (estado.resultadoDescarga == ResultadoDescarga.FallaSinDatos) {
        // Nunca hubo descarga: no hay nada para mostrar, se ofrece reintentar.
        Text("No se pudo consultar el programa")
        OutlinedButton(onClick = model::recargar) { Text("Reintentar") }
    } else {
        // Fallo la red pero hay datos guardados: se muestran igual y se avisa.
        if (estado.resultadoDescarga == ResultadoDescarga.FallaConDatosGuardados) {
            Text("No se pudo actualizar: se están usando datos guardados.")
        }
        // Edicion valida sin charlas (v4).
        if (estado.sinCharlasProgramadas) Text("Sin charlas programadas")
        Grilla(estado.charlas, abrir)
    }
}

@Composable
private fun Selector(opciones: List<String>, indice: Int, elegir: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEachIndexed { i, texto ->
            FilterChip(selected = i == indice, onClick = { elegir(i) }, label = { Text(texto) })
        }
    }
}

@Composable
fun Grilla(fichas: List<Charla>, abrir: (String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)) {
        items(fichas, key = { it.id }) { ficha ->
            ElevatedCard(onClick = { abrir(ficha.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Día ${ficha.dia} · ${ficha.hora} · ${ficha.sala}", style = MaterialTheme.typography.labelMedium)
                    Text(ficha.titulo, style = MaterialTheme.typography.titleLarge)
                    Text(if (ficha.favorita) "${ficha.tema} · En mi agenda" else ficha.tema)
                }
            }
        }
    }
}
