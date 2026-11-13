package ar.jornadas

import android.app.Application

// Clase Application: Android crea una sola instancia por proceso.
// Aca vive el unico Servicios (base de datos, Retrofit y contador de peticiones),
// asi no se vuelven a crear cuando se recrea el ViewModel.
class JornadasApp : Application() {
    lateinit var servicios: Servicios
        private set

    override fun onCreate() {
        super.onCreate()
        servicios = Servicios(this)
    }
}
