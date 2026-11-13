package ar.jornadas

import android.app.Application

// Clase Application: Android crea una sola instancia por proceso.
// Aca vive el unico Servicios (base de datos, Retrofit y contador de peticiones),
// asi no se vuelven a crear cuando se recrea el ViewModel.
class JornadasApp : Application() {
    lateinit var servicios: Servicios
        private set

    // Indica si en este proceso ya se hizo la carga automatica.
    // Vive en la Application (y no en el ViewModel) porque debe durar lo mismo que el proceso.
    var cargaAutomaticaRealizada = false

    override fun onCreate() {
        super.onCreate()
        servicios = Servicios(this)
    }
}
