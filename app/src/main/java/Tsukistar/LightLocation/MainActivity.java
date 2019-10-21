package Tsukistar.LightLocation;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.runtime.Permission;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends AppCompatActivity {

    private Button opencamera;
    private Button opengallery;
    private ImageView preview;
    private TextView resultText;
    private Handler handler = null;
    private String content = null;
    private String httpArg = null;
    private String base64 = null;
    private String access_token = null;
    private String TAG = "LightLocation";
    public static final String API_KEY = "7LanCYlFcmd6rrzapblR4KIP";
    public static final String SECRET_KEY = "eIwllvb4SuFQCFV0RBUvayHjpHXTdGGX";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);//设置界面为竖屏

        opencamera = findViewById(R.id.take_a_photo);
        opengallery = findViewById(R.id.upload_pic);
        preview = findViewById(R.id.Preview_See);
        resultText = findViewById(R.id.resultText);
        handler = new Handler();
        getpermissions();

        opencamera.setOnClickListener(takephoto);
        opengallery.setOnClickListener(pickphoto);
        preview.setOnClickListener(uploadpic);
    }

    //退出界面
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            AlertDialog.Builder bdr = new AlertDialog.Builder(this);
            bdr.setMessage(R.string.app_name);
            bdr.setIcon(R.drawable.welcome_pic);
            bdr.setMessage(R.string.whether_quit);
            bdr.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
            bdr.setPositiveButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
            bdr.show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    //权限申请
    private void getpermissions() {
        if (!AndPermission.hasPermissions(this, CAMERA_SERVICE))
            AndPermission.with(this)
                    .runtime()
                    .permission(
                            Permission.Group.STORAGE,
                            Permission.Group.CAMERA
                    ).start();
    }

    //打开相机
    View.OnClickListener takephoto = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent, 1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };
    //打开相册
    View.OnClickListener pickphoto = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, 2);
        }
    };
    //上传图片
    View.OnClickListener uploadpic = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (httpArg != null){
                sendRequestWithHttpURLConnection();
            }
        }
    };
    //开启线程来发起网络请求
    private void sendRequestWithHttpURLConnection(){
        new Thread(new Runnable(){
            public void run() {
                if (access_token == null)
                    get_access_token();
                HttpURLConnection connection=null;
                content = "查询中...";
                handler.post(runnableUi);
                try {
                    String uri = "https://aip.baidubce.com/rest/2.0/image-classify/v1/object_detect?access_token=" + access_token;
                    URL url=new URL(uri);
                    connection =(HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    connection.setDoOutput(true);
                    connection.getOutputStream().write(httpArg.getBytes("UTF-8"));
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);
                    connection.connect();

                    InputStream in=connection.getInputStream();
                    BufferedReader reader=new BufferedReader(new InputStreamReader(in));
                    StringBuilder response=new StringBuilder();

                    String line;
                    while((line=reader.readLine())!=null){
                        response.append(line);
                    }
                    content = response.toString();
                    parseJSONWithJSONObject(content);
                }catch (Exception e) {
                    e.printStackTrace();
                    content = "网络连接超时，请点击图片重试...";
                    handler.post(runnableUi);
                }
                finally{
                    if(connection!=null){
                        connection.disconnect();
                    }
                }
            }
        }).start();
    }
    //显示结果
    Runnable runnableUi=new  Runnable(){
        @Override
        public void run() {
            resultText.setText(content);
        }

    };
    //处理回调
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String picturePath = null;
        Bitmap bitmap = null;
        if (requestCode == 1 && resultCode == RESULT_OK && null != data) {
            Bundle bundle = data.getExtras();
            bitmap = (Bitmap)bundle.get("data");
            preview.setImageBitmap(bitmap);
            imgToBase64(null, bitmap);
        }
        else if (requestCode == 2 && resultCode == RESULT_OK && null != data) {

            Uri selectedImage = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};

            Cursor cursor = getContentResolver().query(selectedImage,
                    filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            picturePath = cursor.getString(columnIndex);
            cursor.close();
            if (!picturePath.isEmpty())
                preview.setImageURI(selectedImage);
        }
        if (bitmap != null || picturePath != null) {
            base64 = imgToBase64(picturePath, bitmap);
            base64 = base64.replace("\r\n", "");
            try {
                base64 = URLEncoder.encode(base64, "utf-8");
                httpArg= "imagetype=1"+ "&image="+base64 +"&top_num=4";
                sendRequestWithHttpURLConnection();
            } catch (Exception e) {
                return;
            }
        }
    }
    //将图像转为Base64编码
    public static String imgToBase64(String imgPath, Bitmap bitmap) {
        if (imgPath !=null && imgPath.length() > 0) {
            bitmap = readBitmap(imgPath);
        }
        ByteArrayOutputStream out = null;
        try {
            out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);

            out.flush();
            out.close();

            byte[] imgBytes = out.toByteArray();
            return Base64.encodeToString(imgBytes, Base64.DEFAULT);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            return null;
        }
    }
    //读取Bitmap
    private static Bitmap readBitmap(String imgPath) {
        try {
            return BitmapFactory.decodeFile(imgPath);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            return null;
        }
    }
    //获取token
    private void get_access_token() {
        HttpURLConnection connection=null;
        String uri = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id=" + API_KEY + "&client_secret=" + SECRET_KEY;
        try {
            URL url = new URL(uri);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.connect();

            InputStream in=connection.getInputStream();
            BufferedReader reader=new BufferedReader(new InputStreamReader(in));
            StringBuilder response=new StringBuilder();

            String line;
            while((line=reader.readLine())!=null){
                response.append(line);
            }
            content = response.toString();
            Log.e(TAG, "get_access_token: " + content);

            JSONObject jsonObject=new JSONObject(content);
            access_token = jsonObject.getString("access_token");
            Log.e(TAG, "get_access_token: access_token: " + access_token);
        }  catch (Exception e) {
            e.printStackTrace();
            content = "网络连接超时，请点击图片重试...";
            handler.post(runnableUi);
        } finally{
            if(connection!=null)
                connection.disconnect();
        }
    }
    //使用JSONObject处理返回数据
    private void parseJSONWithJSONObject(String jsonData) {
        content = "";
        try
        {
            JSONObject jsonObject=new JSONObject(jsonData);
            JSONObject Object=jsonObject.getJSONObject("result");
            int width = Integer.parseInt(Object.getString("width"));
            int top = Integer.parseInt(Object.getString("top"));
            int left = Integer.parseInt(Object.getString("left"));
            int height = Integer.parseInt(Object.getString("height"));
            content += "以左上角为坐标原点\n主体所在长方形区域宽度: " + width + "px\n主体所在长方形区域高度" + height + "px\n主体所在长方形区域左上顶点垂直坐标: " + top + "px\n主体所在长方形区域左上顶点水平坐标" + left + "px" ;
            handler.post(runnableUi);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}