package fjmorsan.upo.es.ardufitv13;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by fran on 8/06/15.
 */
public class Coordenada {
    private Date date;
    private int fix;
    private double latitud;
    private double longitud;
    private int altura;

    public Coordenada(Date date, int fix, double latitud, double longitud, int altura) {
        this.date = date;
        this.fix = fix;
        this.latitud = latitud;
        this.longitud = longitud;
        this.altura = altura;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public int getFix() {
        return fix;
    }

    public void setFix(int fix) {
        this.fix = fix;
    }

    public double getLatitud() {
        return latitud;
    }

    public void setLatitud(double latitud) {
        this.latitud = latitud;
    }

    public double getLongitud() {
        return longitud;
    }

    public void setLongitud(double longitud) {
        this.longitud = longitud;
    }

    public int getAltura() {
        return altura;
    }

    public void setAltura(int altura) {
        this.altura = altura;
    }

    @Override
    public String toString() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-M-yyyy hh:mm:ss");
        String fecha = sdf.format(this.getDate());
        return "datetime: " + fecha +
                "\nfix: " + fix +
                "\nlatitud: " + latitud +
                "\nlongitud: " + longitud +
                "\naltura: " + altura+"\n";
    }
}
