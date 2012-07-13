package org.oep.base64;

import java.io.ByteArrayInputStream;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.widget.ImageView;

public class QuickBrowse extends Activity {
	
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.quick_browse);
		putImage();
	}
	
	protected void onStart() {
		super.onStart();
		putImage();
	}
	
	private void putImage() {
		ImageView iv = (ImageView) findViewById(R.id.viewer_image);
		Intent intent = getIntent();
		byte data[] = intent.getByteArrayExtra(INTENT_DATA);
		
		System.out.print("Signature: ");
		for(int i = 0; i < 8; i++) {
			System.out.printf("%x ", data[i]);
		}
		System.out.println();
		
		ByteArrayInputStream bais = new ByteArrayInputStream(data);
		Bitmap bm = BitmapFactory.decodeStream(bais);
		
		if(bm != null) {
			BitmapDrawable bmd = new BitmapDrawable(getResources(), bm);
			iv.setBackgroundDrawable(bmd);
		}
	}
	
	public static final String
		INTENT_DATA = "data";
}
