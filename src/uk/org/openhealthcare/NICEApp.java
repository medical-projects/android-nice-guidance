/*
*    Copyright (C) 2011  Open Health Care, R.Jones, Dr. VJ Joshi
*    Additions Copyright (C) 2011 Neil McPhail
*
*    This program is free software: you can redistribute it and/or modify
*    it under the terms of the GNU General Public License as published by
*    the Free Software Foundation, either version 3 of the License, or
*    (at your option) any later version.
*
*    This program is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package uk.org.openhealthcare;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.lang.Boolean;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Menu; 
import android.os.AsyncTask;


public class NICEApp extends ListActivity {

	private static final int PREFERENCES_GROUP_ID = 0;
	private static final int SHARE_ID = 0;
	private static final int GETALL_ID = 1;
	private static final int FEEDBACK_ID = 2;
	private static final int SEARCH_ID = 3;
	private static final int RELOAD_ID = 4;
	private static final int HELP_ID = 5;
	private static final int ABOUT_ID = 6;
	private static boolean downloadLock = false;
	GuidelineData guidelines;
	int cached[];
	int numGuidelines;
	int lastOpened;
	boolean firstrun;
    boolean haveConnectedWifi = false;
    boolean haveConnectedMobile = false;	

	ArrayAdapter<String> arrad;
	ArrayAdapter<String> adapter = null;
	ListView lv;

	
	@Override



public boolean onCreateOptionsMenu(Menu menu)
	{
	super.onCreateOptionsMenu(menu);

	menu.add(PREFERENCES_GROUP_ID, SHARE_ID, 0, "share")
	.setIcon(android.R.drawable.ic_menu_share);
	menu.add(PREFERENCES_GROUP_ID, GETALL_ID, 0, "download all")
	.setIcon(android.R.drawable.ic_menu_save);
	menu.add(PREFERENCES_GROUP_ID, FEEDBACK_ID, 0, "feedback")
	.setIcon(android.R.drawable.ic_menu_send);
	//menu.add(PREFERENCES_GROUP_ID, SEARCH_ID, 0, "search")
	//.setIcon(android.R.drawable.ic_menu_search);
	menu.add(PREFERENCES_GROUP_ID, RELOAD_ID, 0, "last file")
	.setIcon(android.R.drawable.ic_menu_rotate);
	menu.add(PREFERENCES_GROUP_ID, HELP_ID, 0, "help")
	.setIcon(android.R.drawable.ic_menu_help);
	menu.add(PREFERENCES_GROUP_ID, ABOUT_ID, 0, "about")
	.setIcon(android.R.drawable.ic_menu_info_details);

	return true;
	} 
	
	public boolean onOptionsItemSelected(MenuItem item)
	{
	switch (item.getItemId()) {
	   case SHARE_ID: 
		   LayoutInflater inflater = getLayoutInflater();
		   View layout = inflater.inflate(R.layout.toast_layout,
		                                  (ViewGroup) findViewById(R.id.toast_layout_root));

		   ImageView image = (ImageView) layout.findViewById(R.id.image);
		   image.setImageResource(R.drawable.qrcode);

		   Toast toast = new Toast(getApplicationContext());
		   toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
		   toast.setDuration(Toast.LENGTH_LONG);
		   toast.setView(layout);
		   toast.show();

	   			return true;
	   case HELP_ID: Toast.makeText(getApplicationContext(), 
               "Cached items are in bold.\nLast opened file is highlighted.\n\nMake sure you have a PDF Reader installed.", 
               Toast.LENGTH_LONG).show();
				return true;
	   case FEEDBACK_ID: Toast.makeText(getApplicationContext(), 
               "http://openhealthcare.org.uk\n\nCome say hello :)", 
               Toast.LENGTH_LONG).show();
	   			return true;	
	   case ABOUT_ID: 
		   Toast.makeText(getApplicationContext(),
				   "Developers:\nRoss Jones / Dr VJ Joshi / Neil McPhail",
				   Toast.LENGTH_LONG).show();	   
		   return true;
		   
	   case GETALL_ID: 
		   
		   StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
		   double sdAvailSize = (double)stat.getAvailableBlocks() *(double)stat.getBlockSize();
		   //Check for space first ***ToDo****
	   
		   if (sdAvailSize<25000000){
		    	Toast.makeText(getApplicationContext(), 
		                "Not enough space on SDCard", 
		                Toast.LENGTH_LONG).show();
		   }else
		   {
		   
	    if (isNetworkAvailable()){ 
	    	if (haveConnectedWifi){ 
		    AlertDialog ad = new AlertDialog.Builder(this).create();  
		    //ad.setCancelable(false); // This blocks the 'BACK' button  
		    ad.setTitle("This could be slow...");
		    ad.setMessage("Phone will download all missing files.  Please let it do its thing\n\nDownload can take approx 3 mins over WiFi (25Mb)\n\nPress BACK to back out");  
	
		    ad.setButton("Go", new DialogInterface.OnClickListener() {  
		        @Override  
		        public void onClick(DialogInterface dialog, int which) {  
					final Toast ShortToast = Toast.makeText(getApplicationContext(),
							"Starting downloads",
							Toast.LENGTH_SHORT);
					
					Timer timer = new Timer();
					   TimerTask task = new TimerTask() {

						   @Override
						   public void run() {
						     // make sure to cancel the Toast in UI thread
						     runOnUiThread(new Runnable() {

						       @Override
						       public void run() {
						    	   ShortToast.cancel();
						       }
						     });
						   }
						 };

						 ShortToast.show();
						 timer.schedule(task, 500);
						 
					new AsyncDownload().execute(guidelines.GetKeys());
				   dialog.dismiss();
		 		   
		        }  
		    });  
		    ad.show();  
	    }
	    	else
		    {
		    	Toast.makeText(getApplicationContext(), 
		                "Inadvisable unless over a WiFi connection", 
		                Toast.LENGTH_LONG).show();
		    }
	    }
	    else
	    {
	    	Toast.makeText(getApplicationContext(), 
	                "No Network Connectivity", 
	                Toast.LENGTH_LONG).show();
	    }
		   }   
	    return true;

	    case SEARCH_ID:
		       return true;
	
	    case RELOAD_ID:
	       Object item1 = getListAdapter().getItem(lastOpened);
		   String key = (String) item1;
		   new AsyncDownload().execute(key);
		   return true;
			}
		   
	return false;
	}   

	public void onCreate(Bundle savedInstanceState) {
	  super.onCreate(savedInstanceState);
	  
	  SharedPreferences settings = getPreferences (0);
	  
	  firstrun = settings.getBoolean("firstrun", true);
	  
	  String folderString = pathToStorage(null);
	  File folder = new File(folderString);
	  if ( ! folder.exists() ) {
		  folder.mkdir();
	  }
	  
	  String targetFile = pathToStorage("guidelines.xml");
		boolean exists = (new File(targetFile)).exists();
		if (exists) {
			//do nothing
		} else {
			if (firstrun){
				  CopyAssets("");
					sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse
							("file://"
							+ Environment.getExternalStorageDirectory())));
				  firstrun=false;	  
			}
		}
	  
	  try { 
		  guidelines = new GuidelineData(this);
	  } catch (Exception elocal) {
          Toast.makeText(getApplicationContext(), 
                  "Failed to load guideline list", 
                  Toast.LENGTH_LONG).show();		  
	  }
	   
	  Object[] c = guidelines.GetKeys();
	  Arrays.sort(c);
	  numGuidelines=c.length;
	  
	  cached = new int[numGuidelines];
	  
	  int count =numGuidelines;		
		for (int i = 0; i < count; i++){
			cached[i] = settings.getInt(Integer.toString(i), 0);
		}
	  lastOpened = settings.getInt("last", 0);

	  new CheckExists().execute(guidelines.GetKeys());
	  final ArrayAdapter<String> arrad = new ColourArray(this, (String[])c);
	  setListAdapter(arrad);

	  lv = getListView();
	  lv.setFastScrollEnabled(true);
	  lv.setTextFilterEnabled(true);
	  
	  
	  handleIntent(getIntent());

	  lv.setOnItemClickListener(new OnItemClickListener(){
			@Override
		    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				
					Object item = getListAdapter().getItem(position);
					String key = (String) item;
					new AsyncDownload().execute(key);
					if (cached[position]==1){
						Toast.makeText(getApplicationContext(), 
			                  "Accessing", 
			                  Toast.LENGTH_SHORT).show();
						};
					if (isNetworkAvailable()){ 
						cached[position]=1;
						lastOpened=position;
						arrad.notifyDataSetChanged();
						lv.invalidateViews();
					}	
			}		   
	  });

	}

	@Override
    protected void onStop(){
       super.onStop();

      SharedPreferences settings = getPreferences(0);
      SharedPreferences.Editor editor = settings.edit();
      int count =numGuidelines;		
		for (int i = 0; i < count; i++){
			editor.putInt(Integer.toString(i), cached[i]);
		}
		editor.putInt("last", lastOpened);
		editor.putBoolean("firstrun", firstrun);
	
      editor.commit();
    }

	@Override
	protected void onNewIntent(Intent intent) {
		setIntent(intent);
		handleIntent(intent);
	}

	private void handleIntent(Intent intent) {
	    if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
	      String query = intent.getStringExtra(SearchManager.QUERY);
	      //arrad.getFilter().filter(query);
	      lv.setFilterText(query);    
	    }
	}

	public String MD5_Hash(String s) { 
		  MessageDigest m = null;

		  try {
	            	m = MessageDigest.getInstance("MD5");
		  } catch (NoSuchAlgorithmException e) {
	            	e.printStackTrace();
		  }

	    m.update(s.getBytes(),0,s.length());
	    String hash = new BigInteger(1, m.digest()).toString(16);
	    return hash;
	  }


	public String pathToStorage(String filename) {
		File sdcard = Environment.getExternalStorageDirectory();
		if ( filename != null )
			return sdcard.getAbsolutePath() + File.separator+ "nice_guidance" + File.separator + filename;		
		return sdcard.getAbsolutePath() + File.separator+ "nice_guidance" + File.separator;		
	}
	
	public boolean download(String guideline) { 
		//What is public stays public
		AsyncTask myDownload = new AsyncDownload().execute(guideline);
		try{
			Boolean success = (Boolean) myDownload.get();
			return success.booleanValue();
		}catch(InterruptedException e){
			return false;
		}catch(java.util.concurrent.ExecutionException f){
			return false;
		}

	}
	private class AsyncDownload extends AsyncTask<String, String, Boolean> {

		protected Boolean doInBackground(String... guidelinelist) {
			try {
			int count = guidelinelist.length;
			Boolean singlesuccess = Boolean.FALSE; // if called on a single file the pdf viewer may be opened		
			for (int i = 0; i < count; i++){
				GuidelineItem item =guidelines.Get(guidelinelist[i]); 
				String url = item.url;
				String hash = MD5_Hash(url);
				String targetFile = pathToStorage(hash + ".pdf");
				File file = new File(targetFile);
				if (! file.exists() ) {
					if (isNetworkAvailable()){
						if (downloadLock) {
						publishProgress("Please wait for previous files to download");
						return Boolean.FALSE;
						}
						downloadLock = true;
						if (count == 1){
							publishProgress("Downloading\n" + guidelinelist[i]);
						}else
						{
							publishProgress("Download Progress:\n" + guidelinelist[i]);
						}
						
						DownloadPDF p = new DownloadPDF();
						try {
							p.DownloadFrom(url, targetFile);
							if (!haveConnectedWifi) publishProgress("Downloaded successfully");
							singlesuccess = Boolean.TRUE;

						} catch (Exception exc){
							publishProgress("Failed to download the PDF " + exc.toString());
						}
						downloadLock = false;
						}
					else
						{
						publishProgress("File not cached\nNo Network Connectivity"); 
						}
				} else {
					//publishProgress("Accessing");
					singlesuccess = Boolean.TRUE;
				}
			}
			if (count == 1  && singlesuccess && ! downloadLock ) {
				GuidelineItem item =guidelines.Get(guidelinelist[0]); 
				String url = item.url;
				String hash = MD5_Hash(url);
				String targetFile = pathToStorage(hash + ".pdf");
				File file = new File(targetFile);
                    		Uri path = Uri.fromFile(file);
                    		Intent intent = new Intent(Intent.ACTION_VIEW);
                    		intent.setDataAndType(path, "application/pdf");
                    		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                    		try {
                        		startActivity(intent);
                    		} 
                    		catch (ActivityNotFoundException e) {
                    			publishProgress("No application available to view PDF files");
                    			// Can't do this in a thread.
                    		}
			}
			if (count == 1) return singlesuccess;
			return Boolean.TRUE; } catch ( Exception eee ) {
/*				Toast.makeText(getApplicationContext(),
						eee.toString(),
						Toast.LENGTH_SHORT).show();*/
				return false;
			}
		}
		protected void onProgressUpdate(String... progress) {
			  
			   Toast.makeText(getApplicationContext(), progress[0], Toast.LENGTH_SHORT).show();
			   				 
		}
	}
	
	
	private class CheckExists extends AsyncTask<String, String, Boolean> {
		protected Boolean doInBackground(String... guidelinelist) {
			
			int count = guidelinelist.length;
			for (int i = 0; i < count; i++){
				GuidelineItem item = guidelines.Get(guidelinelist[i]);
				String url = item.url;
				String hash = MD5_Hash(url);
				String targetFile = pathToStorage(hash + ".pdf");
				boolean exists = (new File(targetFile)).exists();
				if (exists) {
					cached[i] =  1;
				} else {
					cached[i] =  0;
				}
			}
			publishProgress("Done");
		return true;
	}
		protected void onProgressUpdate(String... progress) {
			lv.invalidateViews();
		}
}
	
	private static class FilesViewHolder {
        public TextView separator;
        public TextView textView;
        public TextView subtitleView;
    }
	
	public class ColourArray extends ArrayAdapter<String> implements Filterable{
		
		private final Activity context;
		public final String[] names;

		public ColourArray(Activity context, String[] names) {
			super(context, R.layout.list_item, names);
			this.context = context;
			this.names = names;

		}

		@Override
		
		public View getView(int position, View convertView, ViewGroup parent) {
			LayoutInflater inflater = context.getLayoutInflater();

			//View rowView = LayoutInflater.from(context).inflate(R.layout.list_item, parent, false);
			View rowView = inflater.inflate(R.layout.list_item, null, true);
			
			FilesViewHolder holder = new FilesViewHolder();
			
			holder.textView = (TextView) rowView.findViewById(R.id.label);
			ImageView imageView = (ImageView) rowView.findViewById(R.id.icon);
			ImageView imageView2 = (ImageView) rowView.findViewById(R.id.icon2);
			holder.separator = (TextView) rowView.findViewById(R.id.separator);
			holder.subtitleView = (TextView) rowView.findViewById(R.id.subtitle);
				
			String s = names[position];
			int length = s.length()%2;
			
			holder.separator.setText(s.substring(0,1));
			holder.textView.setText(s);
			holder.subtitleView.setText("NICE CG"+length+""+position+"   Other Extra Stuff");
			
						
			if (length==0) {imageView.setImageResource(R.drawable.icon);}
			else {imageView.setImageResource(R.drawable.fox);}
			
			int length2 = s.length()%3;
			if (length2==0) {imageView2.setImageResource(R.drawable.stethoscope);}
			if (length2==1) {imageView2.setImageResource(R.drawable.primary_care);}
			if (length2==2) {imageView2.setImageResource(R.drawable.pharmacology);}
			
			int length3 = position%6;
			if (length3!=0) holder.separator.setVisibility(View.GONE);
			
			
			 //holder.titleView.setText(holder.titleBuffer.data, 0, holder.titleBuffer.sizeCopied);
			 
			 
			//Temporary Hardcode//
			//imageView2.setImageResource(R.drawable.primary_care);
			
			if (s.startsWith("Acute")) {imageView2.setImageResource(R.drawable.stethoscope);}
			
			if (s.startsWith("Alcohol")) {imageView2.setImageResource(R.drawable.iv);}
			
			if (s.startsWith("Anaemia")) {imageView2.setImageResource(R.drawable.blood);}
			
			if (s.startsWith("Antenatal")) {imageView2.setImageResource(R.drawable.gynaecology);}
						
			if (s.startsWith("Atrial fibrillation")) {imageView2.setImageResource(R.drawable.cardiology);}
			
			if (s.startsWith("Bacterial")) {imageView2.setImageResource(R.drawable.medicine);}
			//////////////////////
			
			if (cached[position] > 0) {
				holder.textView.setTextColor(Color.rgb(255,255,255));
			}else {
				holder.textView.setTextColor(Color.rgb(127,127,127));
			}	
			
			if (position==lastOpened && lastOpened!=0) {
				holder.textView.setBackgroundColor(Color.rgb(15,15,191)); 
			}
			
			rowView.setTag(holder);
			return rowView;
		}

	}


