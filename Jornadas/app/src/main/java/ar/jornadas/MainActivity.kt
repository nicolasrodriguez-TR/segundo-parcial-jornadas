package ar.jornadas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import ar.jornadas.ui.*

class MainActivity : ComponentActivity() {
    // La fabrica le pasa al ViewModel el repositorio unico y el SavedStateHandle.
    private val model: ProgramaModel by viewModels { ProgramaModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mesa es la herramienta de prueba del servicio local: no es parte de la app, por eso no pasa por el ViewModel.
        val mesa = (application as JornadasApp).servicios.mesa
        if (savedInstanceState == null && intent.hasExtra("servicio_disponible")) {
            mesa.disponible = intent.getBooleanExtra("servicio_disponible", true)
            intent.removeExtra("servicio_disponible")
        }
        setContent { Portada(model, mesa) }
    }

    override fun onStart() {
        super.onStart()
        // Se mantiene en onStart (despues de onCreate, que puede apagar el servicio),
        // pero ahora el ViewModel solo carga la primera vez del proceso.
        model.cargarAlIniciar()
    }
}
