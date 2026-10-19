package it.studiosmus.meteo;
import android.Manifest;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
public class SettingsActivity extends Activity {
 EditText lat,lon;SharedPreferences prefs;
 @Override public void onCreate(Bundle b){
  super.onCreate(b);prefs=getSharedPreferences("meteo",MODE_PRIVATE);
  LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(24,24,24,24);
  TextView title=new TextView(this);title.setText("Meteo — Cielo vivo\nOpen-Meteo · aggiornamento ogni 5 minuti");title.setTextSize(19);layout.addView(title);
  lat=new EditText(this);lat.setHint("Latitudine");lat.setInputType(8194);lat.setText(prefs.getString("lat","45.47"));layout.addView(lat);
  lon=new EditText(this);lon.setHint("Longitudine");lon.setInputType(8194);lon.setText(prefs.getString("lon","9.19"));layout.addView(lon);
  Button position=new Button(this);position.setText("Usa posizione del telefono");layout.addView(position);
  position.setOnClickListener(v->{if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},5);else useLocation();});
  Button save=new Button(this);save.setText("Salva località");layout.addView(save);save.setOnClickListener(v->saveLocation());
  Button apply=new Button(this);apply.setText("Imposta sfondo animato");layout.addView(apply);
  apply.setOnClickListener(v->{if(saveLocation()){Intent i=new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,new android.content.ComponentName(this,SkyWallpaperService.class));startActivity(i);}});
  WebView preview=new WebView(this);preview.getSettings().setJavaScriptEnabled(true);preview.getSettings().setDomStorageEnabled(true);
  layout.addView(preview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
  WebViewAssetLoader loader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
  preview.setWebViewClient(new WebViewClient(){@Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view,android.webkit.WebResourceRequest request){return loader.shouldInterceptRequest(request.getUrl());}});
  preview.loadUrl(SkyWallpaperService.skyUrl(this,false));setContentView(layout);
 }
 private boolean saveLocation(){
  try{double a=Double.parseDouble(lat.getText().toString()),b=Double.parseDouble(lon.getText().toString());if(a < -90||a>90||b < -180||b>180)throw new IllegalArgumentException();prefs.edit().putString("lat",String.valueOf(a)).putString("lon",String.valueOf(b)).apply();return true;}
  catch(Exception e){android.widget.Toast.makeText(this,"Coordinate non valide",0).show();return false;}
 }
 private void useLocation(){
  try{LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);Location loc=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);if(loc==null&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)loc=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
   if(loc!=null){lat.setText(String.valueOf(loc.getLatitude()));lon.setText(String.valueOf(loc.getLongitude()));saveLocation();}
   else android.widget.Toast.makeText(this,"Posizione non ancora disponibile",0).show();
  }catch(Exception e){android.widget.Toast.makeText(this,"Posizione non disponibile",0).show();}
 }
 @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==5&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)useLocation();}
}