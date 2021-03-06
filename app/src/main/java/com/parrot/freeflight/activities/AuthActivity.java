/*******************************************************************************
 * Copyright 2014 AISpeech
 ******************************************************************************/
package com.parrot.freeflight.activities;

import java.io.FileNotFoundException;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.aispeech.common.Util;
import com.aispeech.export.listeners.AIAuthListener;
import com.aispeech.speech.AIAuthEngine;
import com.parrot.freeflight.R;

public class AuthActivity extends Activity implements OnClickListener {
    public static final String TAG = AuthActivity.class.getName();

    Toast mToast;

    TextView mInfoTv;
    Button mAuthBtn;
    ProgressDialog mProgressBar;

    AIAuthEngine mEngine;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /** 'Window.FEATURE_NO_TITLE' - Used to hide the title */
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.auth);

        mInfoTv = (TextView) findViewById(R.id.tv_info);
        mAuthBtn = (Button) findViewById(R.id.btn_auth);
        mAuthBtn.setOnClickListener(this);

        mEngine = AIAuthEngine.getInstance(getApplicationContext());
        try {
            mEngine.init(AppKey.APPKEY, AppKey.SECRETKEY,"");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }// TODO 换成您的s/n码

        mEngine.setOnAuthListener(new AIAuthListener() {
			
			@Override
			public void onAuthSuccess() {
				runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                            mInfoTv.setText("恭喜，已完成授权，您可以自由使用其它功能");
                        }
                });
			}
			
			@Override
			public void onAuthFailed(final String result) {
				runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                            mInfoTv.setText(result);
                        }
                });
			}
		});
        
        if (mEngine.isAuthed()) {
            mInfoTv.setText("已授权，您可以自由的使用其它功能");
        } else {
            mInfoTv.setText("抱歉，您需要授权才能自由使用其它功能");
        }
    }

    @Override
    public void onClick(View arg0) {
        if (arg0 == mAuthBtn) {
            new Thread(new Runnable() {

                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        
                        @Override
                        public void run() {
                            mProgressBar = ProgressDialog.show(AuthActivity.this, "授权中", "请等待...");
                        }
                    });
                    final boolean authRet = mEngine.doAuth();
                    runOnUiThread(new Runnable() {
                        
                        @Override
                        public void run() {
                            mProgressBar.dismiss();
                        }
                    });
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            if (authRet) {
//                                mInfoTv.setText("恭喜，已完成授权，您可以自由使用其它功能");
                                Toast.makeText(AuthActivity.this, "授权成功", Toast.LENGTH_LONG).show();
                            } else {
//                                mInfoTv.setText("授权失败");
                                Toast.makeText(AuthActivity.this, "授权失败", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            }).start();
        }
    }

}