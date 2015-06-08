package fjmorsan.upo.es.ardufitv13;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;


public class BluetoothService {

    public static final int ESTADO_NINGUNO = 0;
    public static final int ESTADO_CONECTADO = 1;
    public static final int ESTADO_REALIZANDO_CONEXION = 2;
    public static final int MSG_CAMBIO_ESTADO = 10;
    public static final int MSG_LEER = 11;
    public static final int MSG_ESCRIBIR = 12;
    public static final int MSG_SINCRO_FIN = 13;
    private static final String TAG = "BluetoothService";
    //Identificador unico de nuestro dispositivo HC-06
    private static final String UUID_SERIAL_PORT_PROFILE = "00001101-0000-1000-8000-00805F9B34FB";
    private final Context context;
    private final BluetoothAdapter bAdapter;
    private Handler handler = null;


    private int estado;
    private HiloConexion hiloConexion = null;
    private HiloCliente hiloCliente = null;

    public BluetoothService(Context context, Handler handler, BluetoothAdapter adapter) {


        this.context = context;
        this.handler = handler;
        this.bAdapter = adapter;
        this.estado = ESTADO_NINGUNO;

    }

    // Instancia un hilo conector
    public synchronized void solicitarConexion(BluetoothDevice dispositivo) {
        // Comprobamos si existia un intento de conexion en curso.
        // Si es el caso, se cancela y se vuelve a iniciar el proceso
        if (estado == ESTADO_REALIZANDO_CONEXION) {
            if (hiloCliente != null) {
                hiloCliente.cancelarConexion();
                hiloCliente = null;
            }
        }

        // Si existia una conexion abierta, se cierra y se inicia una nueva
        if (hiloConexion != null) {
            hiloConexion.cancelarConexion();
            hiloConexion = null;
        }

        // Se instancia un nuevo hilo conector, encargado de solicitar una conexion
        // al servidor, que sera la otra parte.
        hiloCliente = new HiloCliente(dispositivo);
        hiloCliente.start();

        setEstado(ESTADO_REALIZANDO_CONEXION);
    }


    // Sincroniza el objeto con el hilo HiloConexion e invoca a su metodo escribir()
    // para enviar el mensaje a traves del flujo de salida del socket.
    public int enviar(byte[] buffer) {
        HiloConexion tmpConexion;

        synchronized (this) {
            if (estado != ESTADO_CONECTADO)
                return -1;
            tmpConexion = hiloConexion;
        }

        tmpConexion.escribir(buffer);

        return buffer.length;

    }

    public void finalizarServicio() {

        if (hiloCliente != null) {
            hiloCliente.cancelarConexion();
        }
        if (hiloConexion != null) {
            hiloConexion.cancelarConexion();
        }
        hiloCliente = null;
        hiloConexion = null;
        setEstado(ESTADO_NINGUNO);

    }

    public synchronized int getEstado() {
        return estado;
    }

    private synchronized void setEstado(int estado) {
        this.estado = estado;
        handler.obtainMessage(MSG_CAMBIO_ESTADO, estado, -1).sendToTarget();
    }

    public String getNombreDispositivo() {
        String nombre = "";
        if (estado == ESTADO_CONECTADO) {
            if (hiloConexion != null)
                nombre = hiloConexion.getName();
        }

        return nombre;
    }

    // Hilo encargado de mantener la conexion y realizar las lecturas y escrituras
    // de los mensajes entre flora y el hc-06
    private class HiloConexion extends Thread {

        private final BluetoothSocket socket; //Socket
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public HiloConexion(BluetoothSocket socket) {
            this.socket = socket;
            setName(socket.getRemoteDevice().getName() + " [" + socket.getRemoteDevice().getAddress() + "]");
            InputStream tmpInputStream = null;
            OutputStream tmpOutputStream = null;

            // Obtenemos los flujos de entrada y salida del socket.
            try {
                tmpInputStream = socket.getInputStream();
                tmpOutputStream = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "HiloConexion(): Error al obtener flujos de E/S", e);

            }

