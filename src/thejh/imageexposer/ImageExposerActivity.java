package thejh.imageexposer;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class ImageExposerActivity extends Activity {
//	private static String CONTROL_FILE = "/sys/devices/platform/usb_mass_storage/lun0/file";
	private static String CONTROL_FILE;
	public static final int ACTIVITY_CHOOSE_FILE = 1;
	
	private static void findLun0(File dir, LinkedList<File> results) {
//		Log.d("imageexposer", "traversing "+dir.getAbsolutePath());
		File[] files = dir.listFiles();
		for (File f: files) {
			if (f.getName().equals("lun0")) {
				Log.i("imageexposer", "found a lun0 dir");
				File[] lun0_subfiles = f.listFiles(new FilenameFilter() {
					@Override
					public boolean accept(File dir, String filename) {
						return filename.equals("file");
					}
				});
				if (lun0_subfiles.length == 1) {
					results.add(lun0_subfiles[0]);
				}
			} else
				try {
					if (f.isDirectory() && f.getAbsolutePath().equals(f.getCanonicalPath())) {
						findLun0(f, results);
					}
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
		}
	}
	
	static {
		// figure out where the control file is
		File sysfs = new File("/sys/devices");
		LinkedList<File> results = new LinkedList<File>();
		findLun0(sysfs, results);
		if (results.size() == 0) {
			throw new RuntimeException("no control file found");
		}
		CONTROL_FILE = results.getFirst().getAbsolutePath();
	}
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        refresh_image_display();
    }
    
    private void set_image_display(String str) {
    	TextView display = (TextView) findViewById(R.id.current_image_display);
    	display.setText(str);
    }
    
    private void refresh_image_display() {
    	set_image_display("<checking...>");
    	try {
			Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat "+CONTROL_FILE});
			InputStream in = p.getInputStream();
			byte[] buf = new byte[4096]; // should be sufficient
			int bufpos = 0;
			while (true) {
				int bytesRead = in.read(buf, bufpos, buf.length - bufpos);
				if (bytesRead == -1 || bufpos == buf.length) break;
				bufpos += bytesRead;
			}
			if (bufpos == 0 || buf[0] == 0) {
				set_image_display("<none>");
				return;
			}
			String path = new String(buf, 0, bufpos);
			set_image_display(path);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
    }
    
    public void refresh_image_display(View view) {
    	refresh_image_display();
    }
    
    public void select_new_image(View view) {
        Intent chooseFile;
        Intent intent;
        chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.setType("file/*");
        intent = Intent.createChooser(chooseFile, "choose a disk image file");
        startActivityForResult(intent, ACTIVITY_CHOOSE_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	switch(requestCode) {
    	case ACTIVITY_CHOOSE_FILE:
    		if (resultCode == RESULT_OK){
    			Uri uri = data.getData();
    			String filePath = uri.getPath();
    			if (filePath.indexOf("'") != -1) return;
    			try {
					Runtime.getRuntime().exec(new String[]{"su", "-c", "echo -n '"+filePath+"' > "+CONTROL_FILE}).waitFor();
				} catch (InterruptedException e) {
					e.printStackTrace();
					System.exit(1);
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
    			refresh_image_display();
    		}
    		break;
    	}
    }
    
    public void unbind_image(View view) {
    	try {
			Runtime.getRuntime().exec(new String[]{"su", "-c", "echo -n -e '\\0' > "+CONTROL_FILE}).waitFor();
		} catch (InterruptedException e) {
			e.printStackTrace();
			System.exit(1);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
    	refresh_image_display();
    }
}