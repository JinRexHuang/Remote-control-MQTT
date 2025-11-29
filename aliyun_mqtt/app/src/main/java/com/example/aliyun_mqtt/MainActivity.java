package com.example.aliyun_mqtt;

import androidx.appcompat.app.AppCompatActivity;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private MqttClient client;
    private MqttConnectOptions options;
    private Handler handler;
    private ScheduledExecutorService scheduler;

    private String productKey = "i8t1JQgMtaq";
    private String deviceName = "Android";
    private String deviceSecret = "233a36ecfe53e470f4d732136fab1e80";

    private final String pub_topic = "/i8t1JQgMtaq/Android/user/ESP8266";
    private final String sub_topic = "/i8t1JQgMtaq/Android/user/ESP8266";

    private int temperature = 0;
    private int humidity = 0;
    private TextView tv_status;

    private TextView tvDate;
    private TextView tvTime;
    private TextView tvWeekday;
    private final Handler timeHandler = new Handler();
    private final SimpleDateFormat timeFormat;
    private final SimpleDateFormat timeOnlyFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault());
    private final SimpleDateFormat weekdayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());

    // 时钟指针视图
    private View hourHand;
    private View minuteHand;
    private View secondHand;

    public MainActivity() {
        // 初始化时区
        timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        timeFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai")); // 设置时区为东八区
        timeOnlyFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        weekdayFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
    }

    private TextView tvComputerStatus;
    private ImageView ivComputerStatus;
    private LinearLayout statusContainer;


    private TextView tvComputerStatus_mqtt;



    // 添加电脑状态变量
    private boolean isComputerOn = false;
    private boolean isMqttOn = false;

    // 添加连接状态容器引用
    private LinearLayout statusConnectionContainer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化连接状态容器
        statusConnectionContainer = findViewById(R.id.status_connection_container);
        tvComputerStatus_mqtt = findViewById(R.id.tv_status);

        // 初始化电脑状态视图
        tvComputerStatus = findViewById(R.id.tv_computer_status);
        ivComputerStatus = findViewById(R.id.iv_computer_status);
        statusContainer = findViewById(R.id.status_container); // 需要给LinearLayout添加id

        tv_status = findViewById(R.id.tv_status);
        tvDate = findViewById(R.id.tv_date);
        tvTime = findViewById(R.id.tv_time);
        tvWeekday = findViewById(R.id.tv_weekday);

        // 初始化时钟指针
        hourHand = findViewById(R.id.hour_hand);
        minuteHand = findViewById(R.id.minute_hand);
        secondHand = findViewById(R.id.second_hand);

        ImageButton btn_open = findViewById(R.id.btn_open);
        ImageButton btn_close = findViewById(R.id.btn_close);

        btn_open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //开
                publish_message("1");
            }
        });

        btn_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //关
                publish_message("0");
            }
        });

        View.OnTouchListener touchListener = (v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    break;
            }
            return false;
        };

        btn_open.setOnTouchListener(touchListener);
        btn_close.setOnTouchListener(touchListener);

        // 初始化指针位置
        hourHand.post(() -> adjustHandPosition(hourHand));
        minuteHand.post(() -> adjustHandPosition(minuteHand));
        secondHand.post(() -> adjustHandPosition(secondHand));

        mqtt_init();
        start_reconnect();

        handler = new Handler() {
            @SuppressLint("SetTextI18n")
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case 1: //开机校验更新回传
                        break;
                    case 2:  // 反馈回传
                        break;
                    case 3:  //MQTT 收到消息回传   UTF8Buffer msg=new UTF8Buffer(object.toString());
                        String message = msg.obj.toString();
                        Log.d("nicecode", "handleMessage: "+ message);
                        // 检查是否为"ok"消息
                        if ("ok".equalsIgnoreCase(message.trim())) {
                            isComputerOn = true;
                            updateComputerStatus();
                            Log.d("ComputerStatus", "Computer is ON");
                        } else if("yes".equalsIgnoreCase(message.trim())) {
                            isComputerOn = false;
                            updateComputerStatus();
                            Log.d("ComputerStatus", "Computer is OFF (received yes)");
                        }
                        try {
                            JSONObject jsonObjectALL = null;
                            jsonObjectALL = new JSONObject(message);
                            JSONObject items = jsonObjectALL.getJSONObject("items");

                            JSONObject obj_temp = items.getJSONObject("temp");
                            JSONObject obj_humi = items.getJSONObject("humi");

                            temperature = obj_temp.getInt("value");
                            humidity = obj_humi.getInt("value");

                            // 这里可以根据需要显示温湿度数据
                            Log.d("nicecode", "temp: "+ temperature);
                            Log.d("nicecode", "humi: "+ humidity);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            break;
                        }
                        break;
                    case 30:  //连接失败
                        Log.e("MQTT", "连接失败: " + msg.obj);
                        runOnUiThread(() -> {
                            tv_status.setText("连接阿里云状态：连接失败");
                            statusConnectionContainer.setActivated(false); // 设置非激活状态（红色背景）
                            isMqttOn = false;
                            updateComputerStatus();
                        });
                        Toast.makeText(MainActivity.this, "连接失败", Toast.LENGTH_SHORT).show();
                        break;
                    case 31:   //连接成功
                        runOnUiThread(() -> {
                            tv_status.setText("连接阿里云状态：已连接");
                            statusConnectionContainer.setActivated(true); // 设置激活状态（绿色背景）
                            isMqttOn = true;
                            updateComputerStatus();
                        });
                        Log.d("MQTT", "连接成功");
                        Toast.makeText(MainActivity.this, "连接成功", Toast.LENGTH_SHORT).show();
                        try {
                            client.subscribe(sub_topic, 1);
                        } catch (MqttException e) {
                            e.printStackTrace();
                        }
                        break;
                    default:
                        break;
                }
            }
        };
        startTimeUpdates();
    }



    // 调整指针位置，使其尾部固定在圆心
    private void adjustHandPosition(View hand) {
        int height = hand.getHeight();
        if (height > 0) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) hand.getLayoutParams();
            params.topMargin = -height;
            hand.setLayoutParams(params);
        }

    }

    private void startTimeUpdates() {
        timeHandler.post(new Runnable() {
            @Override
            public void run() {
                updateTimeDisplay();
                updateClockHands();
                // 每秒更新一次
                timeHandler.postDelayed(this, 1000);
            }
        });
    }

    private void updateTimeDisplay() {
        Date now = new Date();
        runOnUiThread(() -> {
            tvDate.setText(dateFormat.format(now));
            tvTime.setText(timeOnlyFormat.format(now));
            tvWeekday.setText(weekdayFormat.format(now));
        });

    }

    private void updateClockHands() {
        Calendar calendar = Calendar.getInstance();
        int hours = calendar.get(Calendar.HOUR_OF_DAY); // 使用24小时制
        int minutes = calendar.get(Calendar.MINUTE);
        int seconds = calendar.get(Calendar.SECOND);

        // 转换为12小时制
        hours = hours % 12;

        // 计算指针角度（0度在12点位置）
        final float hourAngle = (hours * 30) + (minutes * 0.5f); // 直接计算最终值
        final float minuteAngle = minutes * 6 ;
        final float secondAngle = seconds * 6;

        // 调整角度使0度位于顶部（12点方向）
        final float rotationOffset = 270; // 270° = 12点钟方向
        final float adjustedHourAngle = (hourAngle + rotationOffset) % 360;
        final float adjustedMinuteAngle = (minuteAngle + rotationOffset) % 360;
        final float adjustedSecondAngle = (secondAngle + rotationOffset) % 360;

        runOnUiThread(() -> {
            hourHand.setRotation(hourAngle);
            minuteHand.setRotation(minuteAngle);
            secondHand.setRotation(secondAngle);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        timeHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startTimeUpdates();
    }

    private void mqtt_init() {
        try {

            String clientId = deviceName;
            Map<String, String> params = new HashMap<String, String>(16);
            params.put("productKey", productKey);
            params.put("deviceName", deviceName);
            params.put("clientId", clientId);
            String timestamp = String.valueOf(System.currentTimeMillis());
            params.put("timestamp", timestamp);
            // cn-shanghai
            String host_url = "tcp://" + productKey + ".iot-as-mqtt.cn-shanghai.aliyuncs.com:1883";
            String client_id = clientId + "|securemode=2,signmethod=hmacsha1,timestamp=" + timestamp + "|";
            String user_name = deviceName + "&" + productKey;
            String password = com.example.aliyun_mqtt.AliyunIoTSignUtil.sign(params, deviceSecret, "hmacsha1");

            //host为主机名，test为clientid即连接MQTT的客户端ID，一般以客户端唯一标识符表示，MemoryPersistence设置clientid的保存形式，默认为以内存保存
            System.out.println(">>>" + host_url);
            System.out.println(">>>" + client_id);

            //connectMqtt(targetServer, mqttclientId, mqttUsername, mqttPassword);

            client = new MqttClient(host_url, client_id, new MemoryPersistence());
            //MQTT的连接设置
            options = new MqttConnectOptions();
            //设置是否清空session,这里如果设置为false表示服务器会保留客户端的连接记录，这里设置为true表示每次连接到服务器都以新的身份连接
            options.setCleanSession(false);
            //设置连接的用户名
            options.setUserName(user_name);
            //设置连接的密码
            options.setPassword(password.toCharArray());
            // 设置超时时间 单位为秒
            options.setConnectionTimeout(10);
            // 设置会话心跳时间 单位为秒 服务器会每隔1.5*20秒的时间向客户端发送个消息判断客户端是否在线，但这个方法并没有重连的机制
            options.setKeepAliveInterval(60);
            //设置回调
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    //连接丢失后，一般在这里面进行重连
                    System.out.println("connectionLost----------");
                    // 添加日志记录
                    Log.e("MQTT", "连接丢失", cause);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    //publish后会执行到这里
                    System.out.println("deliveryComplete---------" + token.isComplete());
                    // 添加日志记录
                    Log.d("MQTT", "消息发送完成: " + token.isComplete());
                }

                @Override
                public void messageArrived(String topicName, MqttMessage message)
                        throws Exception {
                    //subscribe后得到的消息会执行到这里面
                    System.out.println("messageArrived----------");
                    Message msg = new Message();
                    //封装message包
                    msg.what = 3;   //收到消息标志位
                    msg.obj = message.toString();
                    //发送messge到handler
                    handler.sendMessage(msg);    // hander 回传

                    // 添加日志记录
                    Log.d("MQTT", "收到消息: " + message.toString());
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            // 添加日志记录
            Log.e("MQTT", "初始化失败", e);
        }
    }

    private void mqtt_connect() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    tv_status.setText("状态：连接中...");
                    tv_status.setTextColor(Color.GRAY);
                });
                try {
                    Log.d("MQTT", "正在连接MQTT服务器..."); // 添加连接开始日志
                    if (!(client.isConnected()))  //如果还未连接
                    {
                        client.connect(options);
                        Message msg = new Message();
                        msg.what = 31;
                        // 没有用到obj字段
                        handler.sendMessage(msg);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Message msg = new Message();
                    msg.what = 30;
                    // 没有用到obj字段
                    handler.sendMessage(msg);

                    // 添加日志记录
                    Log.e("MQTT", "连接失败", e);
                }
            }
        }).start();
    }

    private void start_reconnect() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (client != null && !client.isConnected()) {
                    mqtt_connect();
                }
            }
        }, 0 * 1000, 10 * 1000, TimeUnit.MILLISECONDS);
    }

    private void publish_message(String message) {
        if (client == null || !client.isConnected()) {
            Toast.makeText(MainActivity.this, "MQTT未连接", Toast.LENGTH_SHORT).show();
            return;
        }
        MqttMessage mqtt_message = new MqttMessage();
        mqtt_message.setPayload(message.getBytes());
        try {
            client.publish(pub_topic, mqtt_message);
            Log.d("MQTT", "消息已发送: " + message);
        } catch (MqttException e) {
            e.printStackTrace();
            Log.e("MQTT", "发送消息失败", e);
        }
    }

    // 新增方法：更新电脑状态显示
    private void updateComputerStatus() {
        runOnUiThread(() -> {
            // 添加颜色过渡动画
            ValueAnimator colorAnim = ValueAnimator.ofArgb(
                    tvComputerStatus.getCurrentTextColor(),
                    isComputerOn ? Color.parseColor("#00C853") : Color.parseColor("#FF5252")
            );
            colorAnim.setDuration(300);
            colorAnim.addUpdateListener(animator ->
                    tvComputerStatus.setTextColor((Integer) animator.getAnimatedValue()));
            colorAnim.start();
            if (isComputerOn) {
                tvComputerStatus.setText("电脑状态：已开机");
                tvComputerStatus.setTextColor(Color.parseColor("#00C853")); // 绿色
                ivComputerStatus.setImageResource(R.drawable.ic_computer_on);
                statusContainer.setActivated(true); // 激活状态，使用选择器背景
            } else {
                tvComputerStatus.setText("电脑状态：未开机");
                tvComputerStatus.setTextColor(Color.parseColor("#FF5252")); // 红色
                ivComputerStatus.setImageResource(R.drawable.ic_computer_off);
                statusContainer.setActivated(false); // 非激活状态
            }
            if (isMqttOn) {
                tvComputerStatus_mqtt.setText("阿里云连接状态：已连接");
                tvComputerStatus_mqtt.setTextColor(Color.parseColor("#00C853")); // 绿色
//                tvComputerStatus_mqtt.setImageResource(R.drawable.ic_computer_on);
                statusConnectionContainer.setActivated(true); // 激活状态，使用选择器背景
            } else {
                tvComputerStatus_mqtt.setText("阿里云连接状态：未连接");
                tvComputerStatus_mqtt.setTextColor(Color.parseColor("#FF5252")); // 红色
//                ivComputerStatus.setImageResource(R.drawable.ic_computer_off);
                statusConnectionContainer.setActivated(false); // 非激活状态
            }
        });
    }
}