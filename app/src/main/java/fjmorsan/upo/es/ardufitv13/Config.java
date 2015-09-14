package fjmorsan.upo.es.ardufitv13;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Clase que nos sirve para almancenar el peso y la talla
 */
public class Config {
    private final String SHARED_PREFS_FILE = "HMPrefs";
    private final String KEY_PESO = "peso";
    private final String KEY_TALLA = "talla";

    private Context mContext;





    public Config(Context context){
        mContext = context;
    }
    private SharedPreferences getSettings(){
        return mContext.getSharedPreferences(SHARED_PREFS_FILE, 0);
    }

    public String getUserPeso(){
        return getSettings().getString(KEY_PESO, null);
    }
    public String getUserTalla(){
        return getSettings().getString(KEY_TALLA, null);
    }

    public void setUserPeso(String peso){
        SharedPreferences.Editor editor = getSettings().edit();
        editor.putString(KEY_PESO, peso );
        editor.commit();
    }
    public void setUserTalla(String talla){
        SharedPreferences.Editor editor = getSettings().edit();
        editor.putString(KEY_TALLA, talla );
        editor.commit();
    }
}
