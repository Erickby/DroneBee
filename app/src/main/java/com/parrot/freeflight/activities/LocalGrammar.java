/*******************************************************************************
 * Copyright 2014 AISpeech
 ******************************************************************************/
package com.parrot.freeflight.activities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.aispeech.AIError;
import com.aispeech.AIResult;
import com.aispeech.IMergeRule;
import com.aispeech.common.JSONResultParser;
import com.aispeech.common.Util;
import com.aispeech.export.engines.AILocalGrammarEngine;
import com.aispeech.export.engines.AIMixASREngine;
import com.aispeech.export.listeners.AIASRListener;
import com.aispeech.export.listeners.AILocalGrammarListener;
import com.parrot.freeflight.R;
import com.parrot.util.GrammarHelper;
import com.parrot.util.NetworkUtil;
import com.parrot.util.SampleConstants;

/**
 * 本示例将演示通过联合使用本地识别引擎和本地语法编译引擎实现定制识别。<br>
 * 将由本地语法编译引擎根据手机中的联系人和应用列表编译出可供本地识别引擎使用的资源，从而达到离线定制识别的功能。
 */
public class LocalGrammar extends Activity implements OnClickListener {
    public static final String TAG = LocalGrammar.class.getName();

    EditText tv;
    Button bt_res;
    Button bt_asr;
    Toast mToast;

