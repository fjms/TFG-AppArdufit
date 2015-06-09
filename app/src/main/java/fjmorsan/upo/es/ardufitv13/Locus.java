package fjmorsan.upo.es.ardufitv13;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Locus {

    public static List<Coordenada> parseFile(File dir, String filename) {
        File archivo = new File(dir, filename);
        FileReader fr = null;
        BufferedReader br;
        List<Coordenada> coords = null;
        try {
            fr = new FileReader(archivo);
            br = new BufferedReader(fr);
            String linea;
            coords = new ArrayList<>();
            linea = br.readLine();
            while ((linea = br.readLine()) != null) {

                List resultados = parseLine(linea);
                if (!resultados.isEmpty()) {
                    coords.addAll(resultados);
                }
            }
            return coords;

        } catch (FileNotFoundException ex) {
            Logger.getLogger(Locus.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Locus.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            //cerramos el fichero salga o no una exception
            try {
                if (null != fr) {
                    fr.close();
                }
            } catch (Exception e) {
                Logger.getLogger(Locus.class.getName()).log(Level.SEVERE, null, e);
            }
        }
        return coords;
    }

    private static List parseLine(String linea) {
        List<Coordenada> records = new ArrayList<>();
        if (linea.startsWith("$PMTKLOX,1")) {
            if (linea.contains("*")) {

                String[] split = linea.split("\\*");
                String data = split[0];
                String actual_checksum = split[1];
                String generated_checksum = checksum(data);
                if (actual_checksum.equals(generated_checksum)) {
                /*  remove the first 3 parts - command, type, line_number
                 following this 8 byte hex strings (max 24)
                 */
                    String[] parts = data.split(",");
                    String dataFields = "";
                    for (int i = 3; i < parts.length; i++) {
                        dataFields = dataFields + parts[i];
                    }
                    int chunksize = 32; //Basic logging
                    while (dataFields.length() >= chunksize) {
                        String sub = dataFields.substring(0, chunksize);
                        int[] bytes = hexStringToIntArray(sub);
                        Coordenada record = parseBasicRecord(bytes);
                        records.add(record);
                        dataFields = dataFields.substring(chunksize);
                    }
                } else {
                    System.out.println("WARNING: Checksum failed. Expected " + actual_checksum + " but calculated " + generated_checksum + " for " + data);
                }
            }
        }
        return records;
    }

    private static int[] hexStringToIntArray(String s) {
        int len = s.length();
        int[] data = new int[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (int) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private static String checksum(String data) {
        int check = 0;
        //XOR all the chars in the line except leading $
        for (int i = 1; i < data.length(); i++) {
            check = check ^ data.charAt(i);
        }
        String toHexString = Integer.toHexString(check);
        return toHexString.toUpperCase();
    }

    /*
     #
     # Basic Record - 16 bytes
     # 0 - 3 timestamp
     # 4 fix flag
     # 5 - 8 latitude
     # 9 - 12 longitude
     # 13 - 14 height
     */
    private static Coordenada parseBasicRecord(int[] bytes) {

        long timestamp = parseLong(Arrays.copyOfRange(bytes, 0, 4));
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timestamp);
        int fix = bytes[4]; // TODO bit flag     unsigned char u1VALID = 0x00;  // 0:NoFix , 1: Fix, 2: DGPS, 6: Estimated
        double latitud = parseDouble(Arrays.copyOfRange(bytes, 5, 9));
        double longitud = parseDouble(Arrays.copyOfRange(bytes, 9, 13));
        int height = parseInt(Arrays.copyOfRange(bytes, 13, 15));

        Coordenada coordenadas = new Coordenada(c.getTime(), fix, latitud, longitud, height);
        return coordenadas;

    }

    private static long parseLong(int[] bytes) {
        if (bytes.length != 4) {
            System.err.println("WARNING: expecting 4 bytes got " + bytes.length + " bytes");
        }
        return ((0xFF & bytes[3]) << 24) | ((0xFF & bytes[2]) << 16) | ((0xFF & bytes[1]) << 8) | (0xFF & bytes[0]);
    }

    private static double parseDouble(int[] bytes) {
        long longValue = parseLong(bytes);
        double exponente = (longValue >> 23) & 0xff;
        exponente -= 127.0;
        exponente = Math.pow(2, exponente);
        double mantissa = (longValue & 0x7fffff);
        mantissa = 1.0 + (mantissa / 8388607.0);
        double doubleValue = mantissa * exponente;
        if ((longValue & 0x80000000) == 0x80000000) {
            doubleValue = -doubleValue;
        }
        return doubleValue;
    }

    private static int parseInt(int[] bytes) {
        if (bytes.length != 2) {
            System.err.println("WARNING: expecting 2 bytes got " + bytes.length + " bytes");
        }
        int number = ((0xFF & bytes[1]) << 8) | (0xFF & bytes[0]);
        return number;
    }
}