//	   private boolean isNetworkAvailable() {
//		    ConnectivityManager connectivityManager 
//		          = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
//		    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
//		    return activeNetworkInfo != null;
//		}
	   
	   private boolean isNetworkAvailable() {

		    haveConnectedWifi = false;
		    haveConnectedMobile = false;
		    
		    ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		    NetworkInfo[] netInfo = cm.getAllNetworkInfo();
		    for (NetworkInfo ni : netInfo) {
		        if (ni.isConnected()){
		        	if (ni.getTypeName().equalsIgnoreCase("WIFI")) haveConnectedWifi = true;
		        	if (ni.getTypeName().equalsIgnoreCase("MOBILE")) haveConnectedMobile = true;
		        }
		    }
		    return haveConnectedWifi || haveConnectedMobile;
		}

	   
	   private void CopyAssets(String path) {
		    AssetManager assetManager = this.getAssets();
		    String assets[] = null;
		    try {
		        Log.i("tag", "CopyAssets() "+path);
		        assets = assetManager.list(path);
		        if (assets.length == 0) {
		            copyFile(path);
		        } else {
		            String fullPath =  pathToStorage(path);
		            Log.i("tag", "path="+fullPath);
		            File dir = new File(fullPath);
		            if (!dir.exists() && !path.startsWith("images") && !path.startsWith("sounds") && !path.startsWith("webkit"))
		                if (!dir.mkdirs());
		                    Log.i("tag", "could not create dir "+fullPath);
		            for (int i = 0; i < assets.length; ++i) {
		                String p;
		                if (path.equals(""))
		                    p = "";
		                else 
		                    p = path + "/";

		                if (!path.startsWith("images") && !path.startsWith("sounds") && !path.startsWith("webkit"))
		                	CopyAssets( p + assets[i]);
		            }
		        }
		    } catch (IOException ex) {
		        Log.e("tag", "I/O Exception", ex);
		    }
		}

		private void copyFile(String filename) {
		    AssetManager assetManager = this.getAssets();

		    InputStream in = null;
		    OutputStream out = null;
		    String newFileName = null;
		    try {
		        Log.i("tag", "copyFile() "+filename);
		        in = assetManager.open(filename);
		        newFileName = pathToStorage(filename);
		        out = new FileOutputStream(newFileName);

		        byte[] buffer = new byte[1024];
		        int read;
		        while ((read = in.read(buffer)) != -1) {
		            out.write(buffer, 0, read);
		        }
		        in.close();
		        in = null;
		        out.flush();
		        out.close();
		        out = null;
		    } catch (Exception e) {
		        Log.e("tag", "Exception in copyFile() of "+newFileName);
		        Log.e("tag", "Exception in copyFile() "+e.toString());
		    }

	}}