    AILocalGrammarEngine mGrammarEngine;
    AIMixASREngine mAsrEngine;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /** 'Window.FEATURE_NO_TITLE' - Used to hide the title */
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.grammar);
        tv = (EditText) findViewById(R.id.tv);
        bt_res = (Button) findViewById(R.id.btn_gen);
        bt_asr = (Button) findViewById(R.id.btn_asr);
        bt_res.setEnabled(false);
        bt_asr.setEnabled(false);
        bt_res.setOnClickListener(this);
        bt_asr.setOnClickListener(this);

        initGrammarEngine();
        // 检测是否已生成并存在识别资源，若已存在，则立即初始化本地识别引擎，否则等待编译生成资源文件后加载本地识别引擎
        if (new File(Util.getResourceDir(this) + File.separator + AILocalGrammarEngine.OUTPUT_NAME)
                .exists()) {
            initAsrEngine();
        }

        mToast = Toast.makeText(this, "", Toast.LENGTH_LONG);
    }

    /**
     * 初始化资源编译引擎
     */
    private void initGrammarEngine() {
        if (mGrammarEngine != null) {
            mGrammarEngine.destroy();
        }
        Log.i(TAG, "grammar create");
        mGrammarEngine = AILocalGrammarEngine.createInstance();
        mGrammarEngine.setResFileName("ebnfc.aifar.0.0.1.bin");
        mGrammarEngine
                .init(this, new AILocalGrammarListenerImpl(), AppKey.APPKEY, AppKey.SECRETKEY);
        mGrammarEngine.setDeviceId(Util.getIMEI(this));
    }

    /**
     * 初始化本地合成引擎
     */
    @SuppressLint("NewApi")
    private void initAsrEngine() {
        if (mAsrEngine != null) {
            mAsrEngine.destroy();
        }
        Log.i(TAG, "asr create");
        mAsrEngine = AIMixASREngine.createInstance();
        mAsrEngine.setResBin("ebnfr.aifar.0.0.1.bin");
        mAsrEngine.setNetBin(AILocalGrammarEngine.OUTPUT_NAME, true);

        //mAsrEngine.setWaitCloudTimeout(3000);
        mAsrEngine.setVadResource("vad.aihome.v0.3_20150901.bin");
        if (getExternalCacheDir() != null) {
            mAsrEngine.setTmpDir(getExternalCacheDir().getAbsolutePath());
            mAsrEngine.setUploadEnable(true);
            mAsrEngine.setUploadInterval(1000);
        }
        mAsrEngine.setServer("ws://s-test.api.aispeech.com:10000");
        mAsrEngine.setRes("aihome");
        mAsrEngine.setUseXbnfRec(true);
        mAsrEngine.setUsePinyin(true);
        mAsrEngine.setUseForceout(false);
        mAsrEngine.setAthThreshold(0.4f);//设置本地置信度阀值
        mAsrEngine.setIsRelyOnLocalConf(true);//是否开启依据本地置信度优先输出,如需添加例外
        mAsrEngine.setLocalBetterDomains(new String[] { "phone", "remind"});//设置本地擅长的领域范围 
        mAsrEngine.setWaitCloudTimeout(3000);
        mAsrEngine.setPauseTime(1000);
        mAsrEngine.setUseConf(true);
        mAsrEngine.setNoSpeechTimeOut(0);
        mAsrEngine.setDeviceId(Util.getIMEI(this));
        // 自行设置合并规则:
      	// 1. 如果无云端结果,则直接返回本地结果
      	// 2. 如果有云端结果,当本地结果置信度大于阈值时,返回本地结果,否则返回云端结果
      	mAsrEngine.setMergeRule(new IMergeRule() {
      			
                  @Override
                  public AIResult mergeResult(AIResult localResult, AIResult cloudResult) {
                  
                      AIResult result = null;
                      try {
                          if (cloudResult == null) {
                              // 为结果增加标记,以标示来源于云端还是本地
                              JSONObject localJsonObject = new JSONObject(localResult.getResultObject()
                                      .toString());
                              localJsonObject.put("src", "native");

                              localResult.setResultObject(localJsonObject);
                              result = localResult;
                          } else {
                              JSONObject cloudJsonObject = new JSONObject(cloudResult.getResultObject()
                                      .toString());
                              cloudJsonObject.put("src", "cloud");
                              cloudResult.setResultObject(cloudJsonObject);
                              result = cloudResult;
                          }
                      } catch (JSONException e) {
                          e.printStackTrace();
                      }
                      return result;
                 	 
                  }
              });
        mAsrEngine.init(this, new AIASRListenerImpl(), AppKey.APPKEY, AppKey.SECRETKEY);
        mAsrEngine.setUseCloud(false);//该方法必须在init之后
    }

    /**
     * 开始生成识别资源
     */

    private void startResGen() {
        // 生成ebnf语法
        GrammarHelper gh = new GrammarHelper(this);
        String contactString = gh.getConatcts();
        String appString = gh.getApps();
        // 如果手机通讯录没有联系人
        if (TextUtils.isEmpty(contactString)) {
            contactString = "无联系人";
        }
        String ebnf = gh.importAssets(contactString, appString, "grammar.xbnf");
        Log.i(TAG, ebnf);
        // 设置ebnf语法
        mGrammarEngine.setEbnf(ebnf);
        // 启动语法编译引擎，更新资源
        mGrammarEngine.update();
    }

    /**
     * 语法编译引擎回调接口，用以接收相关事件
     */
    public class AILocalGrammarListenerImpl implements AILocalGrammarListener {

        @Override
        public void onError(AIError error) {
            showInfo("资源生成发生错误");
            showTip(error.getError());
            setResBtnEnable(true);
        }

        @Override
        public void onUpdateCompleted(String recordId, String path) {
            showInfo("资源生成/更新成功\npath=" + path + "\n重新加载识别引擎...");
            Log.i(TAG, "资源生成/更新成功\npath=" + path + "\n重新加载识别引擎...");
            File file = new File(path);
            byte[] buffer = new byte[10240];
            int i = 0;
            try {
                FileInputStream fis = new FileInputStream(file);
                FileOutputStream fos = new FileOutputStream(new File("/sdcard/local.net.bin"));
                while((i = fis.read(buffer)) > 0){
                    fos.write(buffer);
                }
                fis.close();
                fos.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            initAsrEngine();
        }

        @Override
        public void onInit(int status) {
            if (status == 0) {
                showInfo("资源定制引擎加载成功");
                if (mAsrEngine == null) {
                    setResBtnEnable(true);
                }
            } else {
                showInfo("资源定制引擎加载失败");
            }
        }
    }


    /**
     * 本地识别引擎回调接口，用以接收相关事件
     */
    public class AIASRListenerImpl implements AIASRListener {

        @Override
        public void onBeginningOfSpeech() {
            showInfo("检测到说话");

        }

        @Override
        public void onEndOfSpeech() {
            showInfo("检测到语音停止，开始识别...");
        }

        @Override
        public void onReadyForSpeech() {
            showInfo("请说话...");
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            showTip("RmsDB = " + rmsdB);
        }

        @Override
        public void onError(AIError error) {
            showInfo("识别发生错误");
            showTip(error.getErrId() + "");
            setAsrBtnState(true, "识别");
        }

        @Override
        public void onResults(AIResult results) {
            Log.i(TAG, results.getResultObject().toString());
            try {
                showInfo(new JSONObject(results.getResultObject().toString()).toString(4));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            setAsrBtnState(true, "识别");
        }

        @Override
        public void onInit(int status) {
            if (status == 0) {
                Log.i(TAG, "end of init asr engine");
                showInfo("本地识别引擎加载成功");
                setResBtnEnable(true);
                setAsrBtnState(true, "识别");
                if (NetworkUtil.isWifiConnected(LocalGrammar.this)) {
                    if (mAsrEngine != null) {
                        mAsrEngine.setNetWorkState("WIFI");
                    }
                }
            } else {
                showInfo("本地识别引擎加载失败");
            }
        }

        @Override
        public void onRecorderReleased() {
            // showInfo("检测到录音机停止");
        }
    }

    /**
     * 设置资源按钮的状态
     * 
     * @param state
     *            使能状态
     */
    private void setResBtnEnable(final boolean state) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                bt_res.setEnabled(state);
            }
        });
    }

    /**
     * 设置识别按钮的状态
     * 
     * @param state
     *            使能状态
     * @param text
     *            按钮文本
     */
    private void setAsrBtnState(final boolean state, final String text) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                bt_asr.setEnabled(state);
                bt_asr.setText(text);
            }
        });
    }

    private void showInfo(final String str) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                tv.setText(str);
            }
        });
    }

    private void showTip(final String str) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mToast.setText(str);
                mToast.show();
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mGrammarEngine != null) {
            Log.i(TAG, "grammar cancel");
            mGrammarEngine.cancel();
        }
        if (mAsrEngine != null) {
            Log.i(TAG, "asr cancel");
            mAsrEngine.cancel();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mGrammarEngine != null) {
            Log.i(TAG, "grammar destroy");
            mGrammarEngine.destroy();
            mGrammarEngine = null;
        }
        if (mAsrEngine != null) {
            Log.i(TAG, "asr destroy");
            mAsrEngine.destroy();
            mAsrEngine = null;
        }
    }

    @Override
    public void onClick(View view) {
        if (view == bt_res) {
            setResBtnEnable(false);
            setAsrBtnState(false, "识别");
            new Thread(new Runnable() {

                @Override
                public void run() {
                    showInfo("开始生成资源...");
                    startResGen();
                }
            }).start();
        } else if (view == bt_asr) {
            if ("识别".equals(bt_asr.getText())) {
                if (mAsrEngine != null) {
                    setAsrBtnState(true, "停止");
                    mAsrEngine.start();
                } else {
                    showTip("请先生成资源");
                }
            } else if ("停止".equals(bt_asr.getText())) {
                if (mAsrEngine != null) {
                    setAsrBtnState(true, "识别");
                    mAsrEngine.stopRecording();
                }
            }
        }
    }

}