            inputStream = tmpInputStream;
            outputStream = tmpOutputStream;
        }

        // Metodo principal del hilo, encargado de realizar las lecturas
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes = 0;
            setEstado(ESTADO_CONECTADO);
            // Mientras se mantenga la conexion el hilo se mantiene en espera ocupada
            // leyendo del flujo de entrada
            int loop_exit = 1;
            while (loop_exit == 1) {
                try {

                    // Leemos del flujo de entrada del socket

                    buffer[bytes] = (byte) inputStream.read();
                    if ((buffer[bytes] == '\r')) {
                        // Enviamos la informacion a la actividad a traves del handler.
                        // El metodo handleMessage sera el encargado de recibir el mensaje
                        // y mostrar los datos recibidos en el TextView
                        handler.obtainMessage(MSG_LEER, bytes, -1, buffer).sendToTarget();
                        String mensaje = new String(buffer, 0, bytes);
                        Log.d("BTR",mensaje);
                        if(mensaje.contains("$PMTK001,622")){
                            handler.obtainMessage(MSG_SINCRO_FIN).sendToTarget();
                        }
                        bytes = 0;
                    } else {
                        bytes++;
                    }
                    /*
                    bytes = inputStream.read(buffer);
                    handler.obtainMessage(MSG_LEER, bytes, -1, buffer).sendToTarget();
                    */

                } catch (IOException e) {
                    Log.e(TAG, "HiloConexion.run(): Error al realizar la lectura", e);
                    finalizarServicio();
                    loop_exit = 0;
                }
            }
        }


        public void escribir(byte[] buffer) {
            try {
                // Escribimos en el flujo de salida del socket
                outputStream.write(buffer);

                // Enviamos la informacion a la actividad a traves del handler.
                // El metodo handleMessage sera el encargado de recibir el mensaje
                // y mostrar los datos enviados en el Toast
                handler.obtainMessage(MSG_ESCRIBIR, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "HiloConexion.escribir(): Error al realizar la escritura", e);
            }
        }

        public void cancelarConexion() {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "HiloConexion.cancelarConexion(): Error al cerrar el socket", e);
            }
            setEstado(ESTADO_NINGUNO);
        }

    }

    private class HiloCliente extends Thread {

        private final BluetoothDevice dispositivo;
        private final BluetoothSocket socket;

        public HiloCliente(BluetoothDevice dispositivo) {
            this.dispositivo = dispositivo;
            BluetoothSocket tmpSocket = null;
            try {
                tmpSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(getSerialPortUUID());
            } catch (IOException e) {
                Log.e(TAG, "HiloCliente.HiloCliente(): Error al abrir el socket cliente", e);
            }
            socket = tmpSocket;
        }

        public void run() {
            setName("HiloCliente");
            if (bAdapter.isDiscovering()) {
                bAdapter.cancelDiscovery();
            }
            try {
                socket.connect();
                setEstado(ESTADO_REALIZANDO_CONEXION);

            } catch (IOException e) {
                Log.e(TAG, "HiloCliente.run(): Error realizando conexion", e);
                try {
                    socket.close();
                } catch (IOException inner) {
                    Log.e(TAG, "HiloCliente.run(): Error cerrando el socket", e);
                }
                setEstado(ESTADO_NINGUNO);
            }
            // Una vez el socket se ha abierto
            // Reiniciamos el hilo cliente, ya que no lo necesitaremos mas
            synchronized (BluetoothService.this) {
                hiloCliente = null;
            }
            //Realizamos la conexion
            hiloConexion = new HiloConexion(socket);
            hiloConexion.start();

        }

        public void cancelarConexion() {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "HiloCliente.cancelarConexion(): Error al cerrar el socket", e);
            }
            setEstado(ESTADO_NINGUNO);
        }


        private UUID getSerialPortUUID() {
            return UUID.fromString(UUID_SERIAL_PORT_PROFILE);
        }
    }

}



