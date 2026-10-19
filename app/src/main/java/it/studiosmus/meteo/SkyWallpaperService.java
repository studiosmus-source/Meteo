package it.studiosmus.meteo;
import android.app.Presentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.service.wallpaper.WallpaperService;
import android.view.Display;
import android.view.SurfaceHolder;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
public class SkyWallpaperService extends WallpaperService {
 @Override public Engine onCreateEngine(){return new SkyEngine();}
 private final class SkyEngine extends Engine {
  private VirtualDisplay virtualDisplay; private SkyPresentation presentation;
  private int width,height; private boolean visible;
  @Override public void onSurfaceChanged(SurfaceHolder holder,int format,int w,int h){
   super.onSurfaceChanged(holder,format,w,h);if(w<=0||h<=0)return;
   if(virtualDisplay!=null&&w==width&&h==height)return;
   width=w;height=h;destroyDisplay();if(visible)createDisplay();
  }
  @Override public void onVisibilityChanged(boolean v){visible=v;if(v){if(virtualDisplay==null&&width>0&&height>0)createDisplay();}/* Keep the existing rendered WebView alive while lock screen toggles visibility. */}
  private void createDisplay(){
   try{
    DisplayManager manager=(DisplayManager)getSystemService(Context.DISPLAY_SERVICE);
    int density=getResources().getDisplayMetrics().densityDpi;
    virtualDisplay=manager.createVirtualDisplay("MeteoWallpaper",width,height,density,getSurfaceHolder().getSurface(),DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
    if(virtualDisplay==null)return;
    Display display=virtualDisplay.getDisplay();
    presentation=new SkyPresentation(SkyWallpaperService.this,display);presentation.show();
   }catch(Exception ex){android.util.Log.e("Meteo","Wallpaper rendering error",ex);destroyDisplay();}
  }
  private void destroyDisplay(){if(presentation!=null){presentation.dismiss();presentation=null;}if(virtualDisplay!=null){virtualDisplay.release();virtualDisplay=null;}}
  @Override public void onSurfaceDestroyed(SurfaceHolder holder){destroyDisplay();super.onSurfaceDestroyed(holder);}
  @Override public void onDestroy(){destroyDisplay();super.onDestroy();}
 }
 static String skyUrl(Context ctx,boolean wallpaper){
  SharedPreferences p=ctx.getSharedPreferences("meteo",MODE_PRIVATE);
  String lat=p.getString("lat","45.47"),lon=p.getString("lon","9.19");
  return "https://appassets.androidplatform.net/assets/sky.html?lat="+lat+"&lon="+lon+(wallpaper?"&wallpaper=1":"");
 }
 private static final class SkyPresentation extends Presentation {
  private WebView web;
  SkyPresentation(Context context,Display display){super(context,display);}
  @Override protected void onCreate(Bundle b){
   super.onCreate(b);web=new WebView(getContext());web.setBackgroundColor(android.graphics.Color.BLACK);
   WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
   setContentView(web);
   WebViewAssetLoader loader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(getContext())).build();
   web.setWebViewClient(new WebViewClient(){@Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view,android.webkit.WebResourceRequest request){return loader.shouldInterceptRequest(request.getUrl());}});
   web.loadUrl(skyUrl(getContext(),true));
  }
  @Override protected void onStop(){if(web!=null){web.loadUrl("about:blank");web.destroy();web=null;}super.onStop();}
 }
}