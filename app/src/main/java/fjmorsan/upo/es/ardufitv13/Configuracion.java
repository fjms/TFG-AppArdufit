package fjmorsan.upo.es.ardufitv13;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class Configuracion extends AppCompatActivity {

    private EditText etpeso;
    private EditText etTalla;
    private Button btnGuardar;
    private Config conf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configuracion);
        etpeso = (EditText) findViewById(R.id.etPeso);
        etTalla = (EditText) findViewById(R.id.etTalla);
        btnGuardar = (Button) findViewById(R.id.btnPesoTalla);
        conf = new Config(this);


        btnGuardar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //conf.setUserEmail(txtEmail.getText().toString());
                conf.setUserPeso(etpeso.getText().toString());
                conf.setUserTalla(etTalla.getText().toString());
                finish();
            }
        });


    }




}
