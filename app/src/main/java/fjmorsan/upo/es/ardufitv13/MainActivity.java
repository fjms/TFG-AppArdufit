package fjmorsan.upo.es.ardufitv13;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;


public class MainActivity extends Activity implements OnClickListener {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final String NOMBRE_DISPOSITIVO_BT = "ARDUFIT";
    private final Handler handler = new Handler(new IncomingHandlerCallback());
    private Button btnBluetooth;
    private Button btnEnviar;
    private BluetoothAdapter bAdapter;
    private Button btnBuscarDispositivo;
    private ProgressBar spinner;
    private ArrayList<BluetoothDevice> arrayDevices;
    private ListView lvDispositivos;
    // Instanciamos un BroadcastReceiver que se encargara de detectar si el estado
    // del Bluetooth del dispositivo ha cambiado mediante su handler onReceive

    private TextView tvMensaje;
    private TextView tvConexion;
    private BluetoothService servicio;                // Servicio de mensajes de Bluetooth
    private BluetoothDevice ultimoDispositivo;        // Ultimo dispositivo conectado

    private String data = "";


    private final BroadcastReceiver bReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final int estado = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR);
            // Filtramos por la accion. Nos interesa detectar BluetoothAdapter.ACTION_STATE_CHANGED
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                switch (estado) {
                    // Apagado
                    case BluetoothAdapter.STATE_OFF: {
                        servicio.finalizarServicio();
                        ((Button) findViewById(R.id.btnBluetooth)).setText(R.string.ActivarBluetooth);
                        break;
                    }

                    // Encendido
                    case BluetoothAdapter.STATE_ON: {
                        ((Button) findViewById(R.id.btnBluetooth)).setText(R.string.DesactivarBluetooth);
                        break;
                    }
                    default:
                        break;
                }
            }
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Acciones a realizar al descubrir un nuevo dispositivo
                if (arrayDevices == null) {
                    arrayDevices = new ArrayList<>();
                }
                // Extraemos el dispositivo del intent mediante la clave
                // BluetoothDevice.EXTRA_DEVICE
                BluetoothDevice dispositivo = intent.getParcelableExtra(BluetoothDevice.
                                                                                    EXTRA_DEVICE);

                // Añadimos el dispositivo al array
                arrayDevices.add(dispositivo);

                // Le asignamos un nombre del estilo NombreDispositivo [00:11:22:33:44]
                String descripcionDispositivo = dispositivo.getName() +
                        " [" + dispositivo.getAddress() + "]";

                // Mostramos que hemos encontrado el dispositivo por el Toast
                Toast.makeText(getBaseContext(), getString(R.string.DetectadoDispositivo) + ": "
                        + descripcionDispositivo, Toast.LENGTH_SHORT).show();

                // Si es nuestro dispositivo detenemos las busqueda
                if (dispositivo.getName().equals(NOMBRE_DISPOSITIVO_BT)) {
                    bAdapter.cancelDiscovery();
                }
            }

            // Codigo que se ejecutara cuando el Bluetooth finalice la busqueda de dispositivos.
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                // Acciones a realizar al finalizar el proceso de descubrimiento
                // Instanciamos un nuevo adapter para el ListView mediante la clase que acabamos de
                // crear
                spinner.setVisibility(View.GONE);
                if(arrayDevices.size()>0) {
                    lvDispositivos.setVisibility(View.VISIBLE);
                    configurarOnItemClickListener();

                    ArrayAdapter arrayAdapter = new BluetoothDeviceArrayAdapter(getBaseContext(),
                            android.R.layout.simple_list_item_2, arrayDevices);
                    lvDispositivos.setAdapter(arrayAdapter);
                }
                Toast.makeText(getBaseContext(), "Fin de la búsqueda", Toast.LENGTH_SHORT).show();
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnBluetooth = (Button) findViewById(R.id.btnBluetooth);
        btnBuscarDispositivo = (Button) findViewById(R.id.btnBuscarDispositivo);
        btnEnviar = (Button) findViewById(R.id.btnEnviar);
        lvDispositivos = (ListView) findViewById(R.id.lvDispositivos);
        tvMensaje = (TextView) findViewById(R.id.tvMensaje);
        tvMensaje.setMovementMethod(new ScrollingMovementMethod());
        tvConexion = (TextView) findViewById(R.id.tvConexion);
        spinner = (ProgressBar) findViewById(R.id.progressBar1);
        spinner.setVisibility(View.GONE);
        btnEnviar.setVisibility(View.GONE);

        configurarOnclickListener();
        configurarOnItemClickListener();
        configurarAdaptadorBluetooth();
        registrarEventosBluetooth();


    }

    private void configurarOnclickListener() {
        btnBluetooth.setOnClickListener(this);
        btnBuscarDispositivo.setOnClickListener(this);
        btnEnviar.setOnClickListener(this);
    }

    private void configurarOnItemClickListener() {
        lvDispositivos.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                //String item = ((TextView)view).getText().toString();
                String item = ((TextView) view.findViewById(android.R.id.text2)).getText().toString();
                conectarDispositivo(item);


            }
        });
    }

    private void configurarAdaptadorBluetooth() {
        // Obtenemos el adaptador Bluetooth. Si es NULL, significara que el
        // dispositivo no posee Bluetooth, por lo que deshabilitamos el boton
        // encargado de activar/desactivar esta caracteristica.
        bAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bAdapter == null) {
            btnBluetooth.setEnabled(false);
            return;
        }
        // Comprobamos si el Bluetooth esta activo y cambiamos el texto del
        // boton dependiendo del estado, si esta activo activamos el servicio sino lo estaba antes.
        if (bAdapter.isEnabled()) {
            btnBluetooth.setText(R.string.DesactivarBluetooth);
            if (servicio != null) {
                servicio.finalizarServicio();
            } else {
                servicio = new BluetoothService(this, handler, bAdapter);
            }
        } else {
            btnBluetooth.setText(R.string.ActivarBluetooth);
        }
    }

    public void conectarDispositivo(String direccion) {
        Toast.makeText(this, "Conectando a " + direccion, Toast.LENGTH_LONG).show();
        if (servicio != null) {
            BluetoothDevice arduino = bAdapter.getRemoteDevice(direccion);
            servicio.solicitarConexion(arduino);
            this.ultimoDispositivo = arduino;
        }
    }

    private void registrarEventosBluetooth() {
        // Registramos el BroadcastReceiver que instanciamos previamente para
        // detectar los distintos eventos que queremos recibir
        IntentFilter filtro = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        filtro.addAction(BluetoothDevice.ACTION_FOUND);
        filtro.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(bReceiver, filtro);
    }

    public void enviarMensaje(String mensaje) {
        if (servicio.getEstado() != BluetoothService.ESTADO_CONECTADO) {
            Toast.makeText(this, R.string.MsgErrorConexion, Toast.LENGTH_SHORT).show();
            return;
        }

        if (mensaje.length() > 0) {
            byte[] buffer = mensaje.getBytes();
            servicio.enviar(buffer);
        }
    }

    /**
     * Handler del evento desencadenado al retornar de una actividad. En este caso, se utiliza
     * para comprobar el valor de retorno al lanzar la actividad que activara el Bluetooth.
     * En caso de que el usuario acepte, resultCode sera RESULT_OK
     * En caso de que el usuario no acepte, resultCode valdra RESULT_CANCELED
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT: {
                if (resultCode == RESULT_OK) {
                    // Acciones adicionales a realizar si el usuario activa el Bluetooth
                    btnBluetooth.setText(R.string.DesactivarBluetooth);
                    if (servicio != null) {
                        servicio.finalizarServicio();
                    } else {
                        servicio = new BluetoothService(this, handler, bAdapter);
                    }
                }
                break;
            }
            default:
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        //Es mucho mas comodo si se tienen muchos botones realizar un solo listener y filtar
        //el indentificador del boton para la accion a onClick
        switch (v.getId()) {
            // Codigo ejecutado al pulsar el Button que se va a encargar de activar y
            // desactivar el Bluetooth.
            case R.id.btnBluetooth: {
                // 1. Comprobar si el Bluetooth esta activado o desactivado
                // 2. Codificar la activacion/desactivacion del Bluetooth
                if (bAdapter.isEnabled()) {
                    if (servicio != null) {
                        servicio.finalizarServicio();
                    }
                    bAdapter.disable();
                } else {
                    // Lanzamos el Intent que mostrara la interfaz de activacion del
                    // Bluetooth. La respuesta de este Intent se manejara en el metodo
                    // onActivityResult
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
                break;
            }
            case R.id.btnBuscarDispositivo: {
                if (arrayDevices != null) {
                    arrayDevices.clear();
                }
                if (bAdapter.isEnabled()) {
                    // Comprobamos si existe un descubrimiento en curso. En caso afirmativo, se
                    // cancela.
                    if (bAdapter.isDiscovering()) {
                        bAdapter.cancelDiscovery();
                    }
                    // Iniciamos la busqueda de dispositivos
                    if (bAdapter.startDiscovery()) {
                        lvDispositivos.setVisibility(View.GONE);
                        spinner.setVisibility(View.VISIBLE);
                        // Mostramos el mensaje de que el proceso ha comenzado
                        Toast.makeText(this, R.string.IniciandoDescubrimiento, Toast.LENGTH_SHORT).show();

                    } else {
                        Toast.makeText(this, R.string.ErrorIniciandoDescubrimiento, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, R.string.MsgAvisoBTOFF, Toast.LENGTH_SHORT).show();
                }
                break;
            }
            case R.id.btnEnviar:
                enviarMensaje("e");

            default:
                break;
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.unregisterReceiver(bReceiver);
        if (servicio != null) {
            servicio.finalizarServicio();
        }
    }
    protected void saveToFile(String data){

        FileOutputStream outputStream;
        try{
            outputStream = openFileOutput("gps.tmp",MODE_PRIVATE);
            outputStream.write(data.getBytes());
            outputStream.close();
        }catch (Exception e){
            Log.w("ExternalStorage", "Error writing internal file ", e);
        }
    }
    protected void createExernalStorePrivateFile(String data){
        File file = new File(getExternalFilesDir(null), "gps.txt");
        try {
            OutputStream os = new FileOutputStream(file);
            os.write(data.getBytes());
            os.close();

        } catch (IOException e) {
            // Unable to create file, likely because external storage is
            // not currently mounted.
            Log.w("ExternalStorage", "Error writing " + file, e);
        }

    }

    class IncomingHandlerCallback implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {
            byte[] buffer;
            String mensaje;
            // Atendemos al tipo de mensaje
            switch (msg.what) {
                // Mensaje de lectura: se mostrara en un TextView
                case BluetoothService.MSG_LEER: {
                    buffer = (byte[]) msg.obj;
                    mensaje = new String(buffer, 0, msg.arg1);
                    data = data + mensaje;
                    tvMensaje.setText("Sincronizando...");
                    break;
                }

                // Mensaje de escritura: se mostrara en el Toast
                case BluetoothService.MSG_ESCRIBIR: {
                    buffer = (byte[]) msg.obj;
                    mensaje = new String(buffer);
                    mensaje = "Enviando mensaje: " + mensaje;
                    Toast.makeText(getApplicationContext(), mensaje, Toast.LENGTH_SHORT).show();
                    break;
                }
                case BluetoothService.MSG_SINCRO_FIN:{
                    tvMensaje.setText(data);
                    Toast.makeText(getApplicationContext(), R.string.SincroFin, Toast.LENGTH_SHORT).show();
                    /*
                    Aqui tenemos que tratar data, generamos el fichero
                    Lo parseamos con Locus para obtener los puntos gps
                     */
                    //saveToFile(data);
                    createExernalStorePrivateFile(data);

                    data ="";
                    break;
                }

                // Mensaje de cambio de estado
                case BluetoothService.MSG_CAMBIO_ESTADO: {
                    switch (msg.arg1) {
                        // CONECTADO: Se muestra el dispositivo al que se ha conectado y se activa
                        // el boton de enviar, se pone visible y hacemos invisible el boton buscar.
                        case BluetoothService.ESTADO_CONECTADO: {
                            mensaje = getString(R.string.ConexionActual) + " " + servicio.getNombreDispositivo();
                            Toast.makeText(getApplicationContext(), mensaje, Toast.LENGTH_SHORT).show();
                            tvConexion.setText(mensaje);
                            btnEnviar.setEnabled(true);
                            btnBuscarDispositivo.setVisibility(View.GONE);
                            btnEnviar.setVisibility(View.VISIBLE);
                            lvDispositivos.setVisibility(View.GONE);
                            break;
                        }

                        // REALIZANDO CONEXION: Se muestra el dispositivo al que se esta conectando
                        case BluetoothService.ESTADO_REALIZANDO_CONEXION: {
                            mensaje = getString(R.string.ConectandoA) + " " + ultimoDispositivo.getName() + " [" + ultimoDispositivo.getAddress() + "]";
                            Toast.makeText(getApplicationContext(), mensaje, Toast.LENGTH_SHORT).show();
                            btnEnviar.setEnabled(false);
                            break;
                        }

                        // NINGUNO: Mensaje por defecto. Desactivacion del boton de enviar
                        case BluetoothService.ESTADO_NINGUNO: {
                            mensaje = getString(R.string.SinConexion);
                            //Toast.makeText(getApplicationContext(), mensaje, Toast.LENGTH_SHORT).show();
                            tvConexion.setText(mensaje);
                            btnEnviar.setEnabled(false);
                            btnEnviar.setVisibility(View.GONE);
                            btnBuscarDispositivo.setVisibility(View.VISIBLE);
                            lvDispositivos.setVisibility(View.VISIBLE);
                            servicio.finalizarServicio();
                            break;
                        }
                        default:
                            break;
                    }
                    break;
                }

                default:
                    break;
            }
            return true;

        }

    }
}
