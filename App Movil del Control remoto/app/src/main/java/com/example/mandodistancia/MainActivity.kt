package com.example.mandodistancia

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {

    private lateinit var btnConectar: Button
    private lateinit var btnAdelante: Button
    private lateinit var btnAtras: Button
    private lateinit var btnDetener: Button
    private lateinit var txtEstado: TextView

    private val DEVICE_NAME = "Barco_RC_ESP32"
    private val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val CHARACTERISTIC_UUID_RX: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var characteristicRx: BluetoothGattCharacteristic? = null

    private val REQUEST_PERMISSION_CODE = 100
    private val handler = Handler(Looper.getMainLooper())

    private val colaComandos: ArrayDeque<String> = ArrayDeque()
    private var enviando = false
    private val INTERVALO_ENVIO_MS = 25L

    private val INTERVALO_KEEPALIVE_MS = 200L
    private val keepAliveRunnables = mutableMapOf<Button, Runnable>()

    private var escaneando = false
    private val TIMEOUT_ESCANEO_MS = 10_000L
    private val timeoutEscaneoRunnable = Runnable {
        if (escaneando) {
            detenerEscaneo()
            txtEstado.text = "Estado: Dispositivo no encontrado"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnConectar = findViewById(R.id.btnConectar)
        btnAdelante = findViewById(R.id.btnAdelante)
        btnAtras = findViewById(R.id.btnAtras)
        btnDetener = findViewById(R.id.btnDetener)
        txtEstado = findViewById(R.id.txtEstado)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        verificarPermisos()

        btnConectar.setOnClickListener {
            if (bluetoothGatt == null && !escaneando) {
                conectarDispositivo()
            } else {
                desconectarDispositivo()
            }
        }

        // Control del motor con keep-alive: al mantener presionado envía 'F' o 'B'; al soltar envía 'S'
        configurarBotonMantenido(btnAdelante, "F", "S")
        configurarBotonMantenido(btnAtras, "B", "S")

        btnDetener.setOnClickListener {
            enviarComando("S")
            Toast.makeText(this, "Motor detenido", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun configurarBotonMantenido(boton: Button, comandoPresionar: String, comandoSoltar: String) {
        boton.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    enviarComando(comandoPresionar)
                    iniciarKeepAlive(boton, comandoPresionar)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    detenerKeepAlive(boton)
                    enviarComando(comandoSoltar)
                    true
                }
                else -> true
            }
        }
    }

    private fun iniciarKeepAlive(boton: Button, comando: String) {
        detenerKeepAlive(boton)
        val r = object : Runnable {
            override fun run() {
                enviarComando(comando)
                handler.postDelayed(this, INTERVALO_KEEPALIVE_MS)
            }
        }
        keepAliveRunnables[boton] = r
        handler.postDelayed(r, INTERVALO_KEEPALIVE_MS)
    }

    private fun detenerKeepAlive(boton: Button) {
        keepAliveRunnables.remove(boton)?.let { handler.removeCallbacks(it) }
    }

    private fun detenerTodosLosKeepAlive() {
        keepAliveRunnables.values.forEach { handler.removeCallbacks(it) }
        keepAliveRunnables.clear()
    }

    private fun verificarPermisos() {
        val permisos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (permisos.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permisos, REQUEST_PERMISSION_CODE)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val dispositivo = result?.device ?: return
            val nombre = result.scanRecord?.deviceName ?: dispositivo.name
            if (nombre != DEVICE_NAME) return

            detenerEscaneo()
            runOnUiThread { txtEstado.text = "Estado: Conectando..." }
            bluetoothGatt = dispositivo.connectGatt(applicationContext, false, gattCallback)
        }

        override fun onScanFailed(errorCode: Int) {
            escaneando = false
            runOnUiThread { txtEstado.text = "Estado: Error de escaneo ($errorCode)" }
        }
    }

    private fun conectarDispositivo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Faltan permisos de escaneo", Toast.LENGTH_SHORT).show()
            verificarPermisos()
            return
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Toast.makeText(this, "Activa el Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        characteristicRx = null
        txtEstado.text = "Estado: Escaneando..."
        btnConectar.text = "Cancelar"

        escaneando = true
        scanner.startScan(scanCallback)
        handler.postDelayed(timeoutEscaneoRunnable, TIMEOUT_ESCANEO_MS)
    }

    private fun detenerEscaneo() {
        handler.removeCallbacks(timeoutEscaneoRunnable)
        if (escaneando) {
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            escaneando = false
        }
        if (bluetoothGatt == null) btnConectar.text = "Conectar Bluetooth"
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                runOnUiThread {
                    txtEstado.text = "Estado: Sincronizando..."
                    btnConectar.text = "Desconectar"
                }
                gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                handler.postDelayed({ gatt?.discoverServices() }, 300)

            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                runOnUiThread {
                    detenerTodosLosKeepAlive()
                    colaComandos.clear()
                    enviando = false
                    txtEstado.text = "Estado: Desconectado"
                    txtEstado.setTextColor(ContextCompat.getColor(applicationContext, android.R.color.holo_red_dark))
                    btnConectar.text = "Conectar Bluetooth"
                    Toast.makeText(applicationContext, "Desconectado del Barco", Toast.LENGTH_SHORT).show()
                }
                try {
                    gatt?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                bluetoothGatt = null
                characteristicRx = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                characteristicRx = gatt?.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID_RX)

                runOnUiThread {
                    if (characteristicRx != null) {
                        txtEstado.text = "Estado: ¡Listo para Controlar!"
                        txtEstado.setTextColor(ContextCompat.getColor(applicationContext, android.R.color.holo_green_dark))
                        Toast.makeText(applicationContext, "¡Canales BLE vinculados con éxito!", Toast.LENGTH_SHORT).show()
                    } else {
                        txtEstado.text = "Estado: Error de Característica"
                        Toast.makeText(applicationContext, "Error: Característica RX no hallada en el ESP32", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Fallo al descubrir servicios: $status", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun enviarComando(comando: String) {
        if (bluetoothGatt == null || characteristicRx == null) return
        colaComandos.add(comando)
        procesarCola()
    }

    private fun procesarCola() {
        if (enviando) return
        val gatt = bluetoothGatt ?: return
        val ch = characteristicRx ?: return
        val comando = colaComandos.poll() ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        enviando = true
        val data = comando.toByteArray(Charsets.UTF_8)

        @Suppress("DEPRECATION")
        val ok: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                ch, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            ch.value = data
            gatt.writeCharacteristic(ch)
        }

        if (!ok) {
            colaComandos.addFirst(comando)
        }

        handler.postDelayed({
            enviando = false
            procesarCola()
        }, INTERVALO_ENVIO_MS)
    }

    private fun desconectarDispositivo() {
        detenerTodosLosKeepAlive()
        detenerEscaneo()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        if (bluetoothGatt != null && characteristicRx != null) {
            colaComandos.clear()
            enviarComando("S")
        }

        handler.postDelayed({
            bluetoothGatt?.disconnect()
            try {
                bluetoothGatt?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            bluetoothGatt = null
            characteristicRx = null
            colaComandos.clear()
            enviando = false
        }, 120)

        txtEstado.text = "Estado: Desconectado"
        txtEstado.setTextColor(ContextCompat.getColor(applicationContext, android.R.color.holo_red_dark))
        btnConectar.text = "Conectar Bluetooth"
    }

    override fun onPause() {
        super.onPause()
        detenerTodosLosKeepAlive()
        colaComandos.clear()
        enviarComando("S")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        detenerEscaneo()
        try {
            bluetoothGatt?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        bluetoothGatt = null
    }
}