package fjmorsan.upo.es.ardufitv13;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
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

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends FragmentActivity implements OnClickListener, OnMapReadyCallback {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final String NOMBRE_DISPOSITIVO_BT = "ARDUTEST";
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
                if (arrayDevices.size() > 0) {
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

    private MapFragment mapFragment;        // Mapa google
    private Button btnSincro;
    private String data = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnBluetooth = (Button) findViewById(R.id.btnBluetooth);
        btnBuscarDispositivo = (Button) findViewById(R.id.btnBuscarDispositivo);
        btnEnviar = (Button) findViewById(R.id.btnEnviar);
        lvDispositivos = (ListView) findViewById(R.id.lvDispositivos);
        tvMensaje = (TextView) findViewById(R.id.tvMensaje);
        //tvMensaje.setMovementMethod(new ScrollingMovementMethod());
        tvConexion = (TextView) findViewById(R.id.tvConexion);
        spinner = (ProgressBar) findViewById(R.id.progressBar1);
        spinner.setVisibility(View.GONE);
        btnEnviar.setVisibility(View.GONE);
        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        btnSincro = (Button) findViewById(R.id.btnSincro);
        btnSincro.setVisibility(View.GONE);
        mostrarMapa(false);
        configurarOnclickListener();
        configurarOnItemClickListener();
        configurarAdaptadorBluetooth();
        registrarEventosBluetooth();


    }

    private void configurarOnclickListener() {
        btnBluetooth.setOnClickListener(this);
        btnBuscarDispositivo.setOnClickListener(this);
        btnEnviar.setOnClickListener(this);
        btnSincro.setOnClickListener(this);
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
            case R.id.btnEnviar: {
                enviarMensaje("e");
            }
            break;


            case R.id.btnSincro: {
                enviarMensaje("s");
/*
                List<Coordenada> lista = fileToCoordenadasList();
                if (lista == null) {
                    Toast.makeText(this, "Es nula", Toast.LENGTH_SHORT).show();
                } else if (lista.isEmpty()) {
                    Toast.makeText(this, "Esta vacia", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Leido", Toast.LENGTH_SHORT).show();
                    dibujaRuta(mapFragment.getMap(), lista);

                }
                lvDispositivos.setVisibility(View.GONE);
                mostrarMapa(true);
*/
                break;
            }

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

    protected void saveToFile(String data) {

        FileOutputStream outputStream;
        try {
            outputStream = openFileOutput("gps.txt", MODE_PRIVATE);
            outputStream.write(data.getBytes());
            outputStream.close();
        } catch (Exception e) {
            Log.w("ExternalStorage", "Error writing internal file ", e);
        }
    }

    protected boolean createExernalStorePrivateFile(String data) {
        File file = new File(getExternalFilesDir(null), "gps.txt");
        boolean eval;
        try {
            OutputStream os = new FileOutputStream(file);
            os.write(data.getBytes());
            os.close();
            eval = true;

        } catch (IOException e) {
            // Unable to create file, likely because external storage is
            // not currently mounted.
            Log.w("ExternalStorage", "Error writing " + file, e);
            eval = false;
        }
        return eval;

    }

    protected List<Coordenada> fileToCoordenadasList() {
        List<Coordenada> coor = new ArrayList<>();
        for (Coordenada coordenada : Locus.parseFile(getExternalFilesDir(null), "gps.txt")) {
            if (coordenada.getFix() < 5) {
                coor.add(coordenada);
            }
        }
        return coor;
    }

    private void mostrarMapa(boolean estado) {
        if (estado) {
            mapFragment.getView().setVisibility(View.VISIBLE);
            mapFragment.getMapAsync(this);
        } else {
            mapFragment.getView().setVisibility(View.GONE);
        }
    }

    private void dibujaRuta(GoogleMap mapa, List<Coordenada> coordenadaList) {

        mapa.clear();
        PolylineOptions polylineOptions = new PolylineOptions();
        polylineOptions.geodesic(true);
        int num_coordenadas = coordenadaList.size();
        for (Coordenada c : coordenadaList) {
            polylineOptions.add(new LatLng(c.getLatitud(), c.getLongitud()));
        }
        Coordenada cInicio = coordenadaList.get(0);
        Coordenada cFin = coordenadaList.get(num_coordenadas - 1);
        polylineOptions.color(Color.GREEN);
        mapa.addPolyline(polylineOptions);
        mapa.moveCamera(CameraUpdateFactory.zoomTo(17));
        mapa.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(cInicio.getLatitud(), cInicio.getLongitud())));
        float distancia = 0;
        for (int i = 0; i < polylineOptions.getPoints().size() - 1; i++) {
            float[] results = new float[1];
            Location.distanceBetween(polylineOptions.getPoints().get(i).latitude,
                    polylineOptions.getPoints().get(i).longitude,
                    polylineOptions.getPoints().get(i + 1).latitude,
                    polylineOptions.getPoints().get(i + 1).longitude, results);
            distancia = distancia + results[0];
        }
        if (distancia > 1000) {
            tvMensaje.setText("Distancia recorrida: " + distancia / 1000 + " Kilometros");
        } else {
            tvMensaje.setText("Distancia recorrida: " + distancia + " metros");
        }
        //Marcas de Inicio y Final
        mapa.addMarker(new MarkerOptions()
                .title("Inicio")
                .snippet(cInicio.getDate().toString())
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                .position(new LatLng(cInicio.getLatitud(), cInicio.getLongitud())));

        mapa.addMarker(new MarkerOptions()
                .title("Fin")
                .snippet(cFin.getDate().toString())
                .position(new LatLng(cFin.getLatitud(), cFin.getLongitud())));

        muestraCalorias(distancia);


    }
    /*
    Suponemos que la persona va corriendo. Para calcular un caso u otro tendriamos que calcular la
    la velocidad desde la que se desplaza de un punto a otro y en función de dicha velocidad aplicar
    una u otra formula.

    Energía gastada CORRIENDO (kcal) = Peso (kg) x Distancia (km)
    Energía gastada ANDANDO (kcal) = (2/3) x Peso (kg) x Distancia (km)
     */
    public void muestraCalorias(float distancia){
        Config conf = new Config(this);
        String peso = conf.getUserPeso();
        float p = Float.parseFloat(peso);
        float calorias = p * (distancia/1000);
        tvMensaje.setText(tvMensaje.getText()+"\nCalorias: "+calorias+" kcal");
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean result = super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.action_settings).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                startActivity(new Intent(MainActivity.this, Configuracion.class));
                return true;
            }
        });
        return result;
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
                case BluetoothService.MSG_MODO_BUSQUEDA_SAT: {
                    Toast.makeText(getApplicationContext(), "Espere Led Azul", Toast.LENGTH_SHORT).show();
                    btnSincro.setEnabled(false);
                    break;
                }
                case BluetoothService.MSG_MODO_CARRERA_ON: {
                    Toast.makeText(getApplicationContext(), "Modo Carrera ON", Toast.LENGTH_SHORT).show();
                    btnSincro.setEnabled(false);
                    break;
                }
                case BluetoothService.MSG_MODO_CARRERRA_OFF: {
                    Toast.makeText(getApplicationContext(), "Modo Carrera OFF", Toast.LENGTH_SHORT).show();
                    btnSincro.setEnabled(false);
                    break;
                }
                case BluetoothService.MSG_MODO_SINCRO: {
                    Toast.makeText(getApplicationContext(), "Esperando sincronización", Toast.LENGTH_SHORT).show();
                    //Habilitamos boton sincronizar
                    btnSincro.setEnabled(true);
                    break;
                }

                // Mensaje de escritura: se mostrara en el Toast
                case BluetoothService.MSG_ESCRIBIR: {
                    //buffer = (byte[]) msg.obj;
                    //mensaje = new String(buffer);
                    //mensaje = "Enviando mensaje: " + mensaje;
                    //Toast.makeText(getApplicationContext(), mensaje, Toast.LENGTH_SHORT).show();
                    break;
                }
                case BluetoothService.MSG_SINCRO_FIN: {
                    tvMensaje.setText("Sincro Fin");
                    Toast.makeText(getApplicationContext(), R.string.SincroFin,
                            Toast.LENGTH_SHORT).show();
                    /*
                    Aqui tenemos que tratar data, generamos el fichero
                    Lo parseamos con Locus para obtener los puntos gps
                     */
                    //saveToFile(data);
                    if (createExernalStorePrivateFile(data)) {
                        List<Coordenada> lista = fileToCoordenadasList();
                        if (lista != null && !lista.isEmpty()) {
                            dibujaRuta(mapFragment.getMap(), lista);
                            mostrarMapa(true);
                        } else {
                            Toast.makeText(getApplicationContext(), "Coordenadas Vacias",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    data = "";
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
                            btnSincro.setEnabled(false);
                            btnSincro.setVisibility(View.VISIBLE);
                            btnBluetooth.setVisibility(View.GONE);
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
