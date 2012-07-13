package org.oep.base64;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.google.zxing.integration.android.IntentIntegrator;

import net.iharder.base64.Base64;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;

public class Base64Activity extends Activity {
	private Uri mSelectedImage;
	private byte[] mImageBytes;
	private Thread mWorkThread;
	private AlertDialog mWaiter;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        initialize();
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	syncUI();
    }

    
    @Override
    protected Dialog onCreateDialog(int n) {
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	
    	switch(n) {
    	case Base64Activity.DIALOG_EMPTY_TEXT_INPUT:
    		return builder.setMessage(R.string.msg_emptyTextInput)
    			.setTitle(R.string.title_emptyField)
    			.setCancelable(false)
    			.setNeutralButton(R.string.label_ok, new Dialog.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
    			})
    			.create();
    		
    	case Base64Activity.DIALOG_IMAGE_PICKER:
    		final Dialog dialog = new Dialog(this);
    		dialog.setContentView(R.layout.image_chooser);
    		dialog.setTitle(R.string.title_chooseImage);
    		return dialog;
    		
    	case Base64Activity.DIALOG_WAITER:
    		Resources res = getResources();
    		mWaiter = ProgressDialog.show(this,
    				res.getString(R.string.title_pleaseWait), res.getString(R.string.msg_working));
    		return mWaiter;
    	}
    	
    	return builder.setMessage("Programmer is a dummy!").create();
    }
    
    @Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == SELECT_IMAGE && resultCode == Activity.RESULT_OK) {
			mSelectedImage = data.getData();
			mImageBytes = null;
		}
		
		else if(requestCode == IntentIntegrator.REQUEST_CODE && resultCode == Activity.RESULT_OK) {
			String contents = data.getStringExtra("SCAN_RESULT");
			String format = data.getStringExtra("SCAN_RESULT_FORMAT");
			
			EditText base64 = (EditText) findViewById(R.id.main_base64Field);
			base64.setText(contents);
		}
	}
    
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.mainmenu_share:
            doShare();
            return true;
        case R.id.mainmenu_showBarcode:
            doShowBarcode();
            return true;
        }
        return false;
    }
    
    private void initialize() {
    	((Spinner) findViewById(R.id.spinner_selectSource) ).setOnItemSelectedListener(
    			new OnItemSelectedListener() {
    				@Override
    				public void onItemSelected(AdapterView<?> adapter, View v, int position, long id) {
    					Base64Activity.this.syncUI();
    				}
    				
    				@Override
    				public void onNothingSelected(AdapterView<?> arg0) {
    					// I don't care.
    				}
    			}
    	);
    }
    public void syncUI() {
    	switch(getDataType()) {
    	case TYPE_TEXT:
    		findViewById(R.id.container_imageInput).setVisibility(View.GONE);
    		findViewById(R.id.container_plaintextInput).setVisibility(View.VISIBLE);
    		break;
    		
    	case TYPE_IMAGE:
    		findViewById(R.id.container_imageInput).setVisibility(View.VISIBLE);
    		findViewById(R.id.container_plaintextInput).setVisibility(View.GONE);
    		break;
    	}
    }
    
    public void doPickImage(View v) {
    	Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
    	startActivityForResult(i, SELECT_IMAGE);
    }
    
    public void doEncode(View v) {
    	switch(getDataType()) {
    	case TYPE_TEXT:
    		doTextEncode();
    		break;
    		
    	case TYPE_IMAGE:
    		doImageEncode();
    		break;
    	}
    }
    
    private void doImageEncode() {
    	final EditText base64Field = (EditText) this.findViewById(R.id.main_base64Field);
    	showDialog(DIALOG_WAITER);
    	
    	final Handler threadHandler = new Handler() {
    		public void handleMessage(Message msg) {
    			Bundle data = msg.getData();
    			switch(msg.arg1) {
    			case MSG_UPDATE_STATUS:
    				if(mWaiter != null) {
    					String s = getResources().getString(msg.arg2);
    					mWaiter.setMessage(s);
    				}
    				break;
    				
    			case MSG_RECEIVE_CIPHER:
    				mWaiter = null;
    				Base64Activity.this.removeDialog(DIALOG_WAITER);
    				base64Field.setText((String) data.getString("cipher"));
    				break;
    			}
    		}
    	};
    	
    	if(mSelectedImage == null) {
    		//TODO: ERROR
    		return;
    	}
    	
    	Runnable r = new Runnable() {
    		public void run() {
	    		if(mImageBytes == null) {
	    			try {
	    				InputStream is = getContentResolver().openInputStream(mSelectedImage);
	    				Bitmap bm = BitmapFactory.decodeStream(is);
	    				ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    				
	    				Message msg = new Message();
	    				msg.arg1 = MSG_UPDATE_STATUS;
	    				msg.arg2 = R.string.msg_compressingImage;
	    				threadHandler.sendMessage(msg);
	    				
	    				if(bm.compress(Bitmap.CompressFormat.PNG, 10, baos)) {
	    					mImageBytes = baos.toByteArray();
	    				}
	    			} catch (FileNotFoundException e) { /* Oh well */ }
	    		}
	    		
	    		
	    		if(mImageBytes != null) {
    				Message msg = new Message();
    				msg.arg1 = MSG_UPDATE_STATUS;
    				msg.arg2 = R.string.msg_encodingImage;
    				threadHandler.sendMessage(msg);
    				
    				String cipher = Base64.encodeBytes(mImageBytes);
    				Bundle b = new Bundle();
    				b.putString("cipher", cipher);
    				
    				msg = new Message();
    				msg.arg1 = MSG_RECEIVE_CIPHER;
    				msg.setData(b);
    				threadHandler.sendMessage(msg);
	    		}
    		}
    	};
    	
    	mWorkThread = new Thread(r);
    	mWorkThread.start();
    }
    
    public void doShare() {
    	Resources res = getResources();
    	EditText base64 = (EditText) findViewById(R.id.main_base64Field);
    	Intent intent = new Intent(Intent.ACTION_SEND);
    	intent.putExtra(Intent.EXTRA_TEXT, base64.getText().toString());
    	intent.setType("text/plain");
    	startActivity(Intent.createChooser(intent, res.getString(R.string.title_shareVia)));
    }
    
    public void doShowBarcode() {
    	EditText base64 = (EditText) findViewById(R.id.main_base64Field);
    	IntentIntegrator.shareText(Base64Activity.this,
    			base64.getText().toString(),
    			R.string.title_dependencyNeeded,
    			R.string.msg_dependencyNeeded,
    			R.string.label_yes,
    			R.string.label_no);
    }
    
    public void doScan(View v) {
    	IntentIntegrator.initiateScan(Base64Activity.this,
    			R.string.title_dependencyNeeded,
    			R.string.msg_dependencyNeeded,
    			R.string.label_yes,
    			R.string.label_no,
    			IntentIntegrator.QR_CODE_TYPES);
    }
    
    private void doTextEncode() {
    	EditText textInput = (EditText) this.findViewById(R.id.main_plaintextField);
    	EditText base64Field = (EditText) this.findViewById(R.id.main_base64Field);
    	
    	if(textInput.getText().length() <= 0) {
    		showDialog(DIALOG_EMPTY_TEXT_INPUT);
    		return;
    	}
    	
    	String text = textInput.getText().toString();
    	String cipher = Base64.encodeBytes(text.getBytes());
    	base64Field.setText(cipher);
    }
    
    public void doDecode(View v) {
    	switch(getDataType()) {
    	case TYPE_TEXT:
    		doTextDecode();
    		break;
    		
    	case TYPE_IMAGE:
    		doImageDecode();
    		break;
    	}
    }
    
    private void doImageDecode() {
    	EditText base64Field = (EditText) this.findViewById(R.id.main_base64Field);
    	String cipher = base64Field.getEditableText().toString();
    	try {
			byte data[] = Base64.decode(cipher);
			Intent i = new Intent(getApplicationContext(), QuickBrowse.class);
			
			i.putExtra(QuickBrowse.INTENT_DATA, data);
			
			startActivity(i);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    private void doTextDecode() {
    	EditText textInput = (EditText) this.findViewById(R.id.main_plaintextField);
    	EditText base64Field = (EditText) this.findViewById(R.id.main_base64Field);
    	String cipher = base64Field.getText().toString();
    	try {
    		String s = new String( Base64.decode(cipher) );
    		StringBuffer sb = new StringBuffer();
    		
    		// Extract interesting stuff...
    		for(int i = 0; i < s.length(); i++) {
    			char c = s.charAt(i);
    			if(c >= 32 && c <= 126 || c >= 128 && c <= 254) {
    				sb.append(c);
    			}
    		}
    		
    		textInput.setText(sb.toString());
    	} catch (IOException e) {
    		Resources res = this.getResources();
    		String msg = res.getString(R.string.msg_errorPrefix);
    		Toast.makeText(this, String.format(msg, e.getMessage()), Toast.LENGTH_SHORT).show();
    	}
    }
    
    private int getDataType() {
    	Spinner dataSource = (Spinner) this.findViewById(R.id.spinner_selectSource);
    	return dataSource.getSelectedItemPosition();
    }
    
    public static final int
    	MSG_UPDATE_STATUS = 0,
    	MSG_RECEIVE_CIPHER = 1;
    
    public static final int
    	DIALOG_EMPTY_TEXT_INPUT = 0,
    	DIALOG_IMAGE_PICKER = 1,
    	DIALOG_WAITER = 2;
    
    public static final int
    	TYPE_TEXT = 0,
    	TYPE_IMAGE = 1;
    
    public static final int
    	SELECT_IMAGE = 0;